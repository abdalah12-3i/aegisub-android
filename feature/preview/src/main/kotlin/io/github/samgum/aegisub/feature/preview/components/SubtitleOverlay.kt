package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.sp
import io.github.samgum.aegisub.data.font.FontManager
import io.github.samgum.aegisub.domain.model.AssColor
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo

@Composable
fun SubtitleOverlay(
    renderInfos: List<SubtitleRenderInfo>,
    modifier: Modifier = Modifier,
) {
    if (renderInfos.isEmpty()) {
        Box(modifier.fillMaxSize())
        return
    }
    val measurer = rememberTextMeasurer()
    val context = LocalContext.current

    Canvas(modifier = modifier.fillMaxSize()) {
        renderInfos.forEach { info ->
            if (info.text.isBlank()) return@forEach
            val style = info.style

            val resX = if (info.playResX <= 384) 1920 else info.playResX
            val resY = if (info.playResY <= 288) 1080 else info.playResY

            val scaleY = (size.height / resY.toFloat()).coerceAtLeast(0.001f)
            val scaleX = (size.width / resX.toFloat()).coerceAtLeast(0.001f)

            val parsedColor = parseInlineColor(info.text) ?: style.primary.toColor()
            val parsedFontName = parseInlineFont(info.text) ?: style.font
            val parsedFontSize = parseInlineFontSize(info.text) ?: style.fontSize

            val rawFontPx = (parsedFontSize * scaleY).toFloat()
            val fontPx = rawFontPx.coerceIn(10f, size.height * 0.30f)
            val fontScaleUsed = fontPx / (parsedFontSize.coerceAtLeast(0.0001).toFloat())
            
            val outlineWidthPx = (style.outlineWidth * fontScaleUsed).toFloat().coerceIn(1.5f, fontPx * 0.12f)
            val shadowPx = (style.shadowWidth * fontScaleUsed).toFloat().coerceAtMost(fontPx * 0.2f)

            val customFamily = FontManager.getFontFamily(context, parsedFontName)

            val baseStyle = TextStyle(
                color = parsedColor,
                fontSize = (fontPx / (density * fontScale)).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = customFamily,
            )

            val cleanDisplay = stripAssTags(info.text)
            if (cleanDisplay.isBlank()) return@forEach

            val layout = measurer.measure(
                text = cleanDisplay,
                style = baseStyle,
                constraints = Constraints(maxWidth = size.width.toInt()),
                overflow = TextOverflow.Visible,
            )

            val topLeft = computeTopLeftSafe(
                alignment = style.alignment,
                pos = info.pos,
                scaleX = scaleX,
                scaleY = scaleY,
                layoutWidth = layout.size.width.toFloat(),
                layoutHeight = layout.size.height.toFloat(),
                canvasWidth = size.width,
                canvasHeight = size.height,
            )

            if (shadowPx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(topLeft.x + shadowPx, topLeft.y + shadowPx),
                    color = style.shadow.toColor(),
                    drawStyle = Fill,
                )
            }

            if (outlineWidthPx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = topLeft,
                    color = style.outline.toColor(),
                    drawStyle = Stroke(width = outlineWidthPx * 2f),
                )
            }

            drawText(
                textLayoutResult = layout,
                topLeft = topLeft,
                color = parsedColor,
                drawStyle = Fill,
            )
        }
    }
}

private fun parseInlineColor(raw: String): Color? {
    val regex = Regex("""\\(?:c|1c)&H([0-9a-fA-F]+)&?""")
    val match = regex.find(raw) ?: return null
    val hex = match.groupValues[1].removePrefix("&H").removePrefix("&h").removeSuffix("&")
    return runCatching {
        val c = AssColor.parseAss("&H$hex&")
        Color(c.r, c.g, c.b, 255)
    }.getOrNull()
}

private fun parseInlineFont(raw: String): String? {
    val regex = Regex("""\\fn([^\}\\]+)""")
    val match = regex.find(raw) ?: return null
    return match.groupValues[1].trim().ifBlank { null }
}

private fun parseInlineFontSize(raw: String): Double? {
    val regex = Regex("""\\fs([0-9]+(?:\.[0-9]+)?)""")
    val match = regex.find(raw) ?: return null
    return match.groupValues[1].toDoubleOrNull()
}

private fun stripAssTags(text: String): String {
    return text.replace(Regex("""\{[^}]*\}"""), "")
        .replace("\\N", "\n")
        .replace("\\n", "\n")
        .replace("\\h", " ")
        .trim()
}

private fun computeTopLeftSafe(
    alignment: Int,
    pos: Pair<Int, Int>?,
    scaleX: Float,
    scaleY: Float,
    layoutWidth: Float,
    layoutHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
): Offset {
    if (pos != null) {
        val ax = pos.first * scaleX
        val ay = pos.second * scaleY
        var x = ax - layoutWidth / 2f
        var y = ay - layoutHeight / 2f
        x = x.coerceIn(8f, (canvasWidth - layoutWidth - 8f).coerceAtLeast(8f))
        y = y.coerceIn(8f, (canvasHeight - layoutHeight - 8f).coerceAtLeast(8f))
        return Offset(x, y)
    }

    val x = when (alignment) {
        1, 4, 7 -> 24f
        3, 6, 9 -> canvasWidth - layoutWidth - 24f
        else -> (canvasWidth - layoutWidth) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> 24f
        4, 5, 6 -> (canvasHeight - layoutHeight) / 2f
        else -> canvasHeight - layoutHeight - 24f
    }
    return Offset(x.coerceAtLeast(8f), y.coerceAtLeast(8f))
}
