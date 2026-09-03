package com.zig.gravity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijafari.red.astronomy.R
import com.zig.gravity.edu.ChallengeDef
import com.zig.gravity.edu.Challenges
import com.zig.gravity.ui.theme.ZigGravityColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChallengesSheet(
    onSelectChallenge: (ChallengeDef) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ZigGravityColor.surfaceCenter,
        scrimColor = ZigGravityColor.vignette.copy(alpha = 0.6f),
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("challenges_sheet")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        tint = ZigGravityColor.accent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.zig_gravity_edu_challenges_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ZigGravityColor.onSurface
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp).testTag("challenges_sheet_btn_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = ZigGravityColor.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.zig_gravity_edu_challenges_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = ZigGravityColor.onSurfaceVariant,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = ZigGravityColor.glassStroke, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(Challenges.list) { index, challenge ->
                    ChallengeItemRow(
                        index = index + 1,
                        challenge = challenge,
                        onClick = {
                            onSelectChallenge(challenge)
                            onDismiss()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChallengeItemRow(
    index: Int,
    challenge: ChallengeDef,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("challenge_item_${challenge.id}"),
        shape = RoundedCornerShape(14.dp),
        color = ZigGravityColor.surfaceEdge,
        border = BorderStroke(1.dp, ZigGravityColor.glassStroke)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(ZigGravityColor.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.accent
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(challenge.titleRes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = ZigGravityColor.onSurface
                )
                Text(
                    text = stringResource(challenge.introRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = ZigGravityColor.onSurfaceVariant,
                    maxLines = 1,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = ZigGravityColor.accent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
