package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Cursor
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.NinePatch
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fourtyfive.onjNamespaces.OnjColor
import com.fourinachamber.fourtyfive.screen.general.PostProcessor
import onj.value.*

/**
 * object containing utilities for reading onj files
 */
object OnjReaderUtils {

    /**
     * reads an array of textures with names
     */
    fun readTextures(onj: OnjArray): Map<String, TextureRegion> = onj
        .value
        .map { it as OnjObject }
        .map { it.get<String>("name") to Texture(it.get<String>("file")) }
        .associate { it.first to TextureRegion(it.second) }


    /**
     * reads an array of cursors with names
     */
    fun readCursors(onj: OnjArray): Map<String, Cursor> {
        val cursors = mutableMapOf<String, Cursor>()
        onj.value.forEach {
            it as OnjObject
            val cursorPixmap = Pixmap(Gdx.files.internal(it.get<String>("file")))
            val pixmap = Pixmap(cursorPixmap.width, cursorPixmap.height, Pixmap.Format.RGBA8888)
            pixmap.drawPixmap(cursorPixmap, 0, 0)
            cursorPixmap.dispose()
            val hotspotX = it.get<Long>("hotspotX").toInt()
            val hotspotY = it.get<Long>("hotspotY").toInt()
            val cursor = Gdx.graphics.newCursor(pixmap, hotspotX, hotspotY)
            pixmap.dispose()
            cursors[it.get<String>("name")] = cursor
        }
        return cursors
    }

    /**
     * reads an array of atlases and their texture-regions
     * @return a map of the textureRegions of every atlas and a list of all atlases
     */
    fun readAtlases(onj: OnjArray): Pair<Map<String, TextureRegion>, List<TextureAtlas>> {
        val atlases = mutableListOf<TextureAtlas>()
        val textures = mutableMapOf<String, TextureRegion>()
        onj
            .value
            .forEach { atlasOnj ->
                atlasOnj as OnjObject
                val atlas = TextureAtlas(atlasOnj.get<String>("file"))
                atlases.add(atlas)
                atlasOnj
                    .get<OnjArray>("defines")
                    .value
                    .map { (it as OnjString).value }
                    .forEach {
                        textures[it] = atlas.findRegion(it) ?: run {
                            throw RuntimeException("unknown texture name in atlas: $it")
                        }
                    }
            }
        return textures to atlases
    }

    /**
     * reads an array of fonts with names
     */
    fun readFonts(onj: OnjArray): Map<String, BitmapFont> = onj
        .value
        .map { it as OnjNamedObject }
        .associate {
            when (it.name) {
                "BitmapFont" -> it.get<String>("name") to readBitmapFont(it)
                "FreeTypeFont" -> it.get<String>("name") to readFreeTypeFont(it)
                "DistanceFieldFont" -> it.get<String>("name") to readDistanceFieldFont(it)
                else -> throw RuntimeException("Unknown font type ${it.name}")
            }
        }

    /**
     * reads an array of postProcessors with names
     */
    fun readPostProcessors(onj: OnjArray): Map<String, PostProcessor> = onj
        .value
        .map { it as OnjObject }
        .associate {
            it.get<String>("name") to readPostProcessor(it)
        }

    /**
     * reads a single BitmapFont
     */
    fun readBitmapFont(it: OnjNamedObject): BitmapFont {
        val font = BitmapFont(Gdx.files.internal(it.get<String>("file")))
        font.setUseIntegerPositions(false)
        return font
    }

    /**
     * reads a single FreeTypeFont
     */
    fun readFreeTypeFont(fontOnj: OnjNamedObject): BitmapFont {
        val generator = FreeTypeFontGenerator(Gdx.files.internal(fontOnj.get<String>("file")))
        val parameter = FreeTypeFontGenerator.FreeTypeFontParameter()
        parameter.size = fontOnj.get<Long>("size").toInt()
        val font = generator.generateFont(parameter)
        generator.dispose()
        font.setUseIntegerPositions(false)
        return font
    }

    /**
     * reads a single distanceFieldFont
     */
    fun readDistanceFieldFont(fontOnj: OnjNamedObject): BitmapFont {
        val texture = Texture(Gdx.files.internal(fontOnj.get<String>("imageFile")), true)
        val useMipMapLinearLinear = fontOnj.getOr("useMipMapLinearLinear", false)
        texture.setFilter(
            if (useMipMapLinearLinear) Texture.TextureFilter.MipMapLinearLinear else Texture.TextureFilter.MipMapLinearNearest,
            Texture.TextureFilter.Linear
        )
        val font = BitmapFont(Gdx.files.internal(fontOnj.get<String>("fontFile")), TextureRegion(texture), false)
        font.setUseIntegerPositions(false)
        font.color = Color.valueOf(fontOnj.getOr("color", "0000ff"))
        return font
    }

