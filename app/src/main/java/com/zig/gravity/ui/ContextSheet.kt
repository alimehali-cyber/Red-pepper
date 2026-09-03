package com.zig.gravity.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alijafari.red.astronomy.R
import com.zig.gravity.ui.theme.ZigGravityColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextSheet(
    bodyId: Long,
    onInspect: () -> Unit,
    onDuplicate: () -> Unit,
    onRemove: () -> Unit,
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
        modifier = modifier.testTag("context_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // 1. Inspect
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ZigGravityColor.surfaceCenter.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onInspect() }
                    .testTag("context_row_inspect")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = ZigGravityColor.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.zig_gravity_inspect),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZigGravityColor.onSurface
                    )
                }
            }

            // 2. Duplicate
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ZigGravityColor.surfaceCenter.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDuplicate() }
                    .testTag("context_row_duplicate")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        tint = ZigGravityColor.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.zig_gravity_duplicate),
                        style = MaterialTheme.typography.bodyLarge,
                        color = ZigGravityColor.onSurface
                    )
                }
            }

            // 3. Remove
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = ZigGravityColor.surfaceCenter.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onRemove() }
                    .testTag("context_row_remove")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFEF5350),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(R.string.zig_gravity_remove),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFEF5350)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
