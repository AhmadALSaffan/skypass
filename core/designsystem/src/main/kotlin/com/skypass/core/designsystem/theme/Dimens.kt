package com.skypass.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radii from the board. */
object Shapes {
    val pill = RoundedCornerShape(percent = 50)
    val xs = RoundedCornerShape(8.dp)
    val chip = RoundedCornerShape(10.dp)
    val tile = RoundedCornerShape(12.dp)
    val stepper = RoundedCornerShape(14.dp)
    val segmentTrack = RoundedCornerShape(15.dp)
    val segment = RoundedCornerShape(11.dp)
    val sm = RoundedCornerShape(16.dp)
    /** Primary buttons, input fields. */
    val button = RoundedCornerShape(18.dp)
    val field = RoundedCornerShape(18.dp)
    val fieldGroup = RoundedCornerShape(20.dp)
    /** Content cards, tiles, seat map. */
    val card = RoundedCornerShape(22.dp)
    /** Ticket cards. */
    val ticket = RoundedCornerShape(24.dp)
    /** Hero cards: search card, hotel card, card preview, boarding pass. */
    val hero = RoundedCornerShape(26.dp)
    val heroLg = RoundedCornerShape(28.dp)
    val illustration = RoundedCornerShape(32.dp)
    /** Seats: rounder backrest on top. */
    val seat = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 8.dp, bottomEnd = 8.dp)
}

object Spacing {
    val xxs = 4.dp
    val xs = 6.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    /** Screen side gutter. */
    val gutter = 20.dp
    /** Onboarding / sign-in side gutter. */
    val gutterWide = 24.dp
    val xl = 24.dp
    val xxl = 32.dp
    /** Minimum touch target. */
    val touch = 44.dp
}

object Durations {
    const val FAST = 120
    const val BASE = 200
    const val SLOW = 320
    const val COLOR = 400
    const val BAR = 600
}
