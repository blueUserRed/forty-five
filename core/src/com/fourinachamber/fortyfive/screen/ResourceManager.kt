package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.reflect.KClass

interface ResourceBorrower

typealias ResourceHandle = String

object ResourceManager {

    const val cardAtlasResourceHandle = "${Card.cardTexturePrefix}_cards_atlas"

    lateinit var resources: List<Resource>
        private set

    @MainThreadOnly
    fun borrow(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toBorrow = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toBorrow.borrow(borrower)
    }

    /**
     * this function can currently be used from every thread, but this may change in the future, so it should be treated
     * as `@MainThreadOnly`
     */
    @MainThreadOnly
    inline fun <reified T> get(borrower: ResourceBorrower, handle: ResourceHandle): T where T : Any {
        return get(borrower, handle, T::class)
    }

    fun <T> get(borrower: ResourceBorrower, handle: ResourceHandle, type: KClass<T>): T where T : Any {
        val toGet = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        if (borrower !in toGet.borrowedBy) {
            throw RuntimeException("resource $handle not borrowed by $borrower")
        }
        return toGet.get(type) ?: throw RuntimeException("no variant of type ${type.simpleName} for handle $handle")
    }

    fun trimPrepared() {
        resources
            .filter { it.state != Resource.ResourceState.NOT_LOADED }
            .filter { it.borrowedBy.isEmpty() }
            .forEach { it.dispose() }
    }

    @MainThreadOnly
    fun giveBack(borrower: ResourceBorrower, handle: ResourceHandle) {
        val toGiveBack = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toGiveBack.giveBack(borrower)
    }

    private const val assetsFile: String = "config/assets.onj"

    private val assetsSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/assets.onjschema").file())
    }

    @MainThreadOnly
    fun init() {
        val resources = mutableListOf<Resource>()
        val assets = OnjParser.parseFile(Gdx.files.internal(assetsFile).file())
        assetsSchema.assertMatches(assets)
        assets as OnjObject

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
