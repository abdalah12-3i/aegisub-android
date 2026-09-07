package io.github.samgum.aegisub.data.mkv

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class MkvSubtitleTrack(
    val trackIndex: Int,
    val title: String,
    val language: String,
    val mimeType: String,
    val format: String,
)

object MkvExtractor {

    private fun getLanguageDisplayName(code: String): String = when (code.lowercase()) {
        "ara", "ar" -> "العربية (Arabic)"
        "eng", "en" -> "English"
        "jpn", "ja" -> "日本語 (Japanese)"
        "tur", "tr" -> "Türkçe (Turkish)"
        "fra", "fr" -> "Français (French)"
        "spa", "es" -> "Español (Spanish)"
        "ger", "deu", "de" -> "Deutsch (German)"
        "ita", "it" -> "Italiano (Italian)"
        "rus", "ru" -> "Русский (Russian)"
        "chi", "zho", "zh" -> "中文 (Chinese)"
        "und" -> "Undefined / غير محدد"
        else -> code.uppercase()
    }

    /** فحص ملف الـ MKV واستخراج قائمة بمسارات الترجمة المدمجة فيه */
    fun getSubtitleTracks(context: Context, uri: Uri): List<MkvSubtitleTrack> {
        val extractor = MediaExtractor()
        val tracks = mutableListOf<MkvSubtitleTrack>()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("text/") || mime.contains("subrip") || mime.contains("ssa") || mime.contains("vtt")) {
                    val langCode = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                        format.getString(MediaFormat.KEY_LANGUAGE) ?: "und"
                    } else "und"

                    val langName = getLanguageDisplayName(langCode)
                    val formatName = when {
                        mime.contains("ssa") || mime.contains("ass") -> "ass"
                        mime.contains("vtt") -> "vtt"
                        else -> "srt"
                    }
                    val title = if (format.containsKey("title")) {
                        format.getString("title") ?: "$langName [${formatName.uppercase()}]"
                    } else "$langName [${formatName.uppercase()}]"

                    tracks.add(
                        MkvSubtitleTrack(
                            trackIndex = i,
                            title = title,
                            language = langName,
                            mimeType = mime,
                            format = formatName,
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
        return tracks
    }

    /** فك واستخراج نص الترجمة المختار من داخل الـ MKV */
    fun extractSubtitleContent(context: Context, uri: Uri, trackIndex: Int): Pair<String, String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val formatName = when {
                mime.contains("ssa") || mime.contains("ass") -> "ass"
                mime.contains("vtt") -> "vtt"
                else -> "srt"
            }
            extractor.selectTrack(trackIndex)

            val buffer = ByteBuffer.allocate(128 * 1024)
            val lines = mutableListOf<String>()

            // في ترجمات ASS: يتم قراءة الهيدر والأنماط من CodecSpecificData
            var header = ""
            if (formatName == "ass" && format.containsKey("csd-0")) {
                val csd = format.getByteBuffer("csd-0")
                if (csd != null) {
                    val bytes = ByteArray(csd.remaining())
                    csd.get(bytes)
                    header = String(bytes, StandardCharsets.UTF_8).trim()
                }
            }

            var cueNumber = 1
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break
                val bytes = ByteArray(sampleSize)
                buffer.get(bytes)
                val packet = String(bytes, StandardCharsets.UTF_8).trim()
                val startUs = extractor.sampleTime

                if (formatName == "ass") {
                    if (packet.startsWith("Dialogue:", true) || packet.startsWith("Comment:", true)) {
                        lines.add(packet)
                    } else {
                        val parts = packet.split(",", limit = 9)
                        if (parts.size >= 9) {
                            val layer = parts[1]
                            val style = parts[2]
                            val actor = parts[3]
                            val ml = parts[4]
                            val mr = parts[5]
                            val mv = parts[6]
                            val effect = parts[7]
                            val text = parts[8]
                            val startStr = formatAssTime(startUs / 1000)
                            val endStr = formatAssTime((startUs + 2_500_000L) / 1000)
                            lines.add("Dialogue: $layer,$startStr,$endStr,$style,$actor,$ml,$mr,$mv,$effect,$text")
                        } else {
                            val startStr = formatAssTime(startUs / 1000)
                            val endStr = formatAssTime((startUs + 2_500_000L) / 1000)
                            lines.add("Dialogue: 0,$startStr,$endStr,Default,,0,0,0,,$packet")
                        }
                    }
                } else {
                    val startStr = formatSrtTime(startUs / 1000)
                    val endStr = formatSrtTime((startUs + 2_500_000L) / 1000)
                    lines.add("${cueNumber++}\n$startStr --> $endStr\n$packet\n")
                }
                extractor.advance()
            }

            val fullContent = if (formatName == "ass") {
                val baseHeader = if (header.isNotBlank()) {
                    if (!header.contains("[Events]")) {
                        "$header\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                    } else {
                        "$header\n"
                    }
                } else {
                    defaultAssScript()
                }
                baseHeader + lines.joinToString("\n")
            } else {
                lines.joinToString("\n")
            }

            return formatName to fullContent
        } catch (e: Exception) {
            e.printStackTrace()
            return "ass" to defaultAssScript()
        } finally {
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    private fun formatAssTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3600_000
        val m = (total % 3600_000) / 60_000
        val s = (total % 60_000) / 1000
        val cs = (total % 1000) / 10
        return "%d:%02d:%02d.%02d".format(h, m, s, cs)
    }

    private fun formatSrtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0)
        val h = total / 3600_000
        val m = (total % 3600_000) / 60_000
        val s = (total % 60_000) / 1000
        val mm = total % 1000
        return "%02d:%02d:%02d,%03d".format(h, m, s, mm)
    }

    fun defaultAssScript(): String = """
        [Script Info]
        ScriptType: v4.00+
        PlayResX: 1920
        PlayResY: 1080

        [V4+ Styles]
        Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
        Style: Default,Arial,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1

        [Events]
        Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
    """.trimIndent() + "\n"
}
