package com.artishorizon.tidefit.core.media

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artishorizon.tidefit.core.designsystem.Spacing
import com.artishorizon.tidefit.core.designsystem.components.PrimaryButton
import com.artishorizon.tidefit.core.designsystem.components.SecondaryButton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Shows the crop screen whenever [vm] has a picked image waiting. Put this once on any screen that offers photo picking. */
@Composable
fun AvatarCropHost(vm: AvatarViewModel) {
    val pending by vm.pending.collectAsState()
    pending?.let { bitmap ->
        AvatarCropDialog(bitmap, onCancel = vm::cancelCrop, onConfirm = vm::confirmCrop)
    }
}

/** Full-screen "move and zoom" crop with a circular window. Reports the chosen square in bitmap pixels. */
@Composable
private fun AvatarCropDialog(bitmap: Bitmap, onCancel: () -> Unit, onConfirm: (left: Int, top: Int, side: Int) -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
            Text(
                "Move and zoom", style = MaterialTheme.typography.titleLarge, color = Color.White,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.lg)
            )
            var viewSize by remember { mutableStateOf(IntSize.Zero) }
            // Zoom is "extra" on top of the minimum that makes the picture cover the circle; offset is the pan in view pixels.
            var zoom by remember { mutableFloatStateOf(1f) }
            var pan by remember { mutableStateOf(Offset.Zero) }

            val image = remember(bitmap) { bitmap.asImageBitmap() }
            val diameter = min(viewSize.width, viewSize.height) * 0.86f
            val minScale = if (diameter > 0f) diameter / min(bitmap.width, bitmap.height) else 1f
            val scale = minScale * zoom

            fun clamp(p: Offset, s: Float): Offset {
                val maxX = max(0f, bitmap.width * s / 2f - diameter / 2f)
                val maxY = max(0f, bitmap.height * s / 2f - diameter / 2f)
                return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
            }

            Box(
                Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(bitmap) {
                        detectTransformGestures { _, gesture, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 6f)
                            pan = clamp(pan + gesture, minScale * zoom)
                        }
                    }
                    .onSizeChanged { viewSize = it }
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f + pan.x, size.height / 2f + pan.y)
                    val w = bitmap.width * scale
                    val h = bitmap.height * scale
                    drawImage(
                        image,
                        dstOffset = IntOffset((center.x - w / 2f).roundToInt(), (center.y - h / 2f).roundToInt()),
                        dstSize = IntSize(w.roundToInt(), h.roundToInt())
                    )
                    // Dim everything outside the circle, then outline it.
                    val hole = Path().apply { addOval(Rect(Offset(size.width / 2f, size.height / 2f), diameter / 2f)) }
                    clipPath(hole, clipOp = ClipOp.Difference) { drawRect(Color.Black.copy(alpha = 0.62f)) }
                    drawCircle(Color.White.copy(alpha = 0.9f), radius = diameter / 2f, center = Offset(size.width / 2f, size.height / 2f), style = Stroke(2.dp.toPx()))
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(Spacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
                PrimaryButton("Use photo", {
                    // Circle centre is the view centre; invert the draw transform to find that square in the bitmap.
                    val s = scale
                    val side = (diameter / s)
                    val cx = bitmap.width / 2f - pan.x / s
                    val cy = bitmap.height / 2f - pan.y / s
                    onConfirm((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt(), side.roundToInt())
                }, Modifier.weight(1f))
            }
        }
    }
}

