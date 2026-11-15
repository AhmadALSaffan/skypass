package com.skypass.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The four-colour Google Wallet mark (from the official 2022 logo on Wikimedia Commons), for the
 * "Add to Google Wallet" button. Draw with Image, not Icon, to keep its colours.
 */
val GoogleWalletMark: ImageVector = ImageVector.Builder(
    name = "google_wallet",
    defaultWidth = 22.dp,
    defaultHeight = 20.dp,
    viewportWidth = 78.2f,
    viewportHeight = 70.1f,
).apply {
    addPath(pathData = addPathNodes("M 0,29.048546 49.575109,41.239 c 5.858191,1.427514 12.093461,0.129285 16.87967,-3.630731 L 78.109212,28.53141 v 28.528716 c 0,7.132179 -5.669651,12.968822 -12.597131,12.968822 H 12.597131 C 5.6696514,70.028948 0,64.192305 0,57.060126 Z"), fill = SolidColor(Color(0xFF4285F4)))
    addPath(pathData = addPathNodes("m 12.597663,22.04492 c -4.9611601,0 -9.264,3.00129 -11.3144601,7.32031 L 49.574223,41.23828 c 5.85819,1.42751 12.09465,0.12916 16.88086,-3.63086 l 10.42773,-8.12109 c -2.02681,-4.38406 -6.36371,-7.44141 -11.37109,-7.44141 z"), fill = SolidColor(Color(0xFFFE2C25)))
    addPath(pathData = addPathNodes("M 12.597663,11.023438 C 5.6701829,11.023438 0,16.858055 0,23.990234 v 5.058594 l 1.2832,0.316406 c 2.0504601,-4.319025 6.3533,-7.320312 11.3144601,-7.320312 h 52.91406 c 5.00738,0 9.34428,3.057346 11.37109,7.441406 l 1.22657,-0.955078 v -4.541016 c 0,-7.132179 -5.67018,-12.9668 -12.59766,-12.9668 z"), fill = SolidColor(Color(0xFFFFBB00)))
    addPath(pathData = addPathNodes("M 12.597659,0 C 5.67018,0 0,5.836571 0,12.96875 v 11.021484 c 0,-7.132179 5.67017665,-12.9668 12.59765605,-12.9668 h 52.914063 c 6.92748,0 12.597656,5.834617 12.597656,12.9668 V 12.96875 C 78.109378,5.836571 72.439202,0 65.511722,0 Z"), fill = SolidColor(Color(0xFF34A853)))
}.build()
