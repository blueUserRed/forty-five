package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Exception


class AdvancedTextParser(
    val code: String,
    private val screen: OnjScreen,
    defaults: OnjObject,
    private val changes: List<AdvancedTextEffect>
) {
    init {
        if (changes.map { it.indicator }.toSet().size != changes.size) {
            throw IllegalArgumentException("2 Times the same Indicator for the Special Changes")
        }
    }

    private var next = 0

    private val currentText: StringBuilder = StringBuilder()
    private val parts: MutableList<AdvancedTextPart> = mutableListOf()

    private val defaultSettings = Triple(
        ResourceManager.get(screen, defaults.get<String>("font")) as BitmapFont,
        defaults.get<Color>("color"),
        defaults.get<Double>("fontScale").toFloat()
    )

    private val activeTextEffects: MutableList<AdvancedTextEffect> = mutableListOf()

    private var curFont: BitmapFont = defaultSettings.first
    private var curColor: Color = defaultSettings.second
    private var curFontScale = defaultSettings.third


    fun parse(): AdvancedText {
        while (!end()) {
            nextChar()
        }
        finishText()
        return AdvancedText(parts)
    }

    private fun nextChar() {
        val c = consume()
        currentText.append(c)
        //TODO icons are missing
        checkEffects()
        if (c.isWhitespace()) finishText()
    }

    private fun checkEffects() {
        val curEffect = changes.find { currentText.endsWith(it.indicator) }
        if (curEffect != null) {
            currentText.removeSuffix(curEffect.indicator)
            if (curEffect in activeTextEffects) {
                curEffect.backToDefault(this)
                activeTextEffects.remove(curEffect)
            } else {
                curEffect.executeChange(this)
                activeTextEffects.add(curEffect)
            }
        }
    }

    private fun finishText() {
        var text = currentText.toString()
        val breakLine = text.endsWith("\n") || text.endsWith("\r")
        if (breakLine) text = text.trimEnd('\n', '\r')
        parts.add(
            TextAdvancedTextPart(
                text,
                curFont,
                curColor,
                curFontScale,
                screen,
                breakLine
            )
        )
//        parts.last().addDialogAction {  } //TODO this action adding
        currentText.clear()
    }

    private fun backtrack() {
        next--
    }

    private fun consume(): Char = code[next++]

    private fun end(): Boolean = next >= code.length

    private fun tryConsume(c: Char): Boolean {
        if (peek() != c) return false
        consume()
        return true
    }

    private fun last(): Char = code[next - 1]

    private fun peek(): Char = code[next]

    interface AdvancedTextEffect {
        val indicator: String

        fun executeChange(parser: AdvancedTextParser)
        fun backToDefault(parser: AdvancedTextParser)

        companion object {
            fun getFromOnj(screen: OnjScreen, onj: OnjNamedObject): AdvancedTextEffect {
                return when (onj.name) {
                    "Color" -> AdvancedColorTextEffect(onj)
                    "Font" -> AdvancedFontTextEffect(screen, onj)
                    "FontScale" -> AdvancedFontScaleTextEffect(onj)
                    else -> throw Exception("Unknown Text Effect")
                }
            }

            class AdvancedColorTextEffect(data: OnjObject) : AdvancedTextEffect {
                override val indicator: String = data.get<String>("indicator")
                private val color = data.get<Color>("color")
                override fun executeChange(parser: AdvancedTextParser) {
                    parser.curColor = color
                }

                override fun backToDefault(parser: AdvancedTextParser) {
                    parser.curColor = parser.defaultSettings.second
                }
            }
        }

        class AdvancedFontTextEffect(private val screen: OnjScreen, private val data: OnjNamedObject) :
            AdvancedTextEffect {
            override val indicator: String = data.get<String>("indicator")

            override fun executeChange(parser: AdvancedTextParser) {
                parser.curFont = ResourceManager.get(screen, data.get<String>("font")) as BitmapFont
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.curFont = parser.defaultSettings.first
            }
        }


        class AdvancedFontScaleTextEffect(private val data: OnjNamedObject) :
            AdvancedTextEffect {
            override val indicator: String = data.get<String>("indicator")

            override fun executeChange(parser: AdvancedTextParser) {
                parser.curFontScale = data.get<Double>("fontScale").toFloat()
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.curFontScale = parser.defaultSettings.third
            }
        }
    }
}