package com.zig.gravity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.zig.gravity.physics.BodyType
import com.zig.gravity.sim.SimSnapshot
import com.zig.gravity.sim.TrailStore
import kotlin.math.sin
import com.zig.gravity.ui.theme.ZigGravityColor
import com.zig.gravity.ui.theme.GravityPalette
import com.zig.gravity.ui.theme.LocalGravityPalette
import com.zig.gravity.ui.theme.ChromeMode
import com.zig.gravity.ui.theme.GradientType
import com.zig.gravity.ui.theme.LocalTableSurface
import com.zig.gravity.ui.theme.generateStarDots
import kotlin.math.hypot
import kotlin.math.max

@Composable
fun TabletopCanvas(
    snapshotState: State<SimSnapshot>,
    trails: TrailStore,
    density: Float,
    trailsEnabled: Boolean = true,
    lastPickedType: BodyType = BodyType.TEST_MARBLE,
    onViewportChanged: (widthPx: Int, density: Float) -> Unit,
    onSelectBody: (Long) -> Unit,
    onTapEmpty: () -> Unit,
    onLongPressBody: (Long) -> Unit,
    onLongPressEmpty: () -> Unit,
    onBeginDrag: (Long) -> Unit,
    onDragMoveTo: (id: Long, xMeters: Double, yMeters: Double) -> Unit,
    onEndDrag: () -> Unit,
    onUpdateLaunchPreview: (pressX: Double, pressY: Double, vx: Double, vy: Double, type: BodyType) -> Unit,
    onClearLaunchPreview: () -> Unit,
    onSpawnLaunchedBody: (type: BodyType, x: Double, y: Double, vx: Double, vy: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val palette = LocalGravityPalette.current
    val surface = LocalTableSurface.current

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            // Layer 1: Static background with drawWithCache
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val w = size.width
                        val h = size.height

                        // 1. Surface base gradient
                        val bgBrush = when (surface.gradientType) {
                            GradientType.RADIAL -> {
                                val center = Offset(w * surface.radialCenterNorm.first, h * surface.radialCenterNorm.second)
                                val radius = 0.95f * max(w, h)
                                Brush.radialGradient(
                                    colors = surface.gradientColors,
                                    center = center,
                                    radius = radius
                                )
                            }
                            GradientType.LINEAR_VERTICAL -> {
                                Brush.verticalGradient(
                                    colors = surface.gradientColors,
                                    startY = 0f,
                                    endY = h
                                )
                            }
                        }

                        // 2. Corner vignette
                        val vignetteBrush = if (surface.vignetteStrength > 0f) {
                            val vignetteCenter = Offset(w * 0.50f, h * 0.50f)
                            val diagonal = hypot(w.toDouble(), h.toDouble()).toFloat()
                            val vignetteRadius = 0.75f * diagonal
                            Brush.radialGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    surface.vignetteColor.copy(alpha = surface.vignetteStrength)
                                ),
                                center = vignetteCenter,
                                radius = vignetteRadius
                            )
                        } else null

                        // 3. Top sheen
                        val sheenBrush = if (surface.sheenStrength > 0f) {
                            Brush.verticalGradient(
                                0.0f to Color.White.copy(alpha = surface.sheenStrength),
                                0.30f to Color.Transparent,
                                startY = 0f,
                                endY = h
                            )
                        } else null

                        // 4. Star pattern dots (generated once per size/spec, seed fixed)
                        val starDots = if (surface.pattern != null) {
                            generateStarDots(surface.pattern)
                        } else emptyList()

                        onDrawBehind {
                            drawRect(brush = bgBrush)
                            if (vignetteBrush != null) {
                                drawRect(brush = vignetteBrush)
                            }
                            if (sheenBrush != null) {
                                drawRect(brush = sheenBrush)
                            }
                            if (starDots.isNotEmpty()) {
                                val starColor = Color.White
                                for (i in starDots.indices) {
                                    val dot = starDots[i]
                                    drawCircle(
                                        color = starColor.copy(alpha = dot.alpha),
                                        radius = dot.radiusDp * density,
                                        center = Offset(dot.normX * w, dot.normY * h)
                                    )
                                }
                            }
                        }
                    },
                onDraw = {}
            )

            // Preallocated Path pool for trails (2 paths per body for up to 20 bodies = 40 Paths)
            val olderPaths = remember { Array(20) { Path() } }
            val newerPaths = remember { Array(20) { Path() } }
            val trajectoryPath = remember { Path() }

            // Layer 2: Dynamic simulation and interactive gesture layer
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { size ->
                        if (size.width > 0 && size.height > 0) {
                            onViewportChanged(size.width, density)
                        }
                    }
                    .pointerInput(density) {
                        detectTapGestures(
                            onTap = { offset ->
                                val snap = snapshotState.value
                                if (snap.metersPerDp <= 0.0) return@detectTapGestures
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val scale = (density / snap.metersPerDp).toFloat()
                                val minHitRadius = 24.dp.toPx()

                                var hitId = -1L
                                val bodies = snap.bodies
                                for (i in bodies.indices.reversed()) {
                                    val b = bodies[i]
                                    val bx = cx + (b.x * scale).toFloat()
                                    val by = cy + (b.y * scale).toFloat()
                                    val r = (b.radiusMeters * scale).toFloat()
                                    val threshold = max(r, minHitRadius)
                                    val dx = offset.x - bx
                                    val dy = offset.y - by
                                    if (dx * dx + dy * dy <= threshold * threshold) {
                                        hitId = b.id
                                        break
                                    }
                                }

                                if (hitId != -1L) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectBody(hitId)
                                } else {
                                    onTapEmpty()
                                }
                            },
                            onLongPress = { offset ->
                                val snap = snapshotState.value
                                if (snap.metersPerDp <= 0.0) return@detectTapGestures
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val scale = (density / snap.metersPerDp).toFloat()
                                val minHitRadius = 24.dp.toPx()

                                var hitId = -1L
                                val bodies = snap.bodies
                                for (i in bodies.indices.reversed()) {
                                    val b = bodies[i]
                                    val bx = cx + (b.x * scale).toFloat()
                                    val by = cy + (b.y * scale).toFloat()
                                    val r = (b.radiusMeters * scale).toFloat()
                                    val threshold = max(r, minHitRadius)
                                    val dx = offset.x - bx
                                    val dy = offset.y - by
                                    if (dx * dx + dy * dy <= threshold * threshold) {
                                        hitId = b.id
                                        break
                                    }
                                }

                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (hitId != -1L) {
                                    onLongPressBody(hitId)
                                } else {
                                    onLongPressEmpty()
                                }
                            }
                        )
                    }
                    .pointerInput(density, lastPickedType) {
                        var activeDragId = -1L
                        var isSlingshot = false
                        var pressXScene = 0.0
                        var pressYScene = 0.0
                        var currentDragOffset = Offset.Zero

                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val snap = snapshotState.value
                                if (snap.metersPerDp <= 0.0) return@detectDragGestures
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val scale = (density / snap.metersPerDp).toFloat()
                                val minHitRadius = 24.dp.toPx()

                                var hitId = -1L
                                val bodies = snap.bodies
                                for (i in bodies.indices.reversed()) {
                                    val b = bodies[i]
                                    val bx = cx + (b.x * scale).toFloat()
                                    val by = cy + (b.y * scale).toFloat()
                                    val r = (b.radiusMeters * scale).toFloat()
                                    val threshold = max(r, minHitRadius)
                                    val dx = startOffset.x - bx
                                    val dy = startOffset.y - by
                                    if (dx * dx + dy * dy <= threshold * threshold) {
                                        hitId = b.id
                                        break
                                    }
                                }

                                if (hitId != -1L) {
                                    activeDragId = hitId
                                    isSlingshot = false
                                    onBeginDrag(hitId)
                                } else {
                                    activeDragId = -1L
                                    isSlingshot = true
                                    pressXScene = (startOffset.x - cx) / scale.toDouble()
                                    pressYScene = (startOffset.y - cy) / scale.toDouble()
                                    currentDragOffset = Offset.Zero
                                    onUpdateLaunchPreview(pressXScene, pressYScene, 0.0, 0.0, lastPickedType)
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val snap = snapshotState.value
                                if (snap.metersPerDp <= 0.0) return@detectDragGestures
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val scale = (density / snap.metersPerDp).toFloat()

                                if (activeDragId != -1L && !isSlingshot) {
                                    val xMeters = (change.position.x - cx) / scale.toDouble()
                                    val yMeters = (change.position.y - cy) / scale.toDouble()
                                    onDragMoveTo(activeDragId, xMeters, yMeters)
                                } else if (isSlingshot) {
                                    currentDragOffset += dragAmount
                                    // v = -K * dragVector. K tuned so half-screen drag (≈150dp) ≈ 75 km/s
                                    val dragVectorDpX = currentDragOffset.x / density
                                    val dragVectorDpY = currentDragOffset.y / density
                                    val kVelocity = 500.0 // 150 dp drag -> 75,000 m/s = 75 km/s
                                    val vx = -kVelocity * dragVectorDpX
                                    val vy = -kVelocity * dragVectorDpY
                                    onUpdateLaunchPreview(pressXScene, pressYScene, vx, vy, lastPickedType)
                                }
                            },
                            onDragEnd = {
                                if (activeDragId != -1L && !isSlingshot) {
                                    onEndDrag()
                                    activeDragId = -1L
                                } else if (isSlingshot) {
                                    val dragVectorDpX = currentDragOffset.x / density
                                    val dragVectorDpY = currentDragOffset.y / density
                                    val kVelocity = 500.0
                                    val vx = -kVelocity * dragVectorDpX
                                    val vy = -kVelocity * dragVectorDpY
                                    onSpawnLaunchedBody(lastPickedType, pressXScene, pressYScene, vx, vy)
                                    isSlingshot = false
                                }
                            },
                            onDragCancel = {
                                if (activeDragId != -1L && !isSlingshot) {
                                    onEndDrag()
                                    activeDragId = -1L
                                } else if (isSlingshot) {
                                    onClearLaunchPreview()
                                    isSlingshot = false
                                }
                            }
                        )
                    }
            ) {
                val snap = snapshotState.value // READ STATE ONLY HERE (draw phase)
                if (snap.metersPerDp <= 0.0) return@Canvas

                val cx = size.width / 2f
                val cy = size.height / 2f
                val metersPerDp = snap.metersPerDp
                val scale = (density / metersPerDp).toFloat()
                val strokeWidth = 1.5f * density
                val selectionStrokeWidth = 2.0f * density
                val selectionOffsetRadius = 2.0f * density

                val bodies = snap.bodies
                val bodyCount = bodies.size

                // 1) TRAILS (if enabled)
                if (trailsEnabled) {
                    for (bIndex in 0 until bodyCount) {
                        if (bIndex >= 20) break
                        val body = bodies[bIndex]
                        val trailSize = trails.sizeOf(body.id)
                        if (trailSize >= 2) {
                            val olderPath = olderPaths[bIndex]
                            val newerPath = newerPaths[bIndex]
                            olderPath.reset()
                            newerPath.reset()

                            val half = trailSize / 2
                            val baseColor = colorForType(body.type, palette).first

                            var olderStarted = false
                            var newerStarted = false

                            trails.forEach(body.id) { i, xMeters, yMeters ->
                                if (xMeters.isNaN() || yMeters.isNaN()) {
                                    if (i <= half) olderStarted = false
                                    if (i >= half) newerStarted = false
                                } else {
                                    val px = cx + (xMeters * scale)
                                    val py = cy + (yMeters * scale)
                                    if (i <= half) {
                                        if (!olderStarted) {
                                            olderPath.moveTo(px, py)
                                            olderStarted = true
                                        } else {
                                            olderPath.lineTo(px, py)
                                        }
                                    }
                                    if (i >= half) {
                                        if (!newerStarted) {
                                            newerPath.moveTo(px, py)
                                            newerStarted = true
                                        } else {
                                            newerPath.lineTo(px, py)
                                        }
                                    }
                                }
                            }

                            if (olderStarted) {
                                drawPath(
                                    path = olderPath,
                                    color = baseColor.copy(alpha = surface.trailAlphaPair.first),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            if (newerStarted) {
                                drawPath(
                                    path = newerPath,
                                    color = baseColor.copy(alpha = surface.trailAlphaPair.second),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                        }
                    }
                }

                // 2) LAUNCH PREVIEW (if active)
                val preview = snap.preview
                if (preview != null) {
                    val gx = cx + (preview.ghostX * scale).toFloat()
                    val gy = cy + (preview.ghostY * scale).toFloat()
                    val gr = preview.ghostRadiusDp * density

                    // Ghost body (60% alpha)
                    val (baseColor, deepColor) = colorForType(preview.type, palette)
                    val ghostBrush = Brush.radialGradient(
                        colors = listOf(baseColor.copy(alpha = 0.6f), deepColor.copy(alpha = 0.6f)),
                        center = Offset(gx - 0.25f * gr, gy - 0.25f * gr),
                        radius = gr
                    )
                    drawCircle(
                        brush = ghostBrush,
                        radius = gr,
                        center = Offset(gx, gy)
                    )

                    // Dotted trajectory line (2 dp dash, accent color, alpha 0.7)
                    if (preview.pathLength >= 2) {
                        trajectoryPath.reset()
                        val p0x = cx + (preview.pathXs[0] * scale)
                        val p0y = cy + (preview.pathYs[0] * scale)
                        trajectoryPath.moveTo(p0x, p0y)
                        for (pi in 1 until preview.pathLength) {
                            val px = cx + (preview.pathXs[pi] * scale)
                            val py = cy + (preview.pathYs[pi] * scale)
                            trajectoryPath.lineTo(px, py)
                        }
                        val dashEffect = PathEffect.dashPathEffect(
                            floatArrayOf(2.0f * density, 3.0f * density),
                            0f
                        )
                        drawPath(
                            path = trajectoryPath,
                            color = palette.accent.copy(alpha = 0.7f),
                            style = Stroke(width = 1.5f * density, pathEffect = dashEffect)
                        )
                    }

                    // Approximate badge if massive candidate
                    if (preview.approximate) {
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb(220, 246, 178, 107) // accent gold/amber
                                textSize = 11.0f * density
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText("«پیش‌نمایش تقریبی»", gx, gy - gr - (6.0f * density), paint)
                        }
                    }
                }

                // 3) SHADOWS: realistic two-layer soft cast (penumbra then umbra, offset to bottom-right)
                val isDarkSurface = surface.chromeMode == ChromeMode.DARK
                val umbraAlpha = if (isDarkSurface) 0.32f else 0.26f
                val penumbraAlpha = if (isDarkSurface) 0.20f else 0.16f

                for (bIndex in 0 until bodyCount) {
                    val body = bodies[bIndex]
                    if (body.type == BodyType.BLACK_HOLE || body.type == BodyType.WORMHOLE_MOUTH) continue // No contact shadow for aperture bodies
                    val bx = cx + (body.x * scale).toFloat()
                    val by = cy + (body.y * scale).toFloat()
                    val r = (body.radiusMeters * scale).toFloat()
                    if (r <= 0f) continue

                    // Penumbra: radial gradient, oval ~1.45r x 1.15r, offset (+0.38r, +0.34r)
                    val penumbraCenterX = bx + 0.38f * r
                    val penumbraCenterY = by + 0.34f * r
                    val penumbraRx = 1.45f * r
                    val penumbraRy = 1.15f * r
                    val penumbraBrush = Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = penumbraAlpha), Color.Transparent),
                        center = Offset(penumbraCenterX, penumbraCenterY),
                        radius = max(penumbraRx, penumbraRy)
                    )
                    drawOval(
                        brush = penumbraBrush,
                        topLeft = Offset(penumbraCenterX - penumbraRx, penumbraCenterY - penumbraRy),
                        size = Size(penumbraRx * 2f, penumbraRy * 2f)
                    )

                    // Umbra: radial gradient, oval ~0.8r x 0.8r, offset (+0.20r, +0.18r)
                    val umbraCenterX = bx + 0.20f * r
                    val umbraCenterY = by + 0.18f * r
                    val umbraRx = 0.80f * r
                    val umbraRy = 0.80f * r
                    val umbraBrush = Brush.radialGradient(
                        colors = listOf(Color.Black.copy(alpha = umbraAlpha), Color.Transparent),
                        center = Offset(umbraCenterX, umbraCenterY),
                        radius = umbraRx
                    )
                    drawOval(
                        brush = umbraBrush,
                        topLeft = Offset(umbraCenterX - umbraRx, umbraCenterY - umbraRy),
                        size = Size(umbraRx * 2f, umbraRy * 2f)
                    )
                }

                // 4) BODIES (marbles / black hole / wormhole mouths)
                val currentNanos = System.nanoTime()
                val tSec = currentNanos / 1_000_000_000.0
                val shimmerAlpha = (0.85 + 0.10 * sin(2.0 * Math.PI * tSec / 6.0)).toFloat().coerceIn(0f, 1f)
                val wormholePulseAlpha = (0.85 + 0.15 * sin(2.0 * Math.PI * tSec / 3.0)).toFloat().coerceIn(0.70f, 1.0f)

                for (bIndex in 0 until bodyCount) {
                    val body = bodies[bIndex]
                    val bx = cx + (body.x * scale).toFloat()
                    val by = cy + (body.y * scale).toFloat()
                    val r = (body.radiusMeters * scale).toFloat()
                    if (r <= 0f) continue

                    if (body.type == BodyType.WORMHOLE_MOUTH) {
                        // Wormhole mouth: transparent aperture, outer ring, inner ring, synchronized pulse, micro-badge
                        // 1. Outer ring: stroke 1.5 dp, color accent.copy(alpha = 0.85f * pulse)
                        drawCircle(
                            color = palette.accent.copy(alpha = (0.85f * wormholePulseAlpha).coerceIn(0f, 1f)),
                            radius = r,
                            center = Offset(bx, by),
                            style = Stroke(width = strokeWidth)
                        )

                        // 2. Inner ring: stroke 1 dp, color accent.copy(alpha = 0.50f * pulse), radius = 0.65 · r
                        drawCircle(
                            color = palette.accent.copy(alpha = (0.50f * wormholePulseAlpha).coerceIn(0f, 1f)),
                            radius = 0.65f * r,
                            center = Offset(bx, by),
                            style = Stroke(width = 1.0f * density)
                        )

                        // 3. Micro-badge below mouth: «کرمچاله (فرضی)» at 9 sp, color textMuted
                        drawContext.canvas.nativeCanvas.apply {
                            val paint = android.graphics.Paint().apply {
                                color = android.graphics.Color.argb((255 * 0.65f).toInt(), (palette.onSurfaceVariant.red * 255).toInt(), (palette.onSurfaceVariant.green * 255).toInt(), (palette.onSurfaceVariant.blue * 255).toInt())
                                textSize = 9.0f * density
                                isAntiAlias = true
                                textAlign = android.graphics.Paint.Align.CENTER
                            }
                            drawText("«کرمچاله (فرضی)»", bx, by + r + (11.0f * density), paint)
                        }
                    } else if (body.type == BodyType.BLACK_HOLE) {
                        // 1. Faint halo — radial gradient, accent color, alpha 0.10 center → 0 at radius 1.5r
                        val haloRadius = 1.5f * r
                        val haloBrush = Brush.radialGradient(
                            colors = listOf(palette.accent.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(bx, by),
                            radius = haloRadius
                        )
                        drawCircle(
                            brush = haloBrush,
                            radius = haloRadius,
                            center = Offset(bx, by)
                        )

                        // 2. Matte disk — solid bodyBlackHoleDisk (#0A0A0C or #26262B in light), radius 0.55r
                        val diskRadius = 0.55f * r
                        val diskBrush = Brush.radialGradient(
                            colors = listOf(palette.bodyBlackHoleDisk, palette.bodyBlackHoleDisk.copy(alpha = 0.95f)),
                            center = Offset(bx, by),
                            radius = diskRadius
                        )
                        drawCircle(
                            brush = diskBrush,
                            radius = diskRadius,
                            center = Offset(bx, by)
                        )

                        // 3 & 4. Accent ring + Slow shimmer — stroke 1.5 dp, accent color, radius r, modulated alpha
                        drawCircle(
                            color = palette.accent.copy(alpha = shimmerAlpha),
                            radius = r,
                            center = Offset(bx, by),
                            style = Stroke(width = strokeWidth)
                        )
                    } else {
                        val (baseColor, deepColor) = colorForType(body.type, palette)
                        val marbleCenter = Offset(bx - 0.25f * r, by - 0.25f * r)
                        val marbleBrush = Brush.radialGradient(
                            colors = listOf(baseColor, deepColor),
                            center = marbleCenter,
                            radius = r
                        )
                        drawCircle(
                            brush = marbleBrush,
                            radius = r,
                            center = Offset(bx, by)
                        )

                        // Specular highlight: white at alpha 0.35f, radius 0.22r, center (bx - 0.35r, by - 0.35r)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.35f),
                            radius = 0.22f * r,
                            center = Offset(bx - 0.35f * r, by - 0.35f * r)
                        )
                    }

                    // 5) SELECTION RING: if body.id == snap.selectedId → drawCircle(accent, style Stroke(2dp), radius r + 2dp)
                    if (body.id == snap.selectedId) {
                        drawCircle(
                            color = palette.accent,
                            radius = r + selectionOffsetRadius,
                            center = Offset(bx, by),
                            style = Stroke(width = selectionStrokeWidth)
                        )
                    }
                }

                // 6) MERGE PULSES (drawn above bodies, 1.2→1.8x r', expanding over 400ms, alpha 0.5→0, stroke 2dp)
                val pulseStrokeWidth = 2.0f * density
                for (pulse in snap.pulses) {
                    val remainingNanos = pulse.expiryNanos - currentNanos
                    val progress = ((400_000_000L - remainingNanos) / 400_000_000f).coerceIn(0f, 1f)
                    val rMeters = pulse.startRadiusMeters * (1.2f + 0.6f * progress)
                    val rPx = (rMeters * scale).toFloat()
                    val alpha = (0.5f * (1f - progress)).coerceIn(0f, 0.5f)
                    val px = cx + (pulse.xMeters * scale).toFloat()
                    val py = cy + (pulse.yMeters * scale).toFloat()

                    drawCircle(
                        color = palette.accent.copy(alpha = alpha),
                        radius = rPx,
                        center = Offset(px, py),
                        style = Stroke(width = pulseStrokeWidth)
                    )
                }
            }
        }
    }
}

private fun colorForType(type: BodyType, palette: GravityPalette): Pair<Color, Color> = when (type) {
    BodyType.SUN -> Pair(palette.bodySunBase, palette.bodySunDeep)
    BodyType.PLANET -> Pair(palette.bodyEarthBase, palette.bodyEarthDeep)
    BodyType.MOON -> Pair(palette.bodyMoonBase, palette.bodyMoonDeep)
    BodyType.ASTEROID -> Pair(palette.bodyAsteroidBase, palette.bodyAsteroidDeep)
    BodyType.TEST_MARBLE -> Pair(palette.bodyMarbleBase, palette.bodyMarbleDeep)
    BodyType.BLACK_HOLE -> Pair(palette.bodyBlackHoleDisk, Color.Black)
    BodyType.WORMHOLE_MOUTH -> Pair(palette.accent, palette.surfaceEdge)
}
