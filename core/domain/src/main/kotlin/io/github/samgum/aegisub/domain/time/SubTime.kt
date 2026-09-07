package io.github.samgum.aegisub.domain.time

import java.util.Locale

@JvmInline
value class SubTime private constructor(val micros: Long) : Comparable<SubTime> {

    val millis: Long get() = micros / 1_000

    operator fun plus(other: SubTime): SubTime = ofMicros(micros + other.micros)
    operator fun minus(other: SubTime): SubTime = ofMicros(micros - other.micros)
    override fun compareTo(other: SubTime): Int = micros.compareTo(other.micros)
    override fun toString(): String = "SubTime(${micros}µs)"

    fun toSrtString(): String {
        val total = millis
        val h = total / 3_600_000
        val m = (total % 3_600_000) / 60_000
        val s = (total % 60_000) / 1_000
        val mm = total % 1_000
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", h, m, s, mm)
    }

    fun toLrcString(format: LrcTimeFormat): String {
        val totalSec = micros / 1_000_000
        val mm = totalSec / 60
        val ss = totalSec % 60
        val fracMicros = micros % 1_000_000
        val sep = if (format.separator == LrcSeparator.DOT) '.' else ':'
        return when (format.precision) {
            LrcPrecision.CENTI -> {
                val cc = fracMicros / 10_000
                String.format(Locale.US, "[%02d:%02d%s%02d]", mm, ss, sep, cc)
            }
            LrcPrecision.MILLI -> {
                val mmm = fracMicros / 1_000
                String.format(Locale.US, "[%02d:%02d%s%03d]", mm, ss, sep, mmm)
            }
        }
    }

    fun toAssString(msPrecision: Boolean): String {
        if (msPrecision) {
            val total = millis
            val h = total / 3_600_000
            val m = (total % 3_600_000) / 60_000
            val s = (total % 60_000) / 1_000
            val mm = total % 1_000
            return String.format(Locale.US, "%d:%02d:%02d.%03d", h, m, s, mm)
        }
        val cs = (micros + 5_000) / 10_000
        val h = cs / 360_000
        val m = (cs % 360_000) / 6_000
        val s = (cs % 6_000) / 100
        val cc = cs % 100
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cc)
    }

    companion object {
        const val MAX_MICROS: Long = 10L * 60 * 60 * 1_000_000 // 10h
        val ZERO: SubTime = SubTime(0)

        fun ofMicros(v: Long): SubTime = SubTime(v.coerceIn(0, MAX_MICROS))
        fun ofMillis(v: Long): SubTime = ofMicros(v * 1_000)
        fun ofCentiseconds(v: Long): SubTime = ofMicros(v * 10_000)

        fun parseSrt(text: String): SubTime = ofMicros(parseFlexibleMs(text) * 1_000)
        fun parseAss(text: String): SubTime = ofMicros(parseFlexibleMs(text) * 1_000)

        fun parseLrc(tag: String): SubTime {
            val inner = tag.trim().removeSurrounding("[", "]")
            val firstColon = inner.indexOf(':')
            require(firstColon > 0) { "Invalid LRC tag: $tag" }
            val mm = inner.substring(0, firstColon).toLong()
            val rest = inner.substring(firstColon + 1)
            val sep = if ('.' in rest) '.' else ':'
            val (ssStr, fracStr) = rest.split(sep, limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val ss = ssStr.toLong()
            val frac = fracStr.ifEmpty { "0" }
            val micros = (mm * 60 + ss) * 1_000_000 + when (frac.length) {
                2 -> frac.toLong() * 10_000
                3 -> frac.toLong() * 1_000
                else -> (frac.toDouble() * 1_000_000).toLong()
            }
            return ofMicros(micros)
        }

        /** قراءة الوقت بمرونة مع دعم الأرقام الإنجليزية والعربية */
        private fun parseFlexibleMs(text: String): Long {
            var time = 0L
            var current = 0
            var afterDecimal = -1
            for (c in text) {
                when {
                    c == ':' || c == '：' -> { time = time * 60 + current; current = 0 }
                    c == '.' || c == ',' || c == '،' || c == '٫' -> {
                        time = (time * 60 + current) * 1000
                        current = 0
                        afterDecimal = 100
                    }
                    c in '0'..'9' -> {
                        val digit = c - '0'
                        if (afterDecimal < 0) current = current * 10 + digit
                        else { time += digit.toLong() * afterDecimal; afterDecimal /= 10 }
                    }
                    c in '٠'..'٩' -> { // دعم الأرقام المشرقية العربية
                        val digit = c - '٠'
                        if (afterDecimal < 0) current = current * 10 + digit
                        else { time += digit.toLong() * afterDecimal; afterDecimal /= 10 }
                    }
                }
            }
            if (afterDecimal < 0) time = (time * 60 + current) * 1000
            return time
        }
    }
}
