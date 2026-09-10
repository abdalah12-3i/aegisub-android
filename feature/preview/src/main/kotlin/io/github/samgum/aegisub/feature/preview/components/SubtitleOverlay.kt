package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
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
import io.github.samgum.aegisub.domain.edit.VisualTags
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

            // قراءة الوسوم التفاعلية
            val parsedColor = parseInlineColor(info.text) ?: style.primary.toColor()
            val parsedFontName = parseInlineFont(info.text) ?: style.font
            val parsedFontSize = parseInlineFontSize(info.text) ?: style.fontSize
            val rotationDeg = VisualTags.getRotation(info.text).toFloat().let { if (it == 0f) style.angle.toFloat() else it }
            val scaleXFactor = parseInlineScale(info.text, "fscx") ?: (style.scaleX.toFloat() / 100f)
            val scaleYFactor = parseInlineScale(info.text, "fscy") ?: (style.scaleY.toFloat() / 100f)
            val clipRect = VisualTags.getClip(info.text)

            val rawFontPx = (parsedFontSize * scaleY).toFloat()
            val fontPx = rawFontPx.coerceIn(10f, size.height * 0.30f)
            val fontScaleUsed = fontPx / (parsedFontSize.coerceAtLeast(0.0001).toFloat())
            
            val outlineWidthPx = (style.outlineWidth * fontScaleUsed).toFloat().coerceIn(1.5f, fontPx * 0.12f)
            val shadowPx = (style.shadowWidth * fontScaleUsed).toFloat().coerceAtMost(fontPx * 0.2f)

            // 1. معالجة رسم مسارات الفيكتور \p1
            val drawingCommands = extractDrawingCommands(info.text)
            if (drawingCommands != null) {
                val currentPos = info.pos
                val originX = if (currentPos != null) currentPos.first * scaleX else 0f
                val originY = if (currentPos != null) currentPos.second * scaleY else 0f
                val drawingPath = parseAssDrawing(drawingCommands, originX, originY, scaleX, scaleY)
                if (drawingPath != null) {
                    if (outlineWidthPx > 0f) {
                        drawPath(path = drawingPath, color = style.outline.toColor(), style = Stroke(width = outlineWidthPx * 2f))
                    }
                    drawPath(path = drawingPath, color = parsedColor, style = Fill)
                }
                return@forEach
            }

            // 2. معالجة ورسم النصوص العادية
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

            val centerX = topLeft.x + layout.size.width / 2f
            val centerY = topLeft.y + layout.size.height / 2f

            // دالة الرسم الداخلية المطبقة لجميع التأثيرات
            val drawContent: DrawScope.() -> Unit = {
                rotate(degrees = rotationDeg, pivot = Offset(centerX, centerY)) {
                    scale(scaleX = scaleXFactor, scaleY = scaleYFactor, pivot = Offset(centerX, centerY)) {
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

            // تطبيق القص \clip إذا كان موجوداً
            if (clipRect != null) {
                val cx1 = clipRect.x1 * scaleX
                val cy1 = clipRect.y1 * scaleY
                val cx2 = clipRect.x2 * scaleX
                val cy2 = clipRect.y2 * scaleY
                val left = minOf(cx1, cx2)
                val top = minOf(cy1, cy2)
                val right = maxOf(cx1, cx2)
                val bottom = maxOf(cy1, cy2)

                if (!clipRect.inverse) {
                    clipRect(left = left, top = top, right = right, bottom = bottom) {
                        drawContent()
                    }
                } else {
                    drawContent()
                }
            } else {
                drawContent()
            }
        }
    }
}

private fun parseInlineScale(text: String, tag: String): Float? {
    val regex = Regex("""\\$tag([0-9]+(?:\.[0-9]+)?)""")
    val match = regex.find(text) ?: return null
    return (match.groupValues[1].toFloatOrNull() ?: 100f) / 100f
}

private fun extractDrawingCommands(text: String): String? {
    val pMatch = Regex("""\\p([1-9])""").find(text) ?: return null
    val afterP = text.substring(pMatch.range.last + 1)
    val closeBrace = afterP.indexOf('}')
    val drawingBody = if (closeBrace >= 0) afterP.substring(closeBrace + 1) else afterP
    val p0Idx = drawingBody.indexOf("""\p0""")
    val rawDrawing = if (p0Idx >= 0) drawingBody.substring(0, p0Idx) else drawingBody
    val clean = rawDrawing.replace(Regex("""\{[^}]*\}"""), "").trim()
    return if (clean.isNotBlank()) clean else null
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

private fun parseAssDrawing(
    drawingText: String,
    originX: Float,
    originY: Float,
    scaleX: Float,
    scaleY: Float,
): Path? {
    val tokens = drawingText.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    val path = Path()
    var i = 0
    var currentCmd = ""

    try {
        while (i < tokens.size) {
            val token = tokens[i]
            val firstChar = token[0].lowercaseChar()
            if (firstChar in listOf('m', 'l', 'b', 'c', 'n')) {
                currentCmd = firstChar.toString()
                i++
                continue
            }
            when (currentCmd) {
                "m", "n" -> {
                    if (i + 1 < tokens.size) {
                        val x = tokens[i].toFloatOrNull() ?: 0f
                        val y = tokens[i + 1].toFloatOrNull() ?: 0f
                        path.moveTo(originX + x * scaleX, originY + y * scaleY)
                        i += 2
                        currentCmd = "l"
                    } else i++
                }
                "l" -> {
                    if (i + 1 < tokens.size) {
                        val x = tokens[i].toFloatOrNull() ?: 0f
                        val y = tokens[i + 1].toFloatOrNull() ?: 0f
                        path.lineTo(originX + x * scaleX, originY + y * scaleY)
                        i += 2
                    } else i++
                }
                "b" -> {
                    if (i + 5 < tokens.size) {
                        val x1 = tokens[i].toFloatOrNull() ?: 0f
                        val y1 = tokens[i + 1].toFloatOrNull() ?: 0f
                        val x2 = tokens[i + 2].toFloatOrNull() ?: 0f
                        val y2 = tokens[i + 3].toFloatOrNull() ?: 0f
                        val x3 = tokens[i + 4].toFloatOrNull() ?: 0f
                        val y3 = tokens[i + 5].toFloatOrNull() ?: 0f
                        path.cubicTo(
                            originX + x1 * scaleX, originY + y1 * scaleY,
                            originX + x2 * scaleX, originY + y2 * scaleY,
                            originX + x3 * scaleX, originY + y3 * scaleY,
                        )
                        i += 6
                    } else i++
                }
                "c" -> {
                    path.close()
                    currentCmd = ""
                }
                else -> i++
            }
        }
        path.close()
        return path
    } catch (e: Exception) {
        return null
    }
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
