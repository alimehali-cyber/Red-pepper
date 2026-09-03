package com.alijafari.red.astronomy.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alijafari.red.astronomy.domain.AppLanguage
import com.alijafari.red.astronomy.ui.MainUiState
import com.alijafari.red.astronomy.ui.MainViewModel
import com.alijafari.red.astronomy.ui.theme.*

enum class LabFeatureType(
    val titleEn: String,
    val titleFa: String,
    val subtitleEn: String,
    val subtitleFa: String,
    val descriptionEn: String,
    val descriptionFa: String,
    val icon: ImageVector,
    val isAvailable: Boolean
) {
    GRAVITY_SANDBOX(
        titleEn = "Gravity Sandbox",
        titleFa = "میز گرانش (سندباکس)",
        subtitleEn = "N-Body Gravitational Tabletop",
        subtitleFa = "شبیه‌ساز و میز آزمایش جاذبه N-جرم",
        descriptionEn = "Interactive N-body celestial mechanics tabletop simulation.",
        descriptionFa = "میز آزمایشگاهی مکانیک سماوی و شبیه‌سازی گرانش چندجرمی.",
        icon = Icons.Default.Public,
        isAvailable = true
    ),
    TIME_DILATION(
        titleEn = "Time Dilation",
        titleFa = "انقباض زمان و نسبیت",
        subtitleEn = "Relativistic Journey Calculator",
        subtitleFa = "محاسبه‌گر سفرهای بین‌ستاره‌ای نسبیتی",
        descriptionEn = "Simulate relativistic time dilation, proper time, Lorentz factor, and length contraction for interstellar voyages.",
        descriptionFa = "محاسبه و شبیه‌سازی انقباض زمان آینشتاین، زمان اختصاصی، عامل لورنتس و انقباض طول در سفرهای بین‌ستاره‌ای.",
        icon = Icons.Default.HourglassTop,
        isAvailable = true
    ),
    ORBITAL_RESONANCE(
        titleEn = "Orbital Resonance & Keplerian Elements",
        titleFa = "رزونانس مداری و عناصر کپلری",
        subtitleEn = "Celestial Mechanics Tool",
        subtitleFa = "مکانیک سماوی و شبیه‌ساز مدارها",
        descriptionEn = "Analyze gravitational orbital harmonics, Hill spheres, Lagrange points, and orbital resonances.",
        descriptionFa = "تحلیل رزونانس‌های گرانشی، نقاط لاگرانژی و دامنه‌های هیل در اجرام منظومه شمسی.",
        icon = Icons.Default.AllInclusive,
        isAvailable = false
    ),
    STELLAR_EVOLUTION(
        titleEn = "HR Diagram & Stellar Lifetime",
        titleFa = "نمودار هرتسپرونگ-راسل و تکامل ستارگان",
        subtitleEn = "Astrophysical Classifier",
        subtitleFa = "اخترفیزیک و حیات ستاره‌ای",
        descriptionEn = "Plot main sequence stars, red giants, white dwarfs, and compute nuclear fusion lifetimes.",
        descriptionFa = "رسم و تحلیل نمودار H-R، جایگاه تکاملی ستارگان و طول عمر همجوشی هسته‌ای.",
        icon = Icons.Default.AutoAwesome,
        isAvailable = false
    )
}

