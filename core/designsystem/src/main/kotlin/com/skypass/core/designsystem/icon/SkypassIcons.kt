package com.skypass.core.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The board's icon set, path data copied from the design's inline SVGs (24×24 viewBox).
 * Icons are drawn in black and tinted by `Icon(tint = …)`.
 */
object SkypassIcons {
    val ArrowLeft = stroke("arrow_left", 2f, "M19 12H5M12 19l-7-7 7-7")
    val ArrowRight = stroke("arrow_right", 2f, "M5 12h14M12 5l7 7-7 7")
    val ChevronRight = stroke("chevron_right", 2.2f, "m9 6 6 6-6 6")
    val ChevronDown = stroke("chevron_down", 2f, "m6 9 6 6 6-6")
    val Close = stroke("close", 2.4f, "M18 6 6 18M6 6l12 12")
    val Check = stroke("check", 3f, "M20 6 9 17l-5-5")
    val Swap = stroke("swap", 2f, "M7 4v16M7 4 3 8M7 4l4 4M17 20V4M17 20l-4-4M17 20l4-4")
    val Search = stroke("search", 2.2f, circle(11f, 11f, 7f) + "M20 20l-3.5-3.5")
    val Bell = stroke("bell", 1.8f, "M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9M13.73 21a2 2 0 0 1-3.46 0")
    val Filters = stroke("filters", 1.9f, "M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6")
    val Map = stroke("map", 1.9f, "M9 4 3 6v14l6-2 6 2 6-2V4l-6 2zM9 4v14M15 6v14")
    val Share = stroke("share", 2f, "M12 3v13M7 8l5-5 5 5M5 14v5a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-5")
    val Settings = stroke("settings", 1.8f, circle(12f, 12f, 3f) + "M12 2v3M12 19v3M4.9 4.9l2.1 2.1M17 17l2.1 2.1M2 12h3M19 12h3M4.9 19.1 7 17M17 7l2.1-2.1")
    val Lock = stroke("lock", 2.2f, "M6 11h12a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-6a2 2 0 0 1 2-2zM8 11V7a4 4 0 0 1 8 0v4")
    val Wallet = stroke("wallet", 1.9f, "M6 5h12a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V8a3 3 0 0 1 3-3zM3 10h18M15 15h3")
    val Bed = stroke("bed", 1.9f, "M3 19V6M3 15h18v4M21 15v-3a3 3 0 0 0-3-3h-7v6M7 12.5a1.5 1.5 0 1 0 0-.01")
    val Car = stroke("car", 1.9f, "M3 13l2-5.5A2 2 0 0 1 6.9 6h10.2a2 2 0 0 1 1.9 1.5L21 13v5h-2M3 13v5h2M3 13h18M9 18h6" + circle(7f, 18f, 2f) + circle(17f, 18f, 2f))
    val Heart = stroke("heart", 1.9f, "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z")
    val HeartFilled = fill("heart_filled", "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1-1.1a5.5 5.5 0 0 0-7.8 7.8l1 1.1L12 21l7.8-7.5 1-1.1a5.5 5.5 0 0 0 0-7.8z")
    val Bookmark = stroke("bookmark", 1.8f, "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    val BookmarkFilled = fill("bookmark_filled", "M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
    val Home = stroke("home", 1.8f, "M3 9.5 12 2.5l9 7V20a2 2 0 0 1-2 2h-4v-6h-6v6H5a2 2 0 0 1-2-2z")
    val HomeFilled = fill("home_filled", "M3 9.5 12 2.5l9 7V20a2 2 0 0 1-2 2h-4v-6h-6v6H5a2 2 0 0 1-2-2z")
    val Compass = stroke("compass", 1.8f, circle(12f, 12f, 10f) + "M16.24 7.76l-2.12 6.36-6.36 2.12 2.12-6.36z")
    val User = stroke("user", 1.8f, "M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" + circle(12f, 7f, 4f))
    val Plane = fill("plane", "M21 16v-2l-8-5V3.5c0-.83-.67-1.5-1.5-1.5S10 2.67 10 3.5V9l-8 5v2l8-2.5V19l-2 1.5V22l3.5-1 3.5 1v-1.5L13 19v-5.5l8 2.5z")
    val Star = fill("star", "m12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01z")
    val Wifi = stroke("wifi", 2f, "M5 12.55a11 11 0 0 1 14.08 0M1.42 9a16 16 0 0 1 21.16 0M8.53 16.11a6 6 0 0 1 6.95 0M12 20h.01")
    val CloudOff = stroke("cloud_off", 2f, "M22.61 16.95A5 5 0 0 0 18 10h-1.26a8 8 0 0 0-7.05-6M5 5a8 8 0 0 0 4 15h9a5 5 0 0 0 1.7-.3M1 1l22 22")
}

/** SVG `<circle>` as path data (two arcs). Anything appended after it must start with an absolute `M`. */
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"

private fun stroke(name: String, width: Float, d: String) = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(d),
    stroke = SolidColor(Color.Black),
    strokeLineWidth = width,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
).build()

private fun fill(name: String, d: String) = ImageVector.Builder(
    name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
).addPath(
    pathData = addPathNodes(d),
    fill = SolidColor(Color.Black),
).build()
