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
        "ita", "it" -> "Italiano"
        "rus", "ru" -> "Русский (Russian)"
        "chi", "zho", "zh" -> "中文 (Chinese)"
        "und" -> "غير محدد (Undefined)"
        else -> code.uppercase()
    }

    /** فحص ملف الـ MKV واستخراج جميع مسارات الترجمة */
    fun getSubtitleTracks(context: Context, uri: Uri): List<MkvSubtitleTrack> {
        val extractor = MediaExtractor()
        val tracks = mutableListOf<MkvSubtitleTrack>()
        try {
            extractor.setDataSource(context, uri, null)
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                val isSubtitle = mime.startsWith("text/", true) ||
                        mime.contains("subrip", true) ||
                        mime.contains("ssa", true) ||
                        mime.contains("ass", true) ||
                        mime.contains("vtt", true)

                if (isSubtitle) {
                    val langCode = if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                        format.getString(MediaFormat.KEY_LANGUAGE) ?: "und"
                    } else "und"

                    val langName = getLanguageDisplayName(langCode)
                    val formatName = when {
                        mime.contains("ssa", true) || mime.contains("ass", true) -> "ass"
                        mime.contains("vtt", true) -> "vtt"
                        else -> "srt"
                    }

                    val trackTitle = if (format.containsKey("title")) {
                        format.getString("title") ?: "$langName [${formatName.uppercase()}]"
                    } else {
                        "Track ${i + 1}: $langName [${formatName.uppercase()}]"
                    }

                    tracks.add(
                        MkvSubtitleTrack(
                            trackIndex = i,
                            title = trackTitle,
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

    /** استخراج محتوى الترجمة بالكامل وبأوقات البداية والنهاية الحقيقية */
    fun extractSubtitleContent(context: Context, uri: Uri, trackIndex: Int): Pair<String, String> {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val formatName = when {
                mime.contains("ssa", true) || mime.contains("ass", true) -> "ass"
                mime.contains("vtt", true) -> "vtt"
                else -> "srt"
            }
            extractor.selectTrack(trackIndex)

            val buffer = ByteBuffer.allocate(256 * 1024)
            val events = mutableListOf<String>()

            // قراءة هيدر ASS الأصلي إن وجد
            var assHeader = ""
            if (formatName == "ass" && format.containsKey("csd-0")) {
                val csd = format.getByteBuffer("csd-0")
                if (csd != null) {
                    val bytes = ByteArray(csd.remaining())
                    csd.get(bytes)
                    assHeader = String(bytes, StandardCharsets.UTF_8).trim()
                }
            }

            var cueNumber = 1
            while (true) {
                buffer.clear()
                val sampleSize = extractor.readSampleData(buffer, 0)
                if (sampleSize < 0) break

                val bytes = ByteArray(sampleSize)
                buffer.get(bytes)
                val rawPacket = String(bytes, StandardCharsets.UTF_8).trim()
                val startTimeUs = extractor.sampleTime

                if (rawPacket.isNotBlank()) {
                    if (formatName == "ass") {
                        // إذا كان السطر هو سطر حوار كامل
                        if (rawPacket.startsWith("Dialogue:", true) || rawPacket.startsWith("Comment:", true)) {
                            events.add(rawPacket)
                        } else {
                            // حزمة Matroska للـ ASS تأتي عادة: ReadOrder, Layer, Style, Name, MarginL, MarginR, MarginV, Effect, Text
                            val commaIndex = rawPacket.indexOf(',')
                            val cleanPacket = if (commaIndex in 0 until 10 && rawPacket.substring(0, commaIndex).all { it.isDigit() }) {
                                rawPacket.substring(commaIndex + 1) // تجاوز ReadOrder
                            } else {
                                rawPacket
                            }

                            // تقسيم حقول ASS
                            val parts = cleanPacket.split(",", limit = 8)
                            val layer = parts.getOrNull(0)?.trim() ?: "0"
                            val style = parts.getOrNull(1)?.trim()?.ifBlank { "Default" } ?: "Default"
                            val actor = parts.getOrNull(2)?.trim() ?: ""
                            val ml = parts.getOrNull(3)?.trim() ?: "0"
                            val mr = parts.getOrNull(4)?.trim() ?: "0"
                            val mv = parts.getOrNull(5)?.trim() ?: "0"
                            val effect = parts.getOrNull(6)?.trim() ?: ""
                            val dialogueText = parts.getOrNull(7) ?: cleanPacket

                            val startMs = (startTimeUs / 1000).coerceAtLeast(0)
                            val endMs = startMs + 3000L // مدة افتراضية 3 ثوانٍ إن لم تكن محددة

                            events.add("Dialogue: $layer,${formatAssTime(startMs)},${formatAssTime(endMs)},$style,$actor,$ml,$mr,$mv,$effect,$dialogueText")
                        }
                    } else {
                        // معالجة SRT
                        val startMs = (startTimeUs / 1000).coerceAtLeast(0)
                        val endMs = startMs + 3000L
                        events.add("${cueNumber++}\n${formatSrtTime(startMs)} --> ${formatSrtTime(endMs)}\n$rawPacket\n")
                    }
                }
                extractor.advance()
            }

            val fullScript = if (formatName == "ass") {
                val headerText = if (assHeader.isNotBlank()) {
                    if (!assHeader.contains("[Events]")) {
                        "$assHeader\n\n[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                    } else {
                        "$assHeader\n"
                    }
                } else {
                    defaultAssHeader()
                }
                headerText + events.joinToString("\n")
            } else {
                events.joinToString("\n")
            }

            return formatName to fullScript
        } catch (e: Exception) {
            e.printStackTrace()
            return "ass" to defaultAssHeader()
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

    fun defaultAssHeader(): String = """
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
