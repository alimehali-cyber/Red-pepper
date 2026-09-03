package com.zig.gravity.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alijafari.red.astronomy.R
import com.zig.gravity.physics.BodyType
import com.zig.gravity.ui.theme.ZigGravityColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogSheet(
    isFull: Boolean,
    marbleBounce: Boolean = false,
    onToggleMarbleBounce: () -> Unit = {},
    onSelectType: (BodyType) -> Unit,
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
        modifier = modifier.testTag("catalog_bottom_sheet")
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
                    text = stringResource(R.string.zig_gravity_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("catalog_btn_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = ZigGravityColor.onSurfaceVariant
                    )
                }
            }

            if (isFull) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ZigGravityColor.accent.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.zig_gravity_table_full),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ZigGravityColor.accent,
                        modifier = Modifier
                            .padding(12.dp)
                            .testTag("catalog_full_notice_text")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val catalogItems = listOf(
                BodyType.SUN to R.string.zig_gravity_type_sun,
                BodyType.PLANET to R.string.zig_gravity_type_planet,
                BodyType.MOON to R.string.zig_gravity_type_moon,
                BodyType.ASTEROID to R.string.zig_gravity_type_asteroid,
                BodyType.TEST_MARBLE to R.string.zig_gravity_type_marble,
                BodyType.BLACK_HOLE to R.string.zig_gravity_type_black_hole,
                BodyType.WORMHOLE_MOUTH to R.string.zig_gravity_type_wormhole_pair
            )

            catalogItems.forEach { (type, nameRes) ->
                val disabled = isFull

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (disabled) Color.Transparent else ZigGravityColor.surfaceCenter.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !disabled) {
                            onSelectType(type)
                        }
                        .testTag("catalog_item_${type.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Marble / Black Hole / Wormhole preview
                        if (type == BodyType.BLACK_HOLE) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(ZigGravityColor.bodyBlackHoleDisk)
                                    .border(1.5.dp, ZigGravityColor.accent, CircleShape)
                            )
                        } else if (type == BodyType.WORMHOLE_MOUTH) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, ZigGravityColor.accent.copy(alpha = 0.85f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, ZigGravityColor.accent.copy(alpha = 0.5f), CircleShape)
                                )
                            }
                        } else {
                            val (baseColor, deepColor) = colorForType(type)
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(baseColor, deepColor)
                                        )
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(nameRes),
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (disabled) ZigGravityColor.onSurfaceVariant.copy(alpha = 0.5f) else ZigGravityColor.onSurface
                            )
                            if (type == BodyType.WORMHOLE_MOUTH) {
                                Text(
                                    text = stringResource(R.string.zig_gravity_wormhole_desc),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ZigGravityColor.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            }
                        }

                        if (!isFull) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = ZigGravityColor.accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = ZigGravityColor.onSurfaceVariant.copy(alpha = 0.2f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text(
                        text = stringResource(R.string.zig_gravity_marble_bounce_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = ZigGravityColor.onSurface
                    )
                    Text(
                        text = stringResource(R.string.zig_gravity_marble_bounce_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = ZigGravityColor.onSurfaceVariant
                    )
                }

                Switch(
                    checked = marbleBounce,
                    onCheckedChange = { onToggleMarbleBounce() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ZigGravityColor.accent,
                        checkedTrackColor = ZigGravityColor.accent.copy(alpha = 0.5f),
                        uncheckedThumbColor = ZigGravityColor.onSurfaceVariant,
                        uncheckedTrackColor = ZigGravityColor.surfaceEdge
                    ),
                    modifier = Modifier.testTag("catalog_switch_marble_bounce")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun colorForType(type: BodyType): Pair<Color, Color> = when (type) {
    BodyType.SUN -> Pair(ZigGravityColor.bodySunBase, ZigGravityColor.bodySunDeep)
    BodyType.PLANET -> Pair(ZigGravityColor.bodyEarthBase, ZigGravityColor.bodyEarthDeep)
    BodyType.MOON -> Pair(ZigGravityColor.bodyMoonBase, ZigGravityColor.bodyMoonDeep)
    BodyType.ASTEROID -> Pair(ZigGravityColor.bodyAsteroidBase, ZigGravityColor.bodyAsteroidDeep)
    BodyType.TEST_MARBLE -> Pair(ZigGravityColor.bodyMarbleBase, ZigGravityColor.bodyMarbleDeep)
    BodyType.BLACK_HOLE -> Pair(ZigGravityColor.bodyBlackHoleDisk, Color.Black)
    BodyType.WORMHOLE_MOUTH -> Pair(ZigGravityColor.accent, ZigGravityColor.surfaceEdge)
}
