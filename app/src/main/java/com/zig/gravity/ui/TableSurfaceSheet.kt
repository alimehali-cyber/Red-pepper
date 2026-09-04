package com.zig.gravity.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alijafari.red.astronomy.R
import com.zig.gravity.ui.theme.GradientType
import com.zig.gravity.ui.theme.GravitySurface
import com.zig.gravity.ui.theme.TableSurfaces
import com.zig.gravity.ui.theme.ZigGravityColor
import com.zig.gravity.ui.theme.generateStarDots
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableSurfaceSheet(
    currentSurfaceKey: String,
    onSelectSurface: (String) -> Unit,
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
        modifier = modifier.testTag("table_surface_bottom_sheet")
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
                    text = stringResource(R.string.zig_gravity_table_colour_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.onSurface
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("table_surface_btn_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = ZigGravityColor.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(TableSurfaces.allSurfaces, key = { it.key }) { surface ->
                    val isSelected = surface.key == currentSurfaceKey
                    SurfaceSwatchItem(
                        surface = surface,
                        isSelected = isSelected,
                        onClick = { onSelectSurface(surface.key) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SurfaceSwatchItem(
    surface: GravitySurface,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("table_surface_item_${surface.key}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) ZigGravityColor.accent else ZigGravityColor.glassStroke,
                    shape = RoundedCornerShape(16.dp)
                )
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val bgBrush = when (surface.gradientType) {
                        GradientType.RADIAL -> Brush.radialGradient(
                            colors = surface.gradientColors,
                            center = Offset(w * surface.radialCenterNorm.first, h * surface.radialCenterNorm.second),
                            radius = 0.95f * max(w, h)
                        )
                        GradientType.LINEAR_VERTICAL -> Brush.verticalGradient(
                            colors = surface.gradientColors,
                            startY = 0f,
                            endY = h
                        )
                    }
                    val dots = if (surface.pattern != null) {
                        generateStarDots(surface.pattern).take(8)
                    } else emptyList()

                    onDrawBehind {
                        drawRect(brush = bgBrush)
                        if (dots.isNotEmpty()) {
                            for (dot in dots) {
                                drawCircle(
                                    color = Color.White.copy(alpha = dot.alpha),
                                    radius = 1.2f,
                                    center = Offset(dot.normX * w, dot.normY * h)
                                )
                            }
                        }
                    }
                }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(surface.titleRes),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) ZigGravityColor.accent else ZigGravityColor.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
