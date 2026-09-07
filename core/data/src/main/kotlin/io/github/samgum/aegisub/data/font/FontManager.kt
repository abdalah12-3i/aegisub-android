package io.github.samgum.aegisub.data.font

import android.content.Context
import android.graphics.Typeface
import android.os.Environment
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * محرك إدارة وفحص الخطوط الخارجية في الهاتف
 */
object FontManager {
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()
    private val fontFamilyCache = ConcurrentHashMap<String, FontFamily>()

    /** إرجاع المجلدات المتاحة للخطوط (وينشئ المجلدات تلقائياً) */
    fun getFontDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()

        // 1. مجلد المستندات العام Documents/Aegisub/fonts (الأسهل في تطبيق ملفاتي)
        try {
            val docs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Aegisub/fonts")
            if (!docs.exists()) docs.mkdirs()
            dirs.add(docs)
        } catch (_: Exception) {}

        // 2. مجلد التطبيق المباشر داخل الذاكرة Android/data/.../files/fonts
        context.getExternalFilesDir("fonts")?.let {
            if (!it.exists()) it.mkdirs()
            dirs.add(it)
        }

        // 3. مجلد داخلي احتياطي
        val internal = File(context.filesDir, "fonts")
        if (!internal.exists()) internal.mkdirs()
        dirs.add(internal)

        return dirs.distinctBy { it.absolutePath }
    }

    /** فحص المجلدات واستخراج قائمة بأسماء الخطوط المتاحة */
    fun scanFonts(context: Context): List<String> {
        val names = mutableSetOf<String>()
        getFontDirectories(context).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension.equals("ttf", true) || file.extension.equals("otf", true))) {
                        names.add(file.nameWithoutExtension)
                    }
                }
            }
        }
        return names.sorted()
    }

    /** قائمة الخطوط المقترحة (الخطوط الافتراضية + الخطوط المكتشفة في المجلد) */
    fun getAvailableFontNames(context: Context): List<String> {
        val defaults = listOf("Default", "Arial", "Roboto", "Tahoma", "Times New Roman", "Sans-Serif", "Serif")
        val custom = scanFonts(context)
        return (custom + defaults).distinct()
    }

    /** البحث عن ملف الخط المطابق للاسم المختار */
    fun findFontFile(context: Context, fontName: String): File? {
        if (fontName.isBlank() || fontName.equals("Default", true)) return null
        getFontDirectories(context).forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension.equals("ttf", true) || file.extension.equals("otf", true))) {
                        if (file.nameWithoutExtension.equals(fontName, ignoreCase = true) ||
                            file.name.equals(fontName, ignoreCase = true)
                        ) {
                            return file
                        }
                    }
                }
            }
        }
        return null
    }

    /** تحميل الخط كـ Typeface مع التخزين المؤقت للذاكرة */
    fun getTypeface(context: Context, fontName: String): Typeface? {
        if (fontName.isBlank()) return null
        return typefaceCache.getOrPut(fontName.lowercase()) {
            val file = findFontFile(context, fontName) ?: return null
            runCatching { Typeface.createFromFile(file) }.getOrNull() ?: return null
        }
    }

    /** تحميل الخط كـ Compose FontFamily لاستخدامه في رسم المعاينة */
    fun getFontFamily(context: Context, fontName: String): FontFamily? {
        if (fontName.isBlank()) return null
        return fontFamilyCache.getOrPut(fontName.lowercase()) {
            val tf = getTypeface(context, fontName) ?: return null
            FontFamily(tf)
        }
    }
}
