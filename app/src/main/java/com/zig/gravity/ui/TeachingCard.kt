package com.zig.gravity.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijafari.red.astronomy.R
import com.zig.gravity.edu.TeachingCatalog
import com.zig.gravity.ui.theme.ZigGravityColor
import java.util.Locale

data class ActiveTeachingCard(
    val cardId: String,
    val bodyId: Long = 0L,
    val postedNanos: Long = System.nanoTime(),
    val successLine: String? = null,
    val reflection: String? = null
)

@Composable
fun TeachingCard(
    card: ActiveTeachingCard,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tier by remember(card.cardId, card.postedNanos) { mutableStateOf(1) }
    val isEn = Locale.getDefault().language == "en"
    val content = TeachingCatalog.getCard(card.cardId, isEn) ?: return
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxHeight = screenHeight * 0.40f

    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .heightIn(max = maxHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("teaching_card_${card.cardId}"),
        shape = RoundedCornerShape(16.dp),
        color = ZigGravityColor.surfaceCenter.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, ZigGravityColor.glassStroke),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header with close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (card.successLine != null) {
                        stringResource(R.string.zig_gravity_edu_challenges_title)
                    } else {
                        stringResource(R.string.zig_gravity_cd_teaching)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = ZigGravityColor.accent,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp).testTag("teaching_card_btn_close")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.close),
                        tint = ZigGravityColor.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reflection line if present (for challenge)
            if (card.reflection != null) {
                Text(
                    text = card.reflection,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZigGravityColor.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.testTag("teaching_card_reflection")
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Success line if present (for challenge)
            if (card.successLine != null) {
                Text(
                    text = card.successLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZigGravityColor.accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("teaching_card_success_line")
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Tier 1 Content
            Text(
                text = content.t1,
                style = MaterialTheme.typography.bodyMedium,
                color = ZigGravityColor.onSurface,
                modifier = Modifier.testTag("teaching_card_tier1")
            )

            // Tier 2 Content
            if (tier >= 2) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ZigGravityColor.glassStroke, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = content.t2,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZigGravityColor.onSurface.copy(alpha = 0.9f),
                    modifier = Modifier.testTag("teaching_card_tier2")
                )
            }

            // Tier 3 Content
            if (tier >= 3) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = ZigGravityColor.glassStroke, thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = content.t3,
                    style = MaterialTheme.typography.bodySmall,
                    color = ZigGravityColor.onSurface.copy(alpha = 0.85f),
                    modifier = Modifier.testTag("teaching_card_tier3")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Expansion Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (tier < 2) {
                    TextButton(
                        onClick = { tier = 2 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("teaching_card_btn_why")
                    ) {
                        Text(
                            text = stringResource(R.string.zig_gravity_edu_why),
                            color = ZigGravityColor.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (tier in 1..2) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { tier = 3 },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("teaching_card_btn_more")
                    ) {
                        Text(
                            text = stringResource(R.string.zig_gravity_edu_learn_more),
                            color = ZigGravityColor.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