@Composable
fun LabScreen(
    uiState: MainUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val isFa = uiState.language == AppLanguage.PERSIAN
    var selectedFeature by remember { mutableStateOf<LabFeatureType?>(null) }

    if (selectedFeature == LabFeatureType.GRAVITY_SANDBOX) {
        Box(modifier = modifier.fillMaxSize()) {
            com.zig.gravity.ui.GravitySandboxScreen()

            IconButton(
                onClick = { selectedFeature = null },
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 8.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(com.zig.gravity.ui.theme.ZigGravityColor.glassContainer)
                    .border(1.dp, com.zig.gravity.ui.theme.ZigGravityColor.glassStroke, CircleShape)
                    .align(Alignment.TopStart)
                    .testTag("gravity_back_to_lab")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = com.zig.gravity.ui.theme.ZigGravityColor.onSurface
                )
            }
        }
    } else if (selectedFeature == LabFeatureType.TIME_DILATION) {
        TimeDilationCalculatorScreen(
            uiState = uiState,
            onBackToLab = { selectedFeature = null },
            modifier = modifier
        )
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .testTag("lab_screen"),
            contentPadding = PaddingValues(horizontal = RedSpacing.lg, vertical = RedSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(RedSpacing.lg)
        ) {
            // Lab Header Banner
            item {
                RedElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lab_header_card"),
                    shape = RoundedCornerShape(RedCornerRadius.xl),
                    backgroundColor = RedTheme.colors.surfaceElevated,
                    borderColor = RedTheme.colors.border,
                    contentPadding = PaddingValues(RedSpacing.lg)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(RedSpacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(RedSpacing.md)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(RedTheme.colors.accentRed.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Science,
                                    contentDescription = null,
                                    tint = RedTheme.colors.accentRed,
                                    modifier = Modifier.size(RedIconSize.lg)
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = if (isFa) "آزمایشگاه نجومی و فیزیک" else "Astrophysics Lab",
                                    style = RedTypographyTokens.sectionHeading,
                                    color = RedTheme.colors.textPrimary
                                )
                                Text(
                                    text = if (isFa) "مجموعه ابزارهای علمی و محاسبه‌گرهای نجومی" else "Scientific tools & computational simulators",
                                    style = RedTypographyTokens.caption,
                                    color = RedTheme.colors.textSecondary
                                )
                            }
                        }

                        Text(
                            text = if (isFa)
                                "آزمایشگاه نجومی محیطی برای آزمایش فرضیه‌ها، محاسبات نسبیتی، مکانیک سماوی و شبیه‌سازی‌های پیشرفته است."
                            else
                                "An expandable suite of advanced computational astrophysics tools and relativistic physics simulators.",
                            style = RedTypographyTokens.bodySecondary,
                            color = RedTheme.colors.textSecondary
                        )
                    }
                }
            }

            // Section Title
            item {
                RedSectionHeader(
                    title = if (isFa) "ابزارهای فعال و در حال توسعه" else "Available Scientific Tools",
                    subtitle = if (isFa) "شبیه‌سازها و ماشین‌حساب‌های اخترفیزیک" else "Astrophysics calculators & simulators"
                )
            }

            // Feature List Cards
            items(LabFeatureType.entries) { feature ->
                LabFeatureCard(
                    feature = feature,
                    isFa = isFa,
                    onClick = {
                        if (feature.isAvailable) {
                            selectedFeature = feature
                        }
                    }
                )
            }

            // Bottom spacing for floating navigation bar
            item {
                Spacer(modifier = Modifier.height(112.dp))
            }
        }
    }
}

@Composable
private fun LabFeatureCard(
    feature: LabFeatureType,
    isFa: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RedCornerRadius.xl))
            .clickable(enabled = feature.isAvailable, onClick = onClick)
            .testTag("lab_feature_card_${feature.name.lowercase()}"),
        shape = RoundedCornerShape(RedCornerRadius.xl),
        color = if (feature.isAvailable) RedTheme.colors.surfaceElevated else RedTheme.colors.surfaceGrouped,
        border = BorderStroke(
            width = 1.dp,
            color = if (feature.isAvailable) RedTheme.colors.border else RedTheme.colors.border.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(RedSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(RedSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (feature.isAvailable) RedTheme.colors.accentRed.copy(alpha = 0.12f)
                        else RedTheme.colors.surfaceGrouped
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = feature.icon,
                    contentDescription = null,
                    tint = if (feature.isAvailable) RedTheme.colors.accentRed else RedTheme.colors.textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(RedIconSize.md)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(RedSpacing.sm)
                ) {
                    Text(
                        text = if (isFa) feature.titleFa else feature.titleEn,
                        style = RedTypographyTokens.sectionHeading.copy(fontSize = 17.sp),
                        color = if (feature.isAvailable) RedTheme.colors.textPrimary else RedTheme.colors.textSecondary
                    )

                    if (!feature.isAvailable) {
                        RedBadge(
                            text = if (isFa) "به زودی" else "Coming Soon",
                            backgroundColor = RedTheme.colors.surfaceGrouped,
                            textColor = RedTheme.colors.textSecondary,
                            borderColor = RedTheme.colors.border
                        )
                    }
                }

                Text(
                    text = if (isFa) feature.subtitleFa else feature.subtitleEn,
                    style = RedTypographyTokens.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (feature.isAvailable) RedTheme.colors.accentRed else RedTheme.colors.textSecondary
                )

                Text(
                    text = if (isFa) feature.descriptionFa else feature.descriptionEn,
                    style = RedTypographyTokens.bodySecondary,
                    color = RedTheme.colors.textSecondary,
                    maxLines = 2
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open",
                tint = if (feature.isAvailable) RedTheme.colors.accentRed else RedTheme.colors.textSecondary.copy(alpha = 0.3f),
                modifier = Modifier.size(RedIconSize.md)
            )
        }
    }
}
