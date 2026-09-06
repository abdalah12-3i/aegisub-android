package io.github.samgum.aegisub.domain.format

object SubtitleImport {

    data class Resolved(val name: String, val format: String)

    fun resolve(fileName: String, content: String, fallbackName: String = "Imported Subtitle"): Resolved {
        val format = (FormatRegistry.detect(content) ?: FormatRegistry.detectByExtension(fileName))?.name ?: "txt"
        val base = fileName.substringBeforeLast('.', fileName).trim().ifBlank { fallbackName }
        return Resolved(name = base, format = format)
    }
}
