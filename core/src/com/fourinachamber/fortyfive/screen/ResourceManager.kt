package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.reflect.KClass

interface ResourceBorrower

typealias ResourceHandle = String

object ResourceManager {

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

    @MainThreadOnly
    fun giveBack(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toGiveBack = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toGiveBack.giveBack(borrower)
    }

    @MainThreadOnly
    fun init() {
        val resources = mutableListOf<Resource>()
        val assets = ConfigFileManager.getConfigFile("assets")

        assets.get<OnjArray>("textures").value.forEach {
            it as OnjObject
            resources.add(TextureResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.getOr("tileable", false),
                it.getOr("tileScale", 1.0).toFloat(),
                it.getOr("useMipMaps", false)
            ))
        }

        assets.get<OnjArray>("fonts").value.forEach {
            it as OnjObject
            resources.add(FontResource(
                it.get<String>("name"),
                it.get<String>("imageFile"),
                it.get<String>("fontFile"),
                it.getOr("markupEnabled", false)
            ))
        }

        assets.get<OnjArray>("pixmapFonts").value.forEach {
            it as OnjObject
            resources.add(PixmapFontResource(
                it.get<String>("name"),
                it.get<String>("fontFile")
            ))
        }

        assets.get<OnjArray>("textureAtlases").value.forEach { obj ->
            obj as OnjObject
            val name = obj.get<String>("name")
            val file = obj.get<String>("file")
            val atlasResource = AtlasResource(name, file)
            val regionResources = obj.get<OnjArray>("regions").value.map {
                it as OnjObject
                val handle = it.get<String>("handle")
                val regionName = it.get<String>("regionName")
                AtlasRegionResource(handle, regionName, name)
            }
            resources.add(atlasResource)
            resources.addAll(regionResources)
        }

        assets.get<OnjArray>("cursors").value.forEach {
            it as OnjObject
            resources.add(CursorResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("hotspotX").toInt(),
                it.get<Long>("hotspotY").toInt()
            ))
        }

        assets.get<OnjArray>("shaders").value.forEach {
            it as OnjObject
            resources.add(ShaderResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<OnjObject>("constantArgs").value.entries.associate { (key, value) ->
                    "ca_$key" to value.value as Any
                }
            ))
        }

        assets.get<OnjArray>("colorTextures").value.forEach {
            it as OnjObject
            resources.add(ColorTextureResource(
                it.get<String>("name"),
                it.get<Color>("color")
            ))
        }

        assets.get<OnjArray>("particles").value.forEach {
            it as OnjObject
            resources.add(ParticleResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<String>("textureDir"),
                it.get<Double>("scale").toFloat()
            ))
        }

        assets.get<OnjArray>("ninepatches").value.forEach {
            it as OnjObject
            resources.add(NinepatchResource(
                it.get<String>("name"),
                it.get<String>("file"),
                it.get<Long>("left").toInt(),
                it.get<Long>("right").toInt(),
                it.get<Long>("top").toInt(),
                it.get<Long>("bottom").toInt(),
                it.getOr("scale", 1.0).toFloat()
            ))
        }

        assets.get<OnjArray>("frameAnimations").value.forEach {
            it as OnjObject
            resources.add(DeferredFrameAnimationResource(
                it.get<String>("name"),
                it.get<String>("preview"),
                it.get<String>("atlas"),
                it.get<Long>("frameTime").toInt()
            ))
        }

        assets.get<OnjArray>("sounds").value.forEach {
            it as OnjObject
            resources.add(SoundResource(
                it.get<String>("name"),
                it.get<String>("file")
            ))
        }

        assets.get<OnjArray>("music").value.forEach {
            it as OnjObject
            resources.add(MusicResource(
                it.get<String>("name"),
                it.get<String>("file")
            ))
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
    }

    private val cardConfigSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/cards.onjschema").file())
    }

    private const val logTag = "ResourceManager"

    fun end() {
        val message = StringBuilder()
        for (resource in resources) {
            if (resource.handle.startsWith(Card.cardTexturePrefix)) continue
            if (resource.state == Resource.ResourceState.NOT_LOADED) continue
            message.append("resource $resource was still loaded when the Game closed!\n")
            for (borrower in resource.borrowedBy) message.append("is borrowed by: $borrower\n")
        }
        if (message.isEmpty()) return
        FortyFiveLogger.warn(logTag, "Resources were loaded when the game closed. This could " +
                "be indicative of a memory leak. Summary:")
        FortyFiveLogger.dump(FortyFiveLogger.LogLevel.MEDIUM, message.toString())
    }

}
