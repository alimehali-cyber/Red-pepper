package com.zig.gravity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alijafari.red.astronomy.R
import com.zig.gravity.physics.BodyType
import com.zig.gravity.sim.PresetDef
import com.zig.gravity.sim.Presets
import com.zig.gravity.ui.theme.ZigGravityColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetPickerSheet(
    currentPresetKey: String?,
    onSelectPreset: (PresetDef) -> Unit,
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
        modifier = modifier.testTag("preset_picker_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.zig_gravity_presets_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("preset_picker_btn_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = ZigGravityColor.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val presets = Presets.all
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val isSelected = preset.key == currentPresetKey
                    PresetRow(
                        preset = preset,
                        isSelected = isSelected,
                        onClick = {
                            onSelectPreset(preset)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PresetRow(
    preset: PresetDef,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val nameRes = when (preset.key) {
        "sun_earth" -> R.string.zig_gravity_preset_sun_earth
        "sun_earth_moon" -> R.string.zig_gravity_preset_sun_earth_moon
        "binary_stars" -> R.string.zig_gravity_preset_binary_stars
        "figure_eight" -> R.string.zig_gravity_preset_figure_eight
        "collision_course" -> R.string.zig_gravity_preset_collision_course
        "marble_shower" -> R.string.zig_gravity_preset_marble_shower
        "stress_20" -> R.string.zig_gravity_preset_stress_20
        else -> R.string.zig_gravity_preset_sun_earth
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("preset_row_${preset.key}"),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) ZigGravityColor.accent.copy(alpha = 0.12f) else ZigGravityColor.surfaceCenter,
        border = if (isSelected) BorderStroke(1.dp, ZigGravityColor.accent) else BorderStroke(1.dp, ZigGravityColor.glassStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Tiny marble-dot preview (dots representing the bodies)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(44.dp)
                ) {
                    val previewBodies = preset.bodies.take(4)
                    previewBodies.forEach { body ->
                        val dotColor = when (body.type) {
                            BodyType.SUN -> ZigGravityColor.bodySunBase
                            BodyType.PLANET -> ZigGravityColor.bodyEarthBase
                            BodyType.MOON -> ZigGravityColor.bodyMoonBase
                            BodyType.ASTEROID -> ZigGravityColor.bodyAsteroidBase
                            BodyType.TEST_MARBLE -> ZigGravityColor.bodyMarbleBase
                            BodyType.BLACK_HOLE -> Color.Black
                            BodyType.WORMHOLE_MOUTH -> Color(0xFF00E5FF)
                        }
                        Box(
                            modifier = Modifier
                                .size(if (body.type == BodyType.SUN) 9.dp else 6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                    if (preset.bodies.size > 4) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.labelSmall,
                            color = ZigGravityColor.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = stringResource(nameRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) ZigGravityColor.accent else ZigGravityColor.onSurface
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ZigGravityColor.accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
