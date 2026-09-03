package com.zig.gravity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alijafari.red.astronomy.R
import com.zig.gravity.sim.UiState
import com.zig.gravity.ui.theme.ZigGravityColor

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HudBar(
    ui: UiState,
    onTogglePlayPause: () -> Unit,
    onSetSpeed: (Double) -> Unit,
    onReset: () -> Unit,
    onLongReset: () -> Unit = {},
    onToggleTrails: () -> Unit,
    onToggleTeaching: () -> Unit = {},
    onToggleTheme: () -> Unit = {},
    onToggleLanguage: () -> Unit = {},
    onTogglePerfOverlay: () -> Unit = {},
    onOpenChallenges: () -> Unit = {},
    onOpenCatalog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var speedMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("gravity_hud_bar"),
        shape = RoundedCornerShape(18.dp),
        color = ZigGravityColor.glassContainer,
        border = BorderStroke(1.dp, ZigGravityColor.glassStroke),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Play / Pause
            IconButton(
                onClick = onTogglePlayPause,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("hud_btn_play_pause")
            ) {
                Icon(
                    imageVector = if (ui.running) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = stringResource(R.string.zig_gravity_cd_play_pause),
                    tint = if (ui.running) ZigGravityColor.onSurface else ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 2. Speed
            Box {
                IconButton(
                    onClick = { speedMenuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("hud_btn_speed")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = stringResource(R.string.zig_gravity_cd_speed),
                        tint = if (ui.speed != 1.0) ZigGravityColor.accent else ZigGravityColor.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false },
                    modifier = Modifier.background(ZigGravityColor.surfaceCenter)
                ) {
                    val speeds = listOf(
                        0.1 to R.string.zig_gravity_speed_0_1,
                        0.25 to R.string.zig_gravity_speed_0_25,
                        1.0 to R.string.zig_gravity_speed_1,
                        4.0 to R.string.zig_gravity_speed_4,
                        16.0 to R.string.zig_gravity_speed_16
                    )
                    speeds.forEach { (speedVal, labelRes) ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(labelRes),
                                        color = if (ui.speed == speedVal) ZigGravityColor.accent else ZigGravityColor.onSurface
                                    )
                                    if (ui.speed == speedVal) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = ZigGravityColor.accent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSetSpeed(speedVal)
                                speedMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // 3. Reset (Tap: reset preset, Long press: open preset picker)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onReset,
                        onLongClick = onLongReset
                    )
                    .testTag("hud_btn_reset"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = stringResource(R.string.zig_gravity_cd_reset),
                    tint = ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 4. Trails
            IconButton(
                onClick = onToggleTrails,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("hud_btn_trails")
            ) {
                Icon(
                    imageVector = Icons.Default.Timeline,
                    contentDescription = stringResource(R.string.zig_gravity_cd_trails),
                    tint = if (ui.trailsEnabled) ZigGravityColor.accent else ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 5. Teaching (Live in Phase 9)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onToggleTeaching,
                        onLongClick = onOpenChallenges
                    )
                    .testTag("hud_btn_teaching"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.School,
                    contentDescription = stringResource(R.string.zig_gravity_cd_teaching),
                    tint = if (ui.teachingMode) ZigGravityColor.accent else ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 6. Theme (click = toggle theme, long-click = toggle perf overlay)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .combinedClickable(
                        onClick = onToggleTheme,
                        onLongClick = onTogglePerfOverlay
                    )
                    .testTag("hud_btn_theme"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (ui.darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                    contentDescription = stringResource(R.string.zig_gravity_cd_theme),
                    tint = ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // 7. Language
            IconButton(
                onClick = onToggleLanguage,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("hud_btn_language")
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = stringResource(R.string.zig_gravity_cd_language),
                    tint = if (ui.language == "en") ZigGravityColor.accent else ZigGravityColor.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            // Gap
            Spacer(modifier = Modifier.weight(1f))

            // 8. Add "+" button (Wired to open catalog in Phase 4)
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onOpenCatalog,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ZigGravityColor.accent)
                        .testTag("hud_btn_add")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.zig_gravity_cd_add),
                        tint = ZigGravityColor.surfaceEdge,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
