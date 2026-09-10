package io.github.samgum.aegisub.feature.preview.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.domain.edit.VisualTags
import kotlin.math.hypot
import kotlin.math.roundToInt

enum class VisualToolMode { POSITION, MOVE, CLIP }

@Composable
fun VisualTypesettingOverlay(
    playResX: Int,
    playResY: Int,
    mode: VisualToolMode,
    currentPos: Pair<Int, Int>?,
    currentMove: VisualTags.MoveParams?,
    currentClip: VisualTags.ClipRect? = null,
    onPosChange: (x: Int, y: Int) -> Unit,
    onMoveChange: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit,
    onClipChange: (x1: Int, y1: Int, x2: Int, y2: Int) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val handlePx = with(density) { 32.dp.toPx() }
    val defaultPos = remember(playResX, playResY) { (playResX / 2) to (playResY * 9 / 10) }

    var livePos by remember(currentPos, mode) { mutableStateOf(currentPos ?: defaultPos) }

    val initA = currentMove?.let { it.x1 to it.y1 } ?: currentPos ?: defaultPos
    val initB = currentMove?.let { it.x2 to it.y2 } ?: (defaultPos.first to defaultPos.second / 2)
    var liveA by remember(currentMove, mode) { mutableStateOf(initA) }
    var liveB by remember(currentMove, mode) { mutableStateOf(initB) }

    // رسم الكليب التفاعلي باللمس
    var clipStart by remember { mutableStateOf<Offset?>(null) }
    var clipEnd by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .pointerInput(playResX, playResY, mode) {
                detectDragGestures(
                    onDragStart = { offset ->
                        when (mode) {
                            VisualToolMode.POSITION -> livePos = project(offset, boxSize, playResX, playResY)
                            VisualToolMode.MOVE -> {
                                val a = toPx(liveA, boxSize, playResX, playResY)
                                val b = toPx(liveB, boxSize, playResX, playResY)
                                if (hypot(offset.x - a.x, offset.y - a.y) <= hypot(offset.x - b.x, offset.y - b.y)) {
                                    liveA = project(offset, boxSize, playResX, playResY)
                                } else {
                                    liveB = project(offset, boxSize, playResX, playResY)
                                }
                            }
                            VisualToolMode.CLIP -> {
                                clipStart = offset
                                clipEnd = offset
                            }
                        }
                    },
                    onDragEnd = {
                        when (mode) {
                            VisualToolMode.POSITION -> onPosChange(livePos.first, livePos.second)
                            VisualToolMode.MOVE -> onMoveChange(liveA.first, liveA.second, liveB.first, liveB.second)
                            VisualToolMode.CLIP -> {
                                val s = clipStart
                                val e = clipEnd
                                if (s != null && e != null) {
                                    val p1 = project(s, boxSize, playResX, playResY)
                                    val p2 = project(e, boxSize, playResX, playResY)
                                    onClipChange(minOf(p1.first, p2.first), minOf(p1.second, p2.second), maxOf(p1.first, p2.first), maxOf(p1.second, p2.second))
                                }
                            }
                        }
                    },
                    onDragCancel = {
                        livePos = currentPos ?: defaultPos
                        liveA = initA
                        liveB = initB
                        clipStart = null
                        clipEnd = null
                    },
                ) { change, _ ->
                    when (mode) {
                        VisualToolMode.POSITION -> livePos = project(change.position, boxSize, playResX, playResY)
                        VisualToolMode.MOVE -> {
                            val a = toPx(liveA, boxSize, playResX, playResY)
                            val b = toPx(liveB, boxSize, playResX, playResY)
                            if (hypot(change.position.x - a.x, change.position.y - a.y) <= hypot(change.position.x - b.x, change.position.y - b.y)) {
                                liveA = project(change.position, boxSize, playResX, playResY)
                            } else {
                                liveB = project(change.position, boxSize, playResX, playResY)
                            }
                        }
                        VisualToolMode.CLIP -> {
                            clipEnd = change.position
                        }
                    }
                    change.consume()
                }
            },
    ) {
        if (boxSize.width > 0 && boxSize.height > 0 && playResX > 0 && playResY > 0) {
            when (mode) {
                VisualToolMode.POSITION -> Handle(livePos, boxSize, playResX, playResY, handlePx, Color.White)
                VisualToolMode.MOVE -> {
                    Handle(liveA, boxSize, playResX, playResY, handlePx, Color(0xFF4CAF50))
                    Handle(liveB, boxSize, playResX, playResY, handlePx, Color(0xFFFF9800))
                }
                VisualToolMode.CLIP -> {
                    val s = clipStart
                    val e = clipEnd
                    if (s != null && e != null) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val left = minOf(s.x, e.x)
                            val top = minOf(s.y, e.y)
                            val w = (maxOf(s.x, e.x) - left).coerceAtLeast(1f)
                            val h = (maxOf(s.y, e.y) - top).coerceAtLeast(1f)
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(left, top),
                                size = Size(w, h),
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Handle(
    pos: Pair<Int, Int>,
    boxSize: IntSize,
    resX: Int,
    resY: Int,
    handlePx: Float,
    ring: Color,
) {
    val hx = (pos.first.toFloat() / resX) * boxSize.width - handlePx / 2f
    val hy = (pos.second.toFloat() / resY) * boxSize.height - handlePx / 2f
    Box(
        modifier = Modifier
            .offset { IntOffset(hx.roundToInt(), hy.roundToInt()) }
            .size(32.dp)
            .background(Color(0x66000000), CircleShape)
            .border(2.dp, ring, CircleShape),
    )
}

private fun toPx(pos: Pair<Int, Int>, size: IntSize, resX: Int, resY: Int): Offset {
    if (size.width == 0 || size.height == 0) return Offset.Zero
    return Offset((pos.first.toFloat() / resX) * size.width, (pos.second.toFloat() / resY) * size.height)
}

private fun project(offset: Offset, size: IntSize, resX: Int, resY: Int): Pair<Int, Int> {
    if (size.width == 0 || size.height == 0) return (resX / 2) to resY
    val x = ((offset.x / size.width) * resX).roundToInt().coerceIn(0, resX)
    val y = ((offset.y / size.height) * resY).roundToInt().coerceIn(0, resY)
    return x to y
}
