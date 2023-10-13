package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.AdvancedText
import com.fourinachamber.fortyfive.screen.general.AdvancedTextPart
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.TextAdvancedTextPart
import onj.value.OnjObject

class AdvancedTextParser(val code: String, private val screen: OnjScreen, defaults: OnjObject) {

    private var next = 0

    private val currentText: StringBuilder = StringBuilder()
    private val parts: MutableList<AdvancedTextPart> = mutableListOf()

    private val defaultFontName: String = defaults.get<String>("font")
    private val defaultFont: BitmapFont = ResourceManager.get(screen, defaultFontName)
    private val defaultColor: Color = defaults.get<Color>("color")
    private val defaultFontScale = defaults.get<Double>("fontScale").toFloat()

    fun parse(): AdvancedText {
        while (!end()) {
            nextChar()
        }

        return AdvancedText(parts)
    }

    private fun nextChar() {
        val c = consume()
        currentText.append(c)
        if (c.isWhitespace()) finishText()
    }

    private fun finishText() {
        var text = currentText.toString()
        val breakLine = text.endsWith("\n") || text.endsWith("\r")
        if (breakLine) text = text.trimEnd('\n', '\r')
        parts.add(TextAdvancedTextPart(text, defaultFont, defaultColor, defaultFontScale, screen, breakLine))
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

}