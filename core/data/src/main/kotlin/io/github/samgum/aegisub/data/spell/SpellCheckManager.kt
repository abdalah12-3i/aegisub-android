package io.github.samgum.aegisub.data.spell

import android.content.Context
import android.os.Environment
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * محرك التدقيق الإملائي وقراءة القواميس الخارجية واقتراح التصحيحات
 */
object SpellCheckManager {
    private val dictionaryWords = ConcurrentHashMap.newKeySet<String>()
    @Volatile private var isLoaded = false

    /** إرجاع مسارات مجلد القواميس */
    fun getDictionaryDirectories(context: Context): List<File> {
        val dirs = mutableListOf<File>()
        try {
            val docs = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Aegisub/dictionaries")
            if (!docs.exists()) docs.mkdirs()
            dirs.add(docs)
        } catch (_: Exception) {}

        context.getExternalFilesDir("dictionaries")?.let {
            if (!it.exists()) it.mkdirs()
            dirs.add(it)
        }
        return dirs
    }

    /** تحميل الكلمات من جميع ملفات القواميس (.dic و .txt) */
    fun loadDictionaries(context: Context) {
        if (isLoaded && dictionaryWords.isNotEmpty()) return
        val dirs = getDictionaryDirectories(context)
        dirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension.equals("dic", true) || file.extension.equals("txt", true))) {
                        runCatching {
                            file.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    val word = line.trim().substringBefore('/').substringBefore('\t')
                                    if (word.isNotBlank() && word.length > 1) {
                                        dictionaryWords.add(word.lowercase())
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        isLoaded = true
    }

    /** استخراج الكلمات وتجاوز وسوم ASS البرمجية {...} */
    fun checkMisspelledWords(context: Context, text: String): List<String> {
        loadDictionaries(context)
        if (dictionaryWords.isEmpty() || text.isBlank()) return emptyList()

        // إزالة وسوم ASS لتفادي فحص الأكواد مثل {\pos(10,20)}
        val cleanText = text.replace(Regex("""\{[^}]*\}"""), " ")
            .replace(Regex("""\\[Nnh]"""), " ")

        val words = cleanText.split(Regex("""[^\p{L}\p{M}]+"""))
            .filter { it.length > 1 && !it.all { ch -> ch.isDigit() } }

        return words.filter { word ->
            !dictionaryWords.contains(word.lowercase())
        }.distinct()
    }

    /** اقتراح أقرب الكلمات الصحيحة باستخدام خوارزمية Levenshtein Distance */
    fun getSuggestions(misspelledWord: String, maxSuggestions: Int = 4): List<String> {
        val target = misspelledWord.lowercase()
        if (dictionaryWords.isEmpty()) return emptyList()

        return dictionaryWords.asSequence()
            .filter { abs(it.length - target.length) <= 2 }
            .map { it to levenshteinDistance(target, it) }
            .filter { it.second <= 2 }
            .sortedBy { it.second }
            .take(maxSuggestions)
            .map { it.first }
            .toList()
    }

    private fun levenshteinDistance(lhs: CharSequence, rhs: CharSequence): Int {
        val len0 = lhs.length + 1
        val len1 = rhs.length + 1
        var cost = IntArray(len0) { it }
        var newCost = IntArray(len0)

        for (i in 1 until len1) {
            newCost[0] = i
            for (j in 1 until len0) {
                val match = if (lhs[j - 1] == rhs[i - 1]) 0 else 1
                newCost[j] = min(min(cost[j] + 1, newCost[j - 1] + 1), cost[j - 1] + match)
            }
            val swap = cost
            cost = newCost
            newCost = swap
        }
        return cost[len0 - 1]
    }
}
