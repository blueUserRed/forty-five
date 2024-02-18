package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Action
import com.fourinachamber.fortyfive.onjNamespaces.OnjColor
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Exception


class AdvancedTextParser(
    val code: String,
    private val screen: OnjScreen,
    defaults: OnjObject,
    private val isDistanceField: Boolean,
    private val changes: List<AdvancedTextEffect>
) {
    init {
        if (changes.any { ICON_INDICATOR in it.indicator }) {
            FortyFiveLogger.warn(
                logTag,
                "Cannot contain the ICON_INDICATOR '$ICON_INDICATOR' in an Indicator for the Effects"
            )
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
    private var currentActions: MutableList<AdvancedTextPart.() -> Unit> = mutableListOf()

    private var isReadingIcon = false

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
        checkIcons()
        //TODO actions are missing
        checkEffects()
        if (c.isWhitespace()) finishText()
    }

    private fun checkIcons() {
        if (currentText.endsWith(ICON_INDICATOR)) {
            val newText = currentText.removeSuffix(ICON_INDICATOR)
            currentText.clear()
            currentText.append(newText)
            finishText()
            isReadingIcon = !isReadingIcon
        }
    }

    private fun checkEffects() {
        val curEffects = changes.filter { currentText.endsWith(it.indicator) }
        if (curEffects.isNotEmpty()) {
            val newText = currentText.removeSuffix(curEffects.first().indicator)
            currentText.clear()
            currentText.append(newText)
            finishText()
            for (curEffect in curEffects) {
                if (curEffect in activeTextEffects) {
                    curEffect.backToDefault(this)
                    activeTextEffects.remove(curEffect)
                } else {
                    curEffect.executeChange(this)
                    if (curEffect.overridesOthers) {
                        activeTextEffects.removeIf { it::class == curEffect::class }
                    }
                    activeTextEffects.add(curEffect)
                }
            }

        }
    }

    private fun finishText() {
        var text = currentText.toString()
        if (text.isEmpty()) return

        val breakLine = text.endsWith("\n") || text.endsWith("\r")
        if (breakLine) text = text.trimEnd('\n', '\r')

        if (isReadingIcon) {
            parts.add(IconAdvancedTextPart(text.trim(), curFont, screen, curFontScale, breakLine))
        } else {
            parts.add(
                TextAdvancedTextPart(
                    text,
                    curFont,
                    curColor,
                    curFontScale,
                    screen,
                    isDistanceField,
                    breakLine,
                )
            )
        }
        for (i in currentActions)
            parts.last().addDialogAction(i)
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

    companion object {
        const val ICON_INDICATOR = "§§"
        const val logTag = "TextParser"
    }

    interface AdvancedTextEffect {

        /**
         * the indicator to replace when starting or ending
         */
        val indicator: String

        /**
         * if it overrides others from the same type
         */
        val overridesOthers: Boolean

        fun executeChange(parser: AdvancedTextParser)
        fun backToDefault(parser: AdvancedTextParser)

        fun asOnjObject(): OnjObject

        companion object {
            fun getFromOnj(screen: OnjScreen, onj: OnjNamedObject): AdvancedTextEffect {
                return when (onj.name) {
                    "Color" -> AdvancedColorTextEffect(onj)
                    "Font" -> AdvancedFontTextEffect(screen, onj)
                    "FontScale" -> AdvancedFontScaleTextEffect(onj)
                    "Action" -> AdvancedActionTextEffect(onj)
                    else -> throw Exception("Unknown Text Effect: ${onj.name}")
                }
            }
        }

        class AdvancedColorTextEffect(
            override val indicator: String,
            private val color: Color,
        ) : AdvancedTextEffect {

            override val overridesOthers: Boolean = true

            constructor(data: OnjObject) : this(data.get<String>("indicator"), data.get<Color>("color"))

            override fun executeChange(parser: AdvancedTextParser) {
                parser.curColor = color
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.curColor = parser.defaultSettings.second
            }

            override fun asOnjObject(): OnjObject = buildOnjObject {
                name("Color")
                "indicator" with indicator
                "color" with OnjColor(color)
            }
        }

        class AdvancedFontTextEffect(
            private val screen: OnjScreen,
            override val indicator: String,
            private val fontName: String
        ) :
            AdvancedTextEffect {

            override val overridesOthers: Boolean = true

            constructor(screen: OnjScreen, data: OnjObject) : this(
                screen,
                data.get<String>("indicator"),
                data.get<String>("font")
            )

            override fun executeChange(parser: AdvancedTextParser) {
                parser.curFont = ResourceManager.get(screen, fontName) as BitmapFont
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.curFont = parser.defaultSettings.first
            }

            override fun asOnjObject(): OnjObject = buildOnjObject {
                name("Font")
                "indicator" with indicator
                "font" with fontName
            }
        }


        class AdvancedFontScaleTextEffect(
            override val indicator: String,
            private val fontScale: Float,
        ) : AdvancedTextEffect {

            override val overridesOthers: Boolean = true

            constructor(data: OnjObject) : this(
                data.get<String>("indicator"),
                data.get<Double>("fontScale").toFloat()
            )

            override fun executeChange(parser: AdvancedTextParser) {
                parser.curFontScale = fontScale
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.curFontScale = parser.defaultSettings.third
            }


            override fun asOnjObject(): OnjObject = buildOnjObject {
                name("FontScale")
                "indicator" with indicator
                "fontScale" with fontScale
            }
        }

        class AdvancedActionTextEffect(
            override val indicator: String,
            private val action: AdvancedTextPart.() -> Unit,
        ) : AdvancedTextEffect {

            override val overridesOthers: Boolean = false

            constructor(data: OnjObject) : this(
                data.get<String>("indicator"),
                AdvancedTextPartActionFactory.getAction(data.get<OnjNamedObject>("action"))
            )

            override fun executeChange(parser: AdvancedTextParser) {
                parser.currentActions.add(action)
            }

            override fun backToDefault(parser: AdvancedTextParser) {
                parser.currentActions.remove(action)
            }

            override fun asOnjObject(): OnjObject = buildOnjObject {
                name("Action")
                "indicator" with indicator
                "action" with action //TODO this breaks probably, however i don't think this is ever needed
            }
        }
    }
}