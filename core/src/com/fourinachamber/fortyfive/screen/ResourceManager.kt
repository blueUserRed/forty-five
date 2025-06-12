package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.reflect.KClass
import kotlin.system.exitProcess

interface ResourceBorrower

typealias ResourceHandle = String

class ResourceManager {

    lateinit var resources: List<Resource>
        private set

    inline fun <reified T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, handle: ResourceHandle) =
        forceGet(borrower, lifetime, handle, T::class)

    fun forceResolve(promise: Promise<*>) {
        val resource = resources.find { it.promiseMatches(promise) }
            ?: throw RuntimeException("no resource with matching promise found")
        resource.forceResolve()
    }

    fun <T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, handle: ResourceHandle, type: KClass<T>): T {
        val resource = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        return resource.forceGet(borrower, lifetime, type)
    }

    inline fun <reified T : Any> request(
        borrower: ResourceBorrower,
        lifetime: Lifetime,
        handle: ResourceHandle
    ): Promise<T> =
        request(borrower, lifetime, handle, T::class)

    fun <T : Any> request(
        borrower: ResourceBorrower,
        lifetime: Lifetime,
        handle: ResourceHandle,
        type: KClass<T>
    ): Promise<T> {
        val resource = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        return resource.request(borrower, lifetime, type)
    }

    fun giveBack(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toGiveBack = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toGiveBack.giveBack(borrower)
    }

    fun init() {
        val resources = mutableListOf<Resource>()
        val assets = ConfigFileManager.getConfigFile("assets")

        val dropShadowsToDraw = mutableListOf<Pair<String, Color>>()

        assets.get<OnjArray>("textures").value.forEach {
            it as OnjObject
            val name = it.get<String>("name")
            val resource = TextureResource(
                name,
                it.get<String>("file"),
                it.getOr("tileable", false),
                it.getOr("tileScale", 1.0).toFloat(),
                it.getOr("useMipMaps", false)
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)

            val color = it.get<Color?>("dropShadowColor") ?: return@forEach
            val resourceDropShadow = TextureResource(
                name + DROP_SHADOW_END,
                "drop_shadows/$name$DROP_SHADOW_END.png",
                false,
                1f,
                false,
            )
            resourceDropShadow.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resourceDropShadow)
            if (FortyFive.createDropShadows)
                dropShadowsToDraw.add(name to color)
        }

        assets.get<OnjArray>("fonts").value.forEach {
            it as OnjObject
            val resource = FontResource(
                it.get<String>("name"),
                it.get<String>("imageFile"),
                it.get<String>("fontFile"),
                it.getOr("markupEnabled", false)
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("pixmapFonts").value.forEach {
            it as OnjObject
            val resource = PixmapFontResource(
                it.get<String>("name"),
                it.get<String>("fontFile")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("textureAtlases").value.forEach { obj ->
            obj as OnjObject
            val name = obj.get<String>("name")
            val file = obj.get<String>("file")
            val atlasResource = AtlasResource(name, file)
            atlasResource.stayLoaded = obj.getOr("stayLoaded", false)
            val regionResources = obj.get<OnjArray>("regions").value.map {
                it as OnjObject
                val handle = it.get<String>("handle")
                val regionName = it.get<String>("regionName")
                val resource = AtlasRegionResource(handle, regionName, name)
                resource.stayLoaded = it.getOr("stayLoaded", false)
                resource
            }
            resources.add(atlasResource)
            resources.addAll(regionResources)
        }

        assets.get<OnjArray>("cursors").value.forEach {
            it as OnjObject
            val resource = CursorResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("hotspotX").toInt(),
                it.get<Long>("hotspotY").toInt()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("shaders").value.forEach {
            it as OnjObject
            val resource = ShaderResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<OnjObject>("constantArgs").value.entries.associate { (key, value) ->
                    "ca_$key" to value.value as Any
                }
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("colorTextures").value.forEach {
            it as OnjObject
            val resource = ColorTextureResource(
                it.get<String>("name"),
                it.get<Color>("color")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("particles").value.forEach {
            it as OnjObject
            val resource = ParticleResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<String>("textureDir"),
                it.get<Double>("scale").toFloat()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("ninepatches").value.forEach {
            it as OnjObject
            val resource = NinepatchResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("left").toInt(),
                it.get<Long>("right").toInt(),
                it.get<Long>("top").toInt(),
                it.get<Long>("bottom").toInt(),
                it.getOr("scale", 1.0).toFloat()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("frameAnimations").value.forEach {
            it as OnjObject
            val resource = DeferredFrameAnimationResource(
                it.get<String>("name"),
                it.get<String>("preview"),
                it.get<String>("atlas"),
                it.get<Long>("frameTime").toInt()
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("sounds").value.forEach {
            it as OnjObject
            val resource = SoundResource(
                it.get<String>("name"),
                it.get<String>("file")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        assets.get<OnjArray>("music").value.forEach {
            it as OnjObject
            val resource = MusicResource(
                it.get<String>("name"),
                it.get<String>("file")
            )
            resource.stayLoaded = it.getOr("stayLoaded", false)
            resources.add(resource)
        }

        val cardsFile = assets.access<String>(".cards.directory")
        Gdx.files.internal(cardsFile)
            .file()
            .walk()
            .filter { it.isFile }
            .forEach {
                resources.add(TextureResource(
                    "${Card.cardTexturePrefix}${it.nameWithoutExtension}",
                    it.path,
                    false,
                    1f,
                    false
                ))
            }

        this.resources = resources
        createDropShadows(dropShadowsToDraw)
        if (FortyFive.createDropShadows) exitProcess(0)
    }

    fun createDropShadows(dropShadowsToDraw: List<Pair<String, Color>>) {
        if (dropShadowsToDraw.isEmpty()) return
        val borrower = object : ResourceBorrower {}
        val dropShadowShader = forceGet<BetterShader>(borrower, Lifetime.endless, "drop_shadow_shader")

        val batch = SpriteBatch()

        batch.begin()
        batch.setBlendFunction(-1, -1)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE)
        val shader = dropShadowShader
        batch.shader = shader.shader
        val multiplier = 0.3f
        shader.shader.setUniformf("u_multiplier", multiplier)
        shader.shader.setUniformf("u_maxOpacity", 0.3f)
        val scale = (1 + multiplier)
        val maxWidth = Gdx.app.graphics.width.toFloat() - 100f
        val maxHeight = Gdx.app.graphics.height.toFloat() - 100f
        dropShadowsToDraw.forEach {
            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL_COLOR_BUFFER_BIT)

            val drawable = forceGet<Drawable>(borrower, Lifetime.endless, it.first)
            shader.shader.setUniformf("u_color", it.second)
            val prefWidth = drawable.minWidth * scale
            val prefHeight = drawable.minHeight * scale

            val aspectRatio = prefWidth / prefHeight
            var width = prefWidth
            var height = prefHeight
            if (prefWidth > maxWidth) {
                width = maxWidth
                height = width / aspectRatio
            }
            if (prefHeight > maxHeight) {
                height = maxHeight
                width = height * aspectRatio
            }

            drawable.draw(batch, 0f, 0f, width, height)
            batch.flush()

            val pixels = ScreenUtils.getFrameBufferPixels(0, 0, width.toInt(), height.toInt(), true)
            val pixmap = Pixmap(width.toInt(), height.toInt(), Pixmap.Format.RGBA8888)
            BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.size)
            PixmapIO.writePNG(FileHandle(File("drop_shadows/${it.first}$DROP_SHADOW_END.png")), pixmap)
            pixmap.dispose()
            giveBack(borrower, it.first)
            FortyFive.logger.debug(logTag, "Created DropShadow for ${it.first}")
        }
        batch.end()
        giveBack(borrower, "drop_shadow_shader")
    }



    fun end() {
        val message = StringBuilder()
        for (resource in resources) {
            if (resource.handle.startsWith(Card.cardTexturePrefix)) continue
            if (resource.state == Resource.ResourceState.NOT_LOADED) continue
            message.append("resource $resource was still loaded when the Game closed!\n")
            for (borrower in resource.borrowedBy) message.append("is borrowed by: $borrower\n")
        }
        if (message.isEmpty()) return
        FortyFive.logger.warn(logTag, "Resources were loaded when the game closed. This could " +
                "be indicative of a memory leak. Summary:")
        FortyFive.logger.dump(FortyFiveLogger.LogLevel.MEDIUM, message.toString())
    }

    companion object {
        const val DROP_SHADOW_END = "_drop_shadow"
        private const val logTag = "ResourceManager"
    }

}
