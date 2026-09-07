package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import io.github.samgum.aegisub.domain.preview.SubtitleRenderInfo

/**
 * شاشة رسم ومعاينة الترجمة فوق الفيديو (تدعم الآن رسم الخطوط المخصصة المكتشفة)
 */
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
            val scaleY = (size.height / info.playResY.coerceAtLeast(1)).toFloat()
            val scaleX = (size.width / info.playResX.coerceAtLeast(1)).toFloat()
            val rawFontPx = (style.fontSize * scaleY).toFloat()
            val fontPx = rawFontPx.coerceIn(1f, size.height * 0.11f)
            val fontScaleUsed = fontPx / (style.fontSize.coerceAtLeast(0.0001).toFloat())
            val outlinePx = (style.outlineWidth * fontScaleUsed).toFloat()
                .coerceIn(0f, fontPx * 0.14f)
            val shadowPx = (style.shadowWidth * fontScaleUsed).toFloat()
                .coerceAtMost(fontPx * 0.3f)
            val marginLeftPx = info.margins.left * scaleX
            val marginRightPx = info.margins.right * scaleX
            val marginTopPx = info.margins.vertical * scaleY

            // استدعاء الخط المخصص من مجلد الخطوط
            val customFamily = FontManager.getFontFamily(context, style.font)

            val baseStyle = TextStyle(
                color = style.primary.toColor(),
                fontSize = (fontPx / (density * fontScale)).sp,
                fontWeight = if (style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (style.italic) FontStyle.Italic else FontStyle.Normal,
                fontFamily = customFamily,
            )
            val maxWidthPx = (size.width - marginLeftPx - marginRightPx).coerceAtLeast(1f).toInt()
            val layout = measurer.measure(
                text = info.text,
                style = baseStyle,
                constraints = Constraints(maxWidth = maxWidthPx),
                overflow = TextOverflow.Visible,
            )
            val topLeft = computeTopLeft(
                alignment = style.alignment,
                pos = info.pos,
                scaleX = scaleX,
                scaleY = scaleY,
                layoutWidth = layout.size.width.toFloat(),
                layoutHeight = layout.size.height.toFloat(),
                canvasWidth = size.width,
                canvasHeight = size.height,
                marginLeftPx = marginLeftPx,
                marginRightPx = marginRightPx,
                marginVerticalPx = marginTopPx,
            )
            val maxTx = (size.width - layout.size.width).coerceAtLeast(0f)
            val maxTy = (size.height - layout.size.height).coerceAtLeast(0f)
            val drawAt = Offset(topLeft.x.coerceIn(0f, maxTx), topLeft.y.coerceIn(0f, maxTy))
            if (shadowPx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = Offset(drawAt.x + shadowPx, drawAt.y + shadowPx),
                )
            }
            if (outlinePx > 0f) {
                drawText(
                    textLayoutResult = layout,
                    topLeft = drawAt,
                    color = style.outline.toColor(),
                    drawStyle = Stroke(width = outlinePx),
                )
            }
            drawText(textLayoutResult = layout, topLeft = drawAt)
        }
    }
}

private fun DrawScope.computeTopLeft(
    alignment: Int,
    pos: Pair<Int, Int>?,
    scaleX: Float,
    scaleY: Float,
    layoutWidth: Float,
    layoutHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    marginLeftPx: Float,
    marginRightPx: Float,
    marginVerticalPx: Float,
): Offset {
    if (pos != null) {
        val ax = pos.first * scaleX
        val ay = pos.second * scaleY
        return Offset(ax - layoutWidth / 2f, ay - layoutHeight / 2f)
    }
    val x = when (alignment) {
        1, 4, 7 -> marginLeftPx
        3, 6, 9 -> canvasWidth - layoutWidth - marginRightPx
        else -> (canvasWidth - layoutWidth) / 2f
    }
    val y = when (alignment) {
        7, 8, 9 -> marginVerticalPx
        4, 5, 6 -> (canvasHeight - layoutHeight) / 2f
        else -> canvasHeight - layoutHeight - marginVerticalPx
    }
    return Offset(x, y)
}
