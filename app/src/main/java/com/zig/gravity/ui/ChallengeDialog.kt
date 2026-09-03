package com.zig.gravity.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.alijafari.red.astronomy.R
import com.zig.gravity.edu.ChallengeDef
import com.zig.gravity.ui.theme.ZigGravityColor

@Composable
fun ChallengePredictionDialog(
    challenge: ChallengeDef,
    onSelectOption: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = ZigGravityColor.surfaceCenter,
            border = BorderStroke(1.dp, ZigGravityColor.glassStroke),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("challenge_prediction_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(challenge.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ZigGravityColor.accent
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp).testTag("challenge_dialog_btn_close")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.close),
                            tint = ZigGravityColor.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(challenge.introRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZigGravityColor.onSurface,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.zig_gravity_edu_predict_prompt),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                challenge.optionsRes.forEachIndexed { index, optRes ->
                    Surface(
                        onClick = { onSelectOption(index) },
                        shape = RoundedCornerShape(12.dp),
                        color = ZigGravityColor.surfaceEdge,
                        border = BorderStroke(1.dp, ZigGravityColor.glassStroke),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("challenge_option_$index")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                fontWeight = FontWeight.Bold,
                                color = ZigGravityColor.accent,
                                modifier = Modifier.width(20.dp)
                            )
                            Text(
                                text = stringResource(optRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ZigGravityColor.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChallengeHintChip(
    onReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("challenge_hint_chip"),
        shape = RoundedCornerShape(20.dp),
        color = ZigGravityColor.surfaceCenter.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, ZigGravityColor.accent.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Visibility,
                contentDescription = null,
                tint = ZigGravityColor.accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.zig_gravity_edu_observe),
                style = MaterialTheme.typography.bodySmall,
                color = ZigGravityColor.onSurface
            )
            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = onReady,
                colors = ButtonDefaults.buttonColors(containerColor = ZigGravityColor.accent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("challenge_btn_ready")
            ) {
                Text(
                    text = stringResource(R.string.zig_gravity_edu_ready),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ZigGravityColor.surfaceEdge
                )
            }
        }
    }
}
