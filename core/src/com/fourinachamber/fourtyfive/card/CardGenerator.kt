package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.PixmapPackerIO
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fourtyfive.utils.OnjReaderUtils
import kotlinx.coroutines.*
import onj.*

/**
 * generates an atlas containing the cards from a config file
 */
class CardGenerator(private val config: FileHandle) : Disposable {

    private lateinit var onj: OnjObject
    private lateinit var outputFile: FileHandle
    private lateinit var pixmaps: Map<String, Pixmap>
    private lateinit var baseImage: Pixmap
    private lateinit var fonts: Map<String, CustomFont>

    private var wasPrepared: Boolean = false

    /**
     * reads the config file and loads all textures. must be called before [generateCards]
     */
    fun prepare() {

        if (wasPrepared) return

        val onj = OnjParser.parseFile(config.file())
        schema.assertMatches(onj)
        onj as OnjObject
        this.onj = onj

        outputFile = Gdx.files.getFileHandle(onj.get<String>("outputFile"), Files.FileType.Local)

        pixmaps = OnjReaderUtils.readPixmaps(onj.get<OnjObject>("assets").get<OnjArray>("pixmaps"))

        baseImage = pixmapOrError(onj.get<String>("baseImage"))

        fonts = onj
            .get<OnjObject>("assets")
            .get<OnjArray>("fonts")
            .value
            .associate {
                it as OnjObject
                it.get<String>("name") to CustomFont(Gdx.files.internal(it.get<String>("file")))
            }

        wasPrepared = true
    }

    /**
     * generates the cards and disposes the textures. [prepare] must be called before.
     */
    fun generateCards() {

        if (!wasPrepared) throw RuntimeException("CardGenerator must be prepared before generateCards() is called")

        val cards = onj.get<OnjArray>("cards").value.map { it as OnjObject }
        val packerOnj = onj.get<OnjObject>("packer")

        val packer = PixmapPacker(
            packerOnj.get<Long>("pageWidth").toInt(),
            packerOnj.get<Long>("pageHeight").toInt(),
            Pixmap.Format.RGBA8888,
            packerOnj.get<Long>("padding").toInt(),
            true,
            PixmapPacker.SkylineStrategy()
        )

        runBlocking {
            CoroutineScope(Dispatchers.Default).launch {

                val deferreds = cards.map {
                    async { generateCard(it) }
                }.toTypedArray()

                val cardPixmaps = awaitAll(*deferreds)
                cards.indices.forEach { packer.pack(cards[it].get<String>("name"), cardPixmaps[it]) }
            }.join()
        }


        PixmapPackerIO().save(outputFile, packer)
        packer.dispose()

        wasPrepared = false

    }

    private fun generateCard(card: OnjObject): Pixmap {
        val pixmap = Pixmap(baseImage.width, baseImage.height, Pixmap.Format.RGBA8888)
        pixmap.drawPixmap(baseImage, 0, 0)
        card.get<OnjArray>("elements").value.forEach { drawElement(pixmap, it as OnjNamedObject) }
        return pixmap
    }


    private fun drawElement(pixmap: Pixmap, element: OnjNamedObject) = when (element.name) {

        "TextElement" -> {
            val fontName = element.get<String>("font")
            val font = fontOrError(fontName)
            val text = element.get<String>("text")
            val scale = element.get<Double>("fontScale").toFloat()
            val x = element.get<Long>("x").toInt()
            val y = element.get<Long>("y").toInt()
            font.write(pixmap, text, x, y, scale)
        }

        "ImageElement" -> {
            val name = element.get<String>("textureName")
            val pixmapToDraw = pixmapOrError(name)
            val scaleX = element.get<Double>("scaleX")
            val scaleY = element.get<Double>("scaleY")
            pixmap.drawPixmap(
                pixmapToDraw,
                0, 0,
                pixmapToDraw.width,
                pixmapToDraw.height,
                element.get<Long>("x").toInt(),
                element.get<Long>("y").toInt(),
                (pixmapToDraw.width * scaleX).toInt(),
                (pixmapToDraw.height * scaleY).toInt(),
            )
        }

        "RectangleElement" -> {
            val width = element.get<Long>("width").toInt()
            val height = element.get<Long>("height").toInt()
            val x = element.get<Long>("x").toInt()
            val y = element.get<Long>("y").toInt()
            val color = Color.valueOf(element.get<String>("color"))
            val strokeSize = element.get<Long>("strokeSize").toInt()

            pixmap.setColor(color)
            pixmap.fillRectangle(x, y, width, strokeSize)
            pixmap.fillRectangle(x + width - strokeSize, y, strokeSize, height)
            pixmap.fillRectangle(x, y + height - strokeSize, width, strokeSize)
            pixmap.fillRectangle(x, y, strokeSize, height)
        }

        else -> throw RuntimeException("unknown element: ${element.name}")

    }

    private fun pixmapOrError(name: String): Pixmap {
        return pixmaps[name] ?: throw RuntimeException("unknown pixmap name: $name")
    }

    private fun fontOrError(name: String): CustomFont {
        return fonts[name] ?: throw RuntimeException("unknown font name: $name")
    }

    override fun dispose() {
        pixmaps.values.forEach(Pixmap::dispose)
    }


    companion object {

        const val schemaPath = "onjschemas/card_generator_config.onjschema"

        val schema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }


    /**
     * libgdx's BitmapFont uses the Texture-class, which doesn't work on a non-openGL thread, so I had to write a custom
     * Font class which was a great experience and a lot of Fun. I love multithreading in libgdx!
     */
    class CustomFont(fntFile: FileHandle) : Disposable {

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
        fun write(on: Pixmap, text: String, x: Int, y: Int, scale: Float = 1f) {
            var curX = x
            var curY = y
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

}