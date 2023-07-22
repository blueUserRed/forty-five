package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.utils.Disposable

/**
 * libgdx's BitmapFont can't draw on pixmaps and uses the Texture-class, which doesn't work on a non-openGL thread,
 * so I had to write a custom Font class which was a great experience and a lot of Fun. I love multithreading in libgdx!
 */
class PixmapFont @AllThreadsAllowed constructor(fntFile: FileHandle) : Disposable {

    private val pixmap: Pixmap
    private val letters: List<Letter>
    private val size: Int

    init {
        val text = fntFile.readString()
        val result = Regex("file=\"(.*?)\"").find(text)!!
        size = Integer.parseInt(Regex("size=(-?\\d+)").find(text)!!.groupValues[1])
        val imgFile = "${fntFile.parent().path()}/${result.groupValues[1]}"
        val letters = mutableListOf<Letter>()
        pixmap = Pixmap(Gdx.files.internal(imgFile))

        text
            .split('\n')
            .filter { it.startsWith("char ") }
            .forEach { line ->
                val id = Integer.parseInt(idRegex.find(line)!!.groupValues[1])
                val x = Integer.parseInt(xRegex.find(line)!!.groupValues[1])
                val y = Integer.parseInt(yRegex.find(line)!!.groupValues[1])
                val width = Integer.parseInt(widthRegex.find(line)!!.groupValues[1])
                val height = Integer.parseInt(heightRegex.find(line)!!.groupValues[1])
                val xOffset = Integer.parseInt(xOffsetRegex.find(line)!!.groupValues[1])
                val yOffset = Integer.parseInt(yOffsetRegex.find(line)!!.groupValues[1])
                val xAdvance = Integer.parseInt(xAdvanceRegex.find(line)!!.groupValues[1])

                letters.add(
                    Letter(
                        id.toChar(),
                        x, y,
                        width, height,
                        xOffset, yOffset,
                        xAdvance
                    )
                )
            }
        this.letters = letters
    }

    /**
     * writes text to a pixmap
     */
    @AllThreadsAllowed
    fun write(on: Pixmap, text: String, x: Int, y: Int, scale: Float = 1f, color: Color = Color.BLACK) {
        var curX = x
        var curY = y
        on.setColor(color)
        text.forEach { char ->

            if (char == '\n') {
                curY += (size * scale).toInt()
                curX = x
                return@forEach
            }

            val letter = getLetter(char)

            on.drawPixmap(
                pixmap,
                letter.x, letter.y,
                letter.width, letter.height,
                curX + (letter.xOffset * scale).toInt(),
                curY + (letter.yOffset * scale).toInt(),
                (letter.width * scale).toInt(), (letter.height * scale).toInt()
            )
            curX += (letter.xAdvance * scale).toInt()
        }
    }

    private fun getLetter(char: Char): Letter = letters.firstOrNull { it.char == char } ?: run {
        throw RuntimeException("letter $char not in font")
    }

    override fun dispose() {
        pixmap.dispose()
    }

    /**
     * a letter of the font
     */
    data class Letter(
        val char: Char,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val xOffset: Int,
        val yOffset: Int,
        val xAdvance: Int
    )

    private companion object {
        val idRegex = Regex("id=(-?\\d+)")
        val xRegex = Regex("x=(-?\\d+)")
        val yRegex = Regex("y=(-?\\d+)")
        val widthRegex = Regex("width=(-?\\d+)")
        val heightRegex = Regex("height=(-?\\d+)")
        val xOffsetRegex = Regex("xoffset=(-?\\d+)")
        val yOffsetRegex = Regex("yoffset=(-?\\d+)")
        val xAdvanceRegex = Regex("xadvance=(-?\\d+)")
    }

}
