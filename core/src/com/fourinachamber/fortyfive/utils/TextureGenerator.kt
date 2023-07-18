package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Files
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.PixmapPacker
import com.badlogic.gdx.graphics.g2d.PixmapPackerIO
import com.badlogic.gdx.utils.Disposable
import kotlinx.coroutines.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

/**
 * generates an atlas containing the cards from a config file
 */
class TextureGenerator @AllThreadsAllowed constructor(private val config: FileHandle) : Disposable {

    private lateinit var onj: OnjObject
    private lateinit var outputFile: FileHandle
    private lateinit var pixmaps: Map<String, Pixmap>
    private lateinit var baseImage: Pixmap
    private lateinit var fonts: Map<String, PixmapFont>
    private var packerConfig: OnjObject? = null

    private var wasPrepared: Boolean = false

    /**
     * reads the config file and loads all textures. must be called before [generate]
     */
    @AllThreadsAllowed
    fun prepare() {

        if (wasPrepared) return

        val onj = OnjParser.parseFile(config.file())
        schema.assertMatches(onj)
        onj as OnjObject
        this.onj = onj

        val output = onj.get<OnjNamedObject>("output")
        when (output.name) {
            "Packer" -> {
                outputFile = Gdx.files.getFileHandle(output.get<String>("outputFile"), Files.FileType.Local)
                packerConfig = output
            }

            "Files" -> {
                outputFile = Gdx.files.getFileHandle(output.get<String>("outputDirectory"), Files.FileType.Local)
            }
        }

        pixmaps = readPixmaps(onj.get<OnjObject>("assets").get<OnjArray>("pixmaps"))

        baseImage = pixmapOrError(onj.get<String>("baseImage"))

        fonts = onj
            .get<OnjObject>("assets")
            .get<OnjArray>("fonts")
            .value
            .associate {
                it as OnjObject
                it.get<String>("name") to PixmapFont(Gdx.files.internal(it.get<String>("file")))
            }

        wasPrepared = true
    }

    private fun readPixmaps(onj: OnjArray): Map<String, Pixmap> = onj
        .value
        .map { it as OnjObject }
        .associate { it.get<String>("name") to Pixmap(Gdx.files.internal(it.get<String>("file"))) }


    /**
     * generates the cards and disposes the textures. [prepare] must be called before.
     */
    @AllThreadsAllowed
    fun generate() {

        if (!wasPrepared) throw RuntimeException("CardGenerator must be prepared before generateCards() is called")

        val textures = onj.get<OnjArray>("textures").value.map { it as OnjObject }

        // TODO: this is kinda chaotic
        runBlocking {
            CoroutineScope(Dispatchers.Default).launch {

                val deferreds = textures.map {
                    async {
                        val card = generateCard(it)
                        if (packerConfig == null) {
                            val file = outputFile.child("${it.get<String>("name")}.png")
                            PixmapIO.writePNG(file, card)
                        }
                        card
                    }
                }.toTypedArray()

                val cardPixmaps = awaitAll(*deferreds)
                if (packerConfig != null) saveAtlas(cardPixmaps, textures)
                cardPixmaps.forEach { it.dispose() }
            }.join()
        }

        wasPrepared = false
    }

    private fun saveAtlas(pixmaps: List<Pixmap>, textures: List<OnjObject>) {
        val packerConfig = packerConfig!!
        val packer = PixmapPacker(
            packerConfig.get<Long>("pageWidth").toInt(),
            packerConfig.get<Long>("pageHeight").toInt(),
            Pixmap.Format.RGBA8888,
            packerConfig.get<Long>("padding").toInt(),
            true,
            PixmapPacker.SkylineStrategy()
        )
        textures.indices.forEach { packer.pack(textures[it].get<String>("name"), pixmaps[it]) }
        PixmapPackerIO().save(outputFile, packer)
        packer.dispose()
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
            pixmap.drawPixmap(
                pixmapToDraw,
                0, 0,
                pixmapToDraw.width,
                pixmapToDraw.height,
                element.get<Long>("x").toInt(),
                element.get<Long>("y").toInt(),
                element.get<Long>("width").toInt(),
                element.get<Long>("height").toInt(),
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

    private fun fontOrError(name: String): PixmapFont {
        return fonts[name] ?: throw RuntimeException("unknown font name: $name")
    }

    override fun dispose() {
        pixmaps.values.forEach(Pixmap::dispose)
    }


    companion object {

        const val schemaPath = "onjschemas/texture_generator_config.onjschema"

        val schema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal(schemaPath).file())
        }

    }

}