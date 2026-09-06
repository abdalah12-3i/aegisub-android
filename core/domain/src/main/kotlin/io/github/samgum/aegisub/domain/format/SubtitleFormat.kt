package io.github.samgum.aegisub.domain.format

import io.github.samgum.aegisub.domain.model.AssScript

/** 导出时间精度。 */
enum class TimePrecision { TWO_MS, THREE_MS, AUTO }

data class ReadOptions(val detectEncoding: Boolean = true)

data class WriteOptions(
    val timePrecision: TimePrecision = TimePrecision.AUTO,
    val stripTags: Boolean = false,
)

/** 字幕格式编解码器接口。 */
interface SubtitleFormat {
    val name: String
    val extensions: List<String>

    fun canRead(content: String): Boolean
    fun canWrite(fileName: String): Boolean = extensions.any { fileName.endsWith(it, ignoreCase = true) }

    fun read(text: String, options: ReadOptions = ReadOptions()): AssScript
    fun write(script: AssScript, options: WriteOptions = WriteOptions()): String
}
