package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.Color
import kotlin.reflect.KProperty

object Color : com.badlogic.gdx.graphics.Color() {
    // regex to replace from colors.onj
    // (\w+): color\(\"(.{6})\"\),
    // val $1 = Color(0x$2ff)


    private fun Color(l: Long): Color {
        val color = Color()
        color.r = (l and -0x1000000 ushr 24) / 255f
        color.g = (l and 0x00ff0000 ushr 16) / 255f
        color.b = (l and 0x0000ff00 ushr 8) / 255f
        color.a = (l and 0x000000ff) / 255f
        return color
    }

    val FortyWhite = Color(0xF0EADDff)
    val White = Color(0xffffff)
    val Red = Color(0xff0000)
    val Green = Color(0x00ff00ff)
    val DarkGreen = Color(0x6a8759ff)
    val Blue = Color(0x0000ffff)
    val Yellow = Color(0xaaaa00ff)
    val BrightYellow = Color(0xf6be42ff)
    val Black = Color(0x000000ff)
    val BackgroundTransparent = Color(0x04040488)
    val Beige = Color(0x615130ff)
    val Grey = Color(0xaaaaaaff)
    val Taupe_gray = Color(0x888888ff)
    val Viridian = Color(0x5B8266ff)
    val DarkBrown = Color(0x2A2424ff)
    val LightBrown = Color(0xc6c0b2ff)
    val Orange = Color(0xDCA733ff)
    val MicrowaveStudiosBrown = Color(0x53473Bff)
    val SeljukBlue = Color(0x4588EEff)
    val HemoglobinRed = Color(0xC21B1Bff)
    val Magenta = Color(0xA945A1ff)
    val FrostyMintGreen = Color(0xE4F8F0ff)




    private val colorDelegate = ColorDelegate()

    val LIGHT_GRAY by colorDelegate
    val GRAY by colorDelegate
    val DARK_GRAY by colorDelegate
//    val BLACK by colorDelegate

    val WHITE_FLOAT_BITS by colorDelegate

    val CLEAR = Color(0f, 0f, 0f, 0f)

//    val BLUE by colorDelegate
    val NAVY by colorDelegate
    val ROYAL by colorDelegate
    val SLATE by colorDelegate
    val SKY by colorDelegate
    val CYAN by colorDelegate
    val TEAL by colorDelegate

//    val GREEN by colorDelegate
    val CHARTREUSE by colorDelegate
    val LIME by colorDelegate
    val FOREST by colorDelegate
    val OLIVE by colorDelegate

//    val YELLOW by colorDelegate
    val GOLD by colorDelegate
    val GOLDENROD by colorDelegate
//    val ORANGE by colorDelegate

    val BROWN by colorDelegate
    val TAN by colorDelegate
    val FIREBRICK by colorDelegate

//    val RED by colorDelegate
    val SCARLET by colorDelegate
    val CORAL by colorDelegate
    val SALMON by colorDelegate
    val PINK by colorDelegate
//    val MAGENTA by colorDelegate

    val PURPLE by colorDelegate
    val VIOLET by colorDelegate
    val MAROON by colorDelegate

    private class ColorDelegate {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Color {
            return Color::class.java.getDeclaredField(property.name).get(null) as Color
        }
    }
}
