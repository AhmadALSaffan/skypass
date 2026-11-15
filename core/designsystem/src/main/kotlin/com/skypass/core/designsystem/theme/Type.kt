package com.skypass.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skypass.core.designsystem.R

val JakartaSans = FontFamily(
    Font(R.font.plus_jakarta_sans_regular, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans_medium, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans_semibold, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans_bold, FontWeight.Bold),
    Font(R.font.plus_jakarta_sans_extrabold, FontWeight.ExtraBold),
)

val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private fun jakarta(size: Int, weight: FontWeight, lineHeight: Float = 1.25f, tracking: Float = 0f) = TextStyle(
    fontFamily = JakartaSans,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
)

private fun mono(size: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) = TextStyle(
    fontFamily = DmMono,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * 1.3f).sp,
    letterSpacing = tracking.em,
)

/** Type scale lifted from the board (sizes in px → sp). */
@Immutable
data class SkypassTypography(
    /** 34/800 — boarding pass airport codes. */
    val codeXl: TextStyle = jakarta(34, FontWeight.ExtraBold, 1f, -0.02f),
    /** 32/800 — sign-in title. */
    val displayLg: TextStyle = jakarta(32, FontWeight.ExtraBold, 1.12f, -0.03f),
    /** 30/800 — onboarding title. */
    val display: TextStyle = jakarta(30, FontWeight.ExtraBold, 1.12f, -0.03f),
    /** 28/800 — screen titles. */
    val headline: TextStyle = jakarta(28, FontWeight.ExtraBold, 1.12f, -0.03f),
    /** 26/800 — ticket price, onboarding codes. */
    val price: TextStyle = jakarta(26, FontWeight.ExtraBold, 1.1f, -0.02f),
    /** 24/800 — airport codes in fields and tiles. */
    val code: TextStyle = jakarta(24, FontWeight.ExtraBold, 1.1f, -0.02f),
    /** 22/800 — times, totals, stats. */
    val figure: TextStyle = jakarta(22, FontWeight.ExtraBold, 1.15f, -0.02f),
    /** 20/800 — route codes, card prices. */
    val figureSm: TextStyle = jakarta(20, FontWeight.ExtraBold, 1.15f, -0.02f),
    /** 18/800 — card titles, profile name. */
    val titleLg: TextStyle = jakarta(18, FontWeight.ExtraBold, 1.25f, -0.01f),
    /** 17/800 — top bar titles. */
    val topBar: TextStyle = jakarta(17, FontWeight.ExtraBold, 1.25f, -0.01f),
    /** 16/800 — section headers, wordmark in app. */
    val title: TextStyle = jakarta(16, FontWeight.ExtraBold, 1.25f, -0.02f),
    /** 16/700 — card names, big buttons. */
    val titleSm: TextStyle = jakarta(16, FontWeight.Bold, 1.25f),
    /** 15/700 — buttons, strong body. */
    val label: TextStyle = jakarta(15, FontWeight.Bold, 1.3f),
    /** 15/400 — body copy. */
    val body: TextStyle = jakarta(15, FontWeight.Normal, 1.55f),
    /** 15/600 — list rows. */
    val bodyStrong: TextStyle = jakarta(15, FontWeight.SemiBold, 1.4f),
    /** 14/600 — secondary buttons. */
    val labelMd: TextStyle = jakarta(14, FontWeight.SemiBold, 1.3f),
    /** 13/700 — chips, segments, links. */
    val labelSm: TextStyle = jakarta(13, FontWeight.Bold, 1.3f),
    /** 13/400 — captions. */
    val bodySm: TextStyle = jakarta(13, FontWeight.Normal, 1.4f),
    /** 12/400 — meta text. */
    val caption: TextStyle = jakarta(12, FontWeight.Normal, 1.4f),
    /** 12/600 — perks, stat labels. */
    val captionStrong: TextStyle = jakarta(12, FontWeight.SemiBold, 1.35f),
    /** 11/700 — pills. */
    val pill: TextStyle = jakarta(11, FontWeight.Bold, 1.3f),
    /** DM Mono 13 — PNR, step counters. */
    val mono: TextStyle = mono(13, FontWeight.Medium),
    /** DM Mono 11 — flight numbers, counters. */
    val monoSm: TextStyle = mono(11),
    /** DM Mono 10 uppercase-tracked field labels (FROM, DEPART…). */
    val eyebrow: TextStyle = mono(10, FontWeight.Medium, 0.08f),
    /** DM Mono 9 — ticket grid labels. */
    val eyebrowSm: TextStyle = mono(9, FontWeight.Medium, 0.08f),
)