    /**
     * reads an array of pixmaps with names
     */
    fun readPixmaps(onj: OnjArray): Map<String, Pixmap> = onj
        .value
        .map { it as OnjObject }
        .associate { it.get<String>("name") to Pixmap(Gdx.files.internal(it.get<String>("file"))) }

    /**
     * reads a single postProcessor
     */
    fun readPostProcessor(onj: OnjObject): PostProcessor {
        val shader = ShaderProgram(
            Gdx.files.internal(onj.get<String>("vertexShader")),
            Gdx.files.internal(onj.get<String>("fragmentShader"))
        )
        if (!shader.isCompiled) throw RuntimeException(shader.log)
        val uniformsToBind = onj.get<OnjArray>("uniforms").value.map { it.value as String }

        val timeOffset = onj.getOr<Long>("timeOffset", 0).toInt()

        val args: Map<String, Any?> = if (!onj["args"]!!.isNull()) {
            val map = mutableMapOf<String, Any?>()

            val argsOnj = onj.get<OnjObject>("args")
//            onj.get<OnjObject>("args").value

//            for ((key, value)  in argsOnj.value) {
//                map[key] = value.value
//            }
//
            for ((key, value)  in argsOnj.value) when (value) {

                is OnjFloat -> map[key] = value.value.toFloat()
                is OnjInt -> map[key] = value.value.toInt()
                is OnjColor -> map[key] = value.value

                else -> throw RuntimeException("binding type ${value::class.simpleName} as a uniform" +
                        " is currently not supported")
            }

            map
        } else mapOf()

        return PostProcessor(shader, uniformsToBind, args, timeOffset)
    }

    /**
     * reads an array of single-color textures with names
     */
    fun readColorTextures(textureArr: OnjArray): Map<String, TextureRegion> {
        val textures = mutableMapOf<String, TextureRegion>()
        textureArr
            .value
            .forEach {
                it as OnjObject
                val colorPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
                colorPixmap.setColor(it.get<Color>("color"))
                colorPixmap.fill()
                textures[it.get<String>("name")] = TextureRegion(Texture(colorPixmap))
//                colorPixmap.dispose()
            }
        return textures
    }

    /**
     * reads an array of animations with names
     */
    fun readAnimations(anims: OnjArray): Map<String, FrameAnimation> = anims
        .value
        .associate {
            it as OnjObject
            val name = it.get<String>("name")
            name to readAnimation(it)
        }

    /**
     * reads a single animation
     */
    fun readAnimation(onj: OnjObject): FrameAnimation {
        val atlas = TextureAtlas(Gdx.files.internal(onj.get<String>("atlasFile")))

        val frames: Array<Drawable> = if (onj.hasKey<OnjArray>("frames")) {
            val framesOnj = onj.get<OnjArray>("frames").value
            Array(framesOnj.size) { TextureRegionDrawable(atlas.findRegion(framesOnj[it].value as String)) }
        } else {
            // there has to be an easier way
            val framesMap = mutableMapOf<Int, Drawable>()
            for (region in atlas.regions) {
                val index = try {
                    Integer.parseInt(region.name)
                } catch (e: java.lang.NumberFormatException) {
                    continue
                }
                if (framesMap.containsKey(index)) throw RuntimeException("duplicate frame number: $index")
                framesMap[index] = TextureRegionDrawable(region)
            }
            framesMap
                .toList()
                .sortedBy { it.first }
                .map { it.second }
                .toTypedArray()
        }
        val initialFrame = onj.get<Long>("initialFrame").toInt()
        val frameTime = onj.get<Long>("frameTime").toInt()
        if (frameTime == 0) throw RuntimeException("frameTime can not be zero")
        return FrameAnimation(frames, atlas.textures, initialFrame, frameTime)
    }

    fun readParticles(particles: OnjArray): Map<String, ParticleEffect> = particles
        .value
        .associate {
            it as OnjObject
            val effect = ParticleEffect()
            effect.load(
                Gdx.files.internal(it.get<String>("particlePath")),
                Gdx.files.internal(it.get<String>("textureDir"))
            )
            effect.scaleEffect(it.get<Double>("scale").toFloat())
            it.get<String>("name") to effect
        }

    fun readNinepatches(arr: OnjArray): Pair<List<Texture>, Map<String, NinePatchDrawable>> {
        val textures = mutableListOf<Texture>()
        val ninepatches  = mutableMapOf<String, NinePatchDrawable>()
        for (obj in arr.value) {
            obj as OnjObject
            val texture = Texture(Gdx.files.internal(obj.get<String>("file")))
            textures.add(texture)
            val ninepatch = NinePatch(
                TextureRegion(texture),
                obj.get<Long>("left").toInt(),
                obj.get<Long>("right").toInt(),
                obj.get<Long>("top").toInt(),
                obj.get<Long>("bottom").toInt(),
            )
            obj.ifHas<Double>("scale") {
                ninepatch.scale(it.toFloat(), it.toFloat())
            }
            ninepatches[obj.get<String>("name")] = NinePatchDrawable(ninepatch)
        }
        return textures to ninepatches
    }
}
