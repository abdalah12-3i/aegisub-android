package io.github.samgum.aegisub.domain.preview

import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.model.Margins

data class SubtitleRenderInfo(
    val text: String,
    val style: AssStyle,
    val margins: Margins,
    val playResX: Int = 1920,
    val playResY: Int = 1080,
    val pos: Pair<Int, Int>? = null,
)

object ActiveSubtitleResolver {

    fun activeEvent(script: AssScript, positionMs: Long): AssEvent? =
        script.events.firstOrNull { event ->
            !event.comment &&
                event.start.millis <= positionMs &&
                positionMs < event.end.millis
        }

    fun activeEvents(script: AssScript, positionMs: Long): List<AssEvent> =
        script.events.asSequence()
            .filter { event ->
                !event.comment &&
                    event.start.millis <= positionMs &&
                    positionMs < event.end.millis
            }
            .sortedBy { it.layer }
            .toList()

    fun renderInfo(script: AssScript, positionMs: Long): SubtitleRenderInfo? =
        renderInfos(script, positionMs).firstOrNull()

    fun renderInfos(script: AssScript, positionMs: Long): List<SubtitleRenderInfo> {
        val rx = script.getScriptInfo("PlayResX")?.toIntOrNull()?.let { if (it <= 384) 1920 else it } ?: 1920
        val ry = script.getScriptInfo("PlayResY")?.toIntOrNull()?.let { if (it <= 288) 1080 else it } ?: 1080

        return activeEvents(script, positionMs).map { event ->
            val style = resolveStyle(script, event)
            val margins = Margins(
                left = style.margins.left + event.margins.left,
                right = style.margins.right + event.margins.right,
                vertical = style.margins.vertical + event.margins.vertical,
            )
            // إرسال النص الأصلي كاملاً مع وسوم الألوان والمواضع بدلاً من تجريدها
            SubtitleRenderInfo(
                text = event.text,
                style = style,
                margins = margins,
                playResX = rx,
                playResY = ry,
                pos = VisualTags.getPos(event.text),
            )
        }
    }

    fun resolveStyle(script: AssScript, event: AssEvent): AssStyle =
        script.styles.firstOrNull { it.name.equals(event.style, ignoreCase = true) }
            ?: script.styles.firstOrNull { it.name.equals("Default", ignoreCase = true) }
            ?: script.styles.firstOrNull()
            ?: AssStyle()
}
