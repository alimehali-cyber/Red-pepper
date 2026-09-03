package com.zig.gravity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChangeCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijafari.red.astronomy.R
import com.zig.gravity.physics.BodyType
import com.zig.gravity.physics.EngineConstants
import com.zig.gravity.physics.schwarzschildRadius
import com.zig.gravity.sim.BodyRender
import com.zig.gravity.sim.VisualScale
import com.zig.gravity.ui.theme.ZigGravityColor
import java.util.Locale
import kotlin.math.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectorSheet(
    body: BodyRender,
    metersPerDp: Double,
    teachingMode: Boolean,
    onSetMass: (id: Long, kg: Double) -> Unit,
    onSetRadius: (id: Long, meters: Double) -> Unit,
    onSetVelocity: (id: Long, vx: Double, vy: Double) -> Unit,
    onCircularize: (id: Long) -> Unit,
    onDuplicate: (id: Long) -> Unit,
    onRemove: (id: Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ZigGravityColor.glassContainer,
        scrimColor = Color.Black.copy(alpha = 0.5f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = ZigGravityColor.onSurfaceVariant.copy(alpha = 0.4f))
        },
        modifier = modifier.testTag("inspector_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
        ) {
            // Dormant micro-prompt strip (renders nothing if teachingMode == false)
            PredictionMicroPromptStrip(
                teachingMode = teachingMode,
                modifier = Modifier.fillMaxWidth()
            )

            // Header: Type Name + Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(nameResForType(body.type)),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onDuplicate(body.id) },
                        modifier = Modifier.size(36.dp).testTag("inspector_btn_duplicate")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.zig_gravity_duplicate),
                            tint = ZigGravityColor.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onRemove(body.id) },
                        modifier = Modifier.size(36.dp).testTag("inspector_btn_remove")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.zig_gravity_remove),
                            tint = Color(0xFFEF5350)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp).testTag("inspector_btn_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = ZigGravityColor.onSurfaceVariant
                        )
                    }
                }
            }

            Divider(color = ZigGravityColor.glassStroke, thickness = 1.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // 1. MASS SECTION (Log scale)
            val isWormhole = body.type == BodyType.WORMHOLE_MOUTH
            val isBlackHole = body.type == BodyType.BLACK_HOLE
            val isSun = body.type == BodyType.SUN
            val isSolarMass = isBlackHole || isSun
            val refMass = if (isSolarMass) EngineConstants.M_SUN else EngineConstants.M_EARTH
            val unitNameRes = if (isSolarMass) R.string.zig_gravity_mass_sun_unit else R.string.zig_gravity_mass_earth_unit
            val minMassRatio = when {
                isBlackHole -> 1.0
                isSun -> 0.1
                body.type == BodyType.PLANET -> 0.01
                body.type == BodyType.MOON -> 0.001
                else -> 1e-6
            }
            val maxMassRatio = when {
                isBlackHole -> 50.0
                isSun -> 10.0
                body.type == BodyType.PLANET -> 100.0
                body.type == BodyType.MOON -> 10.0
                else -> 1e-3
            }

            val currentRatio = (body.massKg / refMass).coerceIn(minMassRatio, maxMassRatio)
            val logMin = ln(minMassRatio)
            val logMax = ln(maxMassRatio)
            val logCurrent = ln(max(currentRatio, 1e-10))
            val sliderPos = ((logCurrent - logMin) / (logMax - logMin)).toFloat().coerceIn(0f, 1f)

            if (!isWormhole) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.zig_gravity_mass),
                            style = MaterialTheme.typography.titleMedium,
                            color = ZigGravityColor.onSurface
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.3f", currentRatio)} (${stringResource(unitNameRes)})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZigGravityColor.accent
                        )
                    }

                    Slider(
                        value = sliderPos,
                        onValueChange = { pos ->
                            val logVal = logMin + pos * (logMax - logMin)
                            val newRatio = exp(logVal)
                            val newKg = newRatio * refMass
                            onSetMass(body.id, newKg)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("inspector_slider_mass")
                    )

                    Text(
                        text = "${String.format(Locale.US, "%.2e", body.massKg)} kg",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZigGravityColor.onSurfaceVariant
                    )

                    if (isBlackHole) {
                        val rs = schwarzschildRadius(body.massKg)
                        Text(
                            text = stringResource(R.string.zig_gravity_event_horizon_real, formatSchwarzschildRadius(rs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZigGravityColor.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.zig_gravity_bh_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZigGravityColor.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. SIZE SECTION
            val minDp = VisualScale.minDp(body.type)
            val maxDp = VisualScale.maxDp(body.type)
            val currentDp = if (metersPerDp > 0.0) (body.radiusMeters / metersPerDp).toFloat() else VisualScale.defaultDp(body.type)
            val sizeSliderPos = ((currentDp - minDp) / (maxDp - minDp)).coerceIn(0f, 1f)

            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isWormhole) stringResource(R.string.zig_gravity_wormhole_mouth_radius) else stringResource(R.string.zig_gravity_size),
                        style = MaterialTheme.typography.titleMedium,
                        color = ZigGravityColor.onSurface
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.1f", currentDp)} dp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZigGravityColor.accent
                    )
                }

                Slider(
                    value = sizeSliderPos,
                    onValueChange = { pos ->
                        val newDp = minDp + pos * (maxDp - minDp)
                        val newMeters = if (metersPerDp > 0.0) newDp * metersPerDp else newDp * 1e9
                        onSetRadius(body.id, newMeters)
                    },
                    modifier = Modifier.fillMaxWidth().testTag("inspector_slider_size")
                )

                if (isBlackHole) {
                    Text(
                        text = stringResource(R.string.zig_gravity_ring_size_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = ZigGravityColor.onSurfaceVariant
                    )
                } else if (!isWormhole) {
                    val realRadiusRes = realRadiusResForType(body.type)
                    if (realRadiusRes != 0) {
                        Text(
                            text = stringResource(realRadiusRes),
                            style = MaterialTheme.typography.labelSmall,
                            color = ZigGravityColor.onSurfaceVariant
                        )
                    }
                }
            }

            if (isWormhole) {
                Spacer(modifier = Modifier.height(14.dp))
                if (body.partnerId != 0L) {
                    Text(
                        text = stringResource(R.string.zig_gravity_wormhole_partner_label, body.partnerId),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZigGravityColor.accent,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ZigGravityColor.surfaceCenter.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, ZigGravityColor.glassStroke),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.zig_gravity_wormhole_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZigGravityColor.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.zig_gravity_wormhole_remove_warning),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFEF5350).copy(alpha = 0.9f)
                        )
                    }
                }
            }

            if (!isWormhole) {
                Spacer(modifier = Modifier.height(14.dp))

                // 3. VELOCITY SECTION & DIRECTION DIAL
                val speedMps = sqrt(body.vx * body.vx + body.vy * body.vy)
                val speedKmS = speedMps / 1000.0
                val angleRad = atan2(body.vy, body.vx)

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.zig_gravity_velocity),
                            style = MaterialTheme.typography.titleMedium,
                            color = ZigGravityColor.onSurface
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.2f", speedKmS)} km/s",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ZigGravityColor.accent
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Direction Dial
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(ZigGravityColor.surfaceCenter)
                                .border(1.dp, ZigGravityColor.glassStroke, CircleShape)
                                .pointerInput(body.id, speedMps) {
                                    detectDragGestures { change, _ ->
                                        val dialCenterX = size.width / 2f
                                        val dialCenterY = size.height / 2f
                                        val touchX = change.position.x - dialCenterX
                                        val touchY = change.position.y - dialCenterY
                                        val newAngle = atan2(touchY.toDouble(), touchX.toDouble())
                                        val newVx = cos(newAngle) * speedMps
                                        val newVy = sin(newAngle) * speedMps
                                        onSetVelocity(body.id, newVx, newVy)
                                    }
                                }
                                .testTag("inspector_velocity_dial"),
                            contentAlignment = Alignment.Center
                        ) {
                            val accentColor = ZigGravityColor.accent
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val r = size.width / 2f - 8.dp.toPx()
                                val indicatorX = c.x + cos(angleRad).toFloat() * r
                                val indicatorY = c.y + sin(angleRad).toFloat() * r

                                drawLine(
                                    color = accentColor.copy(alpha = 0.5f),
                                    start = c,
                                    end = Offset(indicatorX, indicatorY),
                                    strokeWidth = 2.dp.toPx()
                                )
                                drawCircle(
                                    color = accentColor,
                                    radius = 4.dp.toPx(),
                                    center = Offset(indicatorX, indicatorY)
                                )
                            }
                        }

                        // Circularize Orbit Button
                        Button(
                            onClick = { onCircularize(body.id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ZigGravityColor.accent,
                                contentColor = ZigGravityColor.surfaceEdge
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("inspector_btn_circularize")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChangeCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.zig_gravity_circularize),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun PredictionMicroPromptStrip(
    teachingMode: Boolean,
    modifier: Modifier = Modifier
) {
    if (!teachingMode) return

    var activeCategory by remember { mutableStateOf(0) } // 0: Mass, 1: Velocity, 2: Size
    var selectedOption by remember(activeCategory) { mutableStateOf<Int?>(null) }

    Surface(
        modifier = modifier
            .padding(vertical = 8.dp)
            .testTag("prediction_micro_prompt_strip"),
        shape = RoundedCornerShape(14.dp),
        color = ZigGravityColor.surfaceCenter,
        border = BorderStroke(1.dp, ZigGravityColor.glassStroke)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            // Category selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.zig_gravity_edu_predict_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.accent
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = activeCategory == 0,
                        onClick = { activeCategory = 0 },
                        label = { Text(stringResource(R.string.zig_gravity_mass), fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = activeCategory == 1,
                        onClick = { activeCategory = 1 },
                        label = { Text(stringResource(R.string.zig_gravity_velocity), fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                    FilterChip(
                        selected = activeCategory == 2,
                        onClick = { activeCategory = 2 },
                        label = { Text(stringResource(R.string.zig_gravity_size), fontSize = 11.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (activeCategory) {
                0 -> {
                    // Mass
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { selectedOption = 0 },
                            label = { Text(stringResource(R.string.zig_gravity_pred_mass_opt_change), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedOption == 0) ZigGravityColor.accent.copy(alpha = 0.2f) else ZigGravityColor.surfaceEdge
                            ),
                            modifier = Modifier.weight(1f).testTag("prediction_chip_mass_1")
                        )
                        SuggestionChip(
                            onClick = { selectedOption = 1 },
                            label = { Text(stringResource(R.string.zig_gravity_pred_mass_opt_none), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedOption == 1) ZigGravityColor.accent.copy(alpha = 0.2f) else ZigGravityColor.surfaceEdge
                            ),
                            modifier = Modifier.weight(1f).testTag("prediction_chip_mass_2")
                        )
                    }
                    if (selectedOption != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.zig_gravity_pred_mass_reaction),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZigGravityColor.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("prediction_reaction_mass")
                        )
                    }
                }
                1 -> {
                    // Velocity
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SuggestionChip(
                            onClick = { selectedOption = 0 },
                            label = { Text(stringResource(R.string.zig_gravity_pred_vel_opt_expand), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedOption == 0) ZigGravityColor.accent.copy(alpha = 0.2f) else ZigGravityColor.surfaceEdge
                            ),
                            modifier = Modifier.weight(1f).testTag("prediction_chip_vel_1")
                        )
                        SuggestionChip(
                            onClick = { selectedOption = 1 },
                            label = { Text(stringResource(R.string.zig_gravity_pred_vel_opt_fall), fontSize = 11.sp) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (selectedOption == 1) ZigGravityColor.accent.copy(alpha = 0.2f) else ZigGravityColor.surfaceEdge
                            ),
                            modifier = Modifier.weight(1f).testTag("prediction_chip_vel_2")
                        )
                    }
                    if (selectedOption != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.zig_gravity_pred_vel_reaction),
                            style = MaterialTheme.typography.bodySmall,
                            color = ZigGravityColor.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("prediction_reaction_vel")
                        )
                    }
                }
                2 -> {
                    // Size
                    Text(
                        text = stringResource(R.string.zig_gravity_pred_size_reaction),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZigGravityColor.onSurfaceVariant,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("prediction_reaction_size")
                    )
                }
            }
        }
    }
}

private fun nameResForType(type: BodyType): Int = when (type) {
    BodyType.SUN -> R.string.zig_gravity_type_sun
    BodyType.PLANET -> R.string.zig_gravity_type_planet
    BodyType.MOON -> R.string.zig_gravity_type_moon
    BodyType.ASTEROID -> R.string.zig_gravity_type_asteroid
    BodyType.TEST_MARBLE -> R.string.zig_gravity_type_marble
    BodyType.BLACK_HOLE -> R.string.zig_gravity_type_black_hole
    BodyType.WORMHOLE_MOUTH -> R.string.zig_gravity_type_wormhole_mouth
}

private fun realRadiusResForType(type: BodyType): Int = when (type) {
    BodyType.SUN -> R.string.zig_gravity_real_radius_sun
    BodyType.PLANET -> R.string.zig_gravity_real_radius_earth
    BodyType.MOON -> R.string.zig_gravity_real_radius_moon
    else -> 0
}

private fun formatSchwarzschildRadius(rsMeters: Double): String {
    return if (rsMeters >= 1000.0) {
        val km = rsMeters / 1000.0
        val formatted = String.format(Locale.US, "%.1f", km)
        "≈ ${toPersianDigits(formatted.replace('.', '٫'))} کیلومتر"
    } else {
        val formatted = String.format(Locale.US, "%.1f", rsMeters)
        "≈ ${toPersianDigits(formatted.replace('.', '٫'))} متر"
    }
}

private fun toPersianDigits(str: String): String {
    return str.map { ch ->
        when (ch) {
            '0' -> '۰'
            '1' -> '۱'
            '2' -> '۲'
            '3' -> '۳'
            '4' -> '۴'
            '5' -> '۵'
            '6' -> '۶'
            '7' -> '۷'
            '8' -> '۸'
            '9' -> '۹'
            else -> ch
        }
    }.joinToString("")
}
