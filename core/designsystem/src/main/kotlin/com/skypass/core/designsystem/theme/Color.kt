package com.skypass.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Raw palette from the Skypass board. Features never use these directly — they read [SkypassColors]. */
internal object Palette {
    val Ink = Color(0xFF1C2550)
    val Teal = Color(0xFF0F5B56)
    val TealDeep = Color(0xFF0B4642)
    val Canvas = Color(0xFFF2F3F8)
    val White = Color(0xFFFFFFFF)

    val Slate = Color(0xFF5B6386)
    val SlateDeep = Color(0xFF3D4670)
    val SlateMuted = Color(0xFF7D84A6)

    val Line = Color(0xFFE4E6F0)
    val LineSoft = Color(0xFFEEF0F6)
    val LineStrong = Color(0xFFC6CADB)
    val Perforation = Color(0xFFD7DAE6)

    val Mint = Color(0xFFCDEBE6)
    val Lavender = Color(0xFFDEDCF6)
    val Peach = Color(0xFFF9DCD2)
    val Butter = Color(0xFFF7EBC4)
    val Sky = Color(0xFFCFE0F2)
    val MintPale = Color(0xFFE6F4F1)
    val Lilac = Color(0xFFE6E8F2)
    val MintBorder = Color(0xFF8CCFC4)

    val Alert = Color(0xFFE5533D)
    val Danger = Color(0xFFB5462F)
    val Star = Color(0xFFF5B400)
    val GoldInk = Color(0xFF6B4E00)

    // Dark theme (derived: navy night, same teal brand lifted for contrast).
    val Night = Color(0xFF0E1226)
    val NightSurface = Color(0xFF181D36)
    val NightSunken = Color(0xFF222844)
    val NightLine = Color(0xFF2E3556)
    val NightText = Color(0xFFEDEFF7)
    val NightText2 = Color(0xFFA4AACA)
    val TealBright = Color(0xFF5FC2B6)
}

@Immutable
data class SkypassColors(
    val isDark: Boolean,
    /** Screen background (#F2F3F8). */
    val bg: Color,
    /** Cards, bars, sheets. */
    val surface: Color,
    /** Tracks, stepper buttons, chips, icon tiles. */
    val surfaceSunken: Color,
    /** Navy ink: primary text and primary buttons. */
    val ink: Color,
    val onInk: Color,
    /** Deep teal brand: links, toggles, success, pressed buttons. */
    val brand: Color,
    val onBrand: Color,
    /** Text on mint pills ("On time", "Best value"). */
    val brandDeep: Color,
    val text2: Color,
    val text3: Color,
    val icon2: Color,
    val border: Color,
    val borderSoft: Color,
    val borderStrong: Color,
    val perforation: Color,
    val mint: Color,
    val lavender: Color,
    val peach: Color,
    val butter: Color,
    val sky: Color,
    val mintPale: Color,
    val lilac: Color,
    val mintBorder: Color,
    val alert: Color,
    val danger: Color,
    val star: Color,
    val goldInk: Color,
    /** Text on pastel tiles: pastels stay light in both themes. */
    val onPastel: Color,
    val onPastel2: Color,
    val scrim: Color,
)

internal val LightSkypassColors = SkypassColors(
    isDark = false,
    bg = Palette.Canvas,
    surface = Palette.White,
    surfaceSunken = Palette.Canvas,
    ink = Palette.Ink,
    onInk = Palette.White,
    brand = Palette.Teal,
    onBrand = Palette.White,
    brandDeep = Palette.TealDeep,
    text2 = Palette.Slate,
    text3 = Palette.SlateDeep,
    icon2 = Palette.SlateMuted,
    border = Palette.Line,
    borderSoft = Palette.LineSoft,
    borderStrong = Palette.LineStrong,
    perforation = Palette.Perforation,
    mint = Palette.Mint,
    lavender = Palette.Lavender,
    peach = Palette.Peach,
    butter = Palette.Butter,
    sky = Palette.Sky,
    mintPale = Palette.MintPale,
    lilac = Palette.Lilac,
    mintBorder = Palette.MintBorder,
    alert = Palette.Alert,
    danger = Palette.Danger,
    star = Palette.Star,
    goldInk = Palette.GoldInk,
    onPastel = Palette.Ink,
    onPastel2 = Palette.SlateDeep,
    scrim = Color(0xB8141830),
)

internal val DarkSkypassColors = LightSkypassColors.copy(
    isDark = true,
    bg = Palette.Night,
    surface = Palette.NightSurface,
    surfaceSunken = Palette.NightSunken,
    ink = Palette.NightText,
    onInk = Palette.Night,
    brand = Palette.TealBright,
    onBrand = Palette.Night,
    text2 = Palette.NightText2,
    text3 = Palette.NightText2,
    icon2 = Color(0xFF7D84A6),
    border = Palette.NightLine,
    borderSoft = Color(0xFF262C4A),
    borderStrong = Color(0xFF454D73),
    perforation = Color(0xFF3A4166),
    scrim = Color(0xD9050714),
)
