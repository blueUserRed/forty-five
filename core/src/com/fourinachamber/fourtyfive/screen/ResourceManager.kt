package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.utils.AllThreadsAllowed
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.reflect.KClass
import kotlin.reflect.cast

interface ResourceBorrower

object ResourceManager {

    const val cardAtlasResourceHandle = "${Card.cardTexturePrefix}_cards_atlas"

    lateinit var resources: List<Resource>
        private set

    @MainThreadOnly
    fun borrow(borrower: ResourceBorrower, handle: String) {
        val toBorrow = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        toBorrow.borrow(borrower)
    }

    /**
     * this function can currently be used from every thread, but this may change in the future, so it should be treated
     * as `@MainThreadOnly`
     */
    @MainThreadOnly
    inline fun <reified T> get(borrower: ResourceBorrower, handle: String): T where T : Any {
        return get(borrower, handle, T::class)
    }

    fun <T> get(borrower: ResourceBorrower, handle: String, type: KClass<T>): T where T : Any {
        val toGet = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        if (borrower !in toGet.borrowedBy) {
            throw RuntimeException("resource $handle not borrowed by $borrower")
        }
        for (variant in toGet.variants) {
            if (type.isInstance(variant)) return type.cast(variant)
        }
        throw RuntimeException("no variant of type ${type.simpleName} for handle $handle")
    }

    @MainThreadOnly
    fun giveBack(borrower: ResourceBorrower, handle: String) {
        val toGiveBack = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        if (borrower !in toGiveBack.borrowedBy) {
            throw RuntimeException("resource $handle not borrowed by $borrower")
        }
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
                it.get<String>("file")
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

        assets.ifHas<OnjObject>("cards") { onj ->
            val atlasFile = onj.get<String>("atlas")
            val configFile = onj.get<String>("config")
            val cardPrefix = Card.cardTexturePrefix
            val atlasResource = AtlasResource(cardAtlasResourceHandle, atlasFile)
            val config = OnjParser.parseFile(Gdx.files.internal(configFile).file())
            cardConfigSchema.assertMatches(config)
            config as OnjObject
            val regionResources = config.get<OnjArray>("cards").value.map {
                it as OnjObject
                AtlasRegionResource(
                    "${cardPrefix}${it.get<String>("name")}",
                    it.get<String>("name"),
                    atlasResource.handle
                )
            }
            resources.add(atlasResource)
            resources.addAll(regionResources)
        }

        this.resources = resources
    }

//    @MainThreadOnly
//    fun init() {
//        val resources = mutableListOf<Resource>()
//
//        val assets = OnjParser.parseFile(Gdx.files.internal(assetsFile).file())
//        assetsSchema.assertMatches(assets)
//        assets as OnjObject
//
//        assets.get<OnjArray>("textures").value.forEach {
//            it as OnjObject
//            val file = it.get<String>("file")
//            val resource = Resource(it.get<String>("name")) {
//                val texture = Texture(Gdx.files.internal(file))
//                val region = TextureRegion(texture)
//                disposables = listOf(texture)
//                variants = listOf(texture, region, TextureRegionDrawable(region))
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("fonts").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val (font, disposable) = OnjReaderUtils.readDistanceFieldFont(it)
//                disposables = listOf(font, disposable)
//                variants = listOf(font)
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("textureAtlases").value.forEach { obj ->
//            obj as OnjObject
//            val name = obj.get<String>("name")
//            val file = obj.get<String>("file")
//            val atlasResource = Resource(
//                handle = name,
//            ) {
//                val atlas = TextureAtlas(Gdx.files.internal(file))
//                disposables = listOf(atlas)
//                variants = listOf(atlas)
//            }
//            val regionResources = obj.get<OnjArray>("regions").value.map {
//                it as OnjObject
//                val handle = it.get<String>("handle")
//                val regionName = it.get<String>("regionName")
//                AtlasRegionResource(handle, regionName, name)
//            }
//            resources.add(atlasResource)
//            resources.addAll(regionResources)
//        }
//
//        assets.get<OnjArray>("cursors").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val cursor = OnjReaderUtils.readCursor(it)
//                variants = listOf(cursor)
//                disposables = listOf(cursor)
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("shaders").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val shader = OnjReaderUtils.readShader(it)
//                variants = listOf(shader)
//                disposables = listOf(shader)
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("colorTextures").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val colorPixmap = OnjReaderUtils.readColorPixmap(it)
//                val texture = Texture(colorPixmap)
//                val textureRegion = TextureRegion(texture)
//                variants = listOf(colorPixmap, texture, textureRegion, TextureRegionDrawable(textureRegion))
//                disposables = listOf(colorPixmap, texture)
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("particles").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val particle = OnjReaderUtils.readParticleEffect(it)
//                variants = listOf(particle)
//                disposables = listOf(particle)
//            }
//            resources.add(resource)
//        }
//
//        assets.get<OnjArray>("ninepatches").value.forEach {
//            it as OnjObject
//            val resource = Resource(it.get<String>("name")) {
//                val (ninepatch, texture) = OnjReaderUtils.readNinepatch(it)
//                variants = listOf(ninepatch, NinePatchDrawable(ninepatch))
//                disposables = listOf(texture)
//            }
//            resources.add(resource)
//        }
//
//        assets.ifHas<OnjObject>("cards") { onj ->
//            val atlasFile = onj.get<String>("atlas")
//            val configFile = onj.get<String>("config")
//            val cardPrefix = Card.cardTexturePrefix
//            val atlasResource = Resource(
//                cardAtlasResourceHandle,
//            ) {
//                val atlas = TextureAtlas(Gdx.files.internal(atlasFile))
//                disposables = listOf(atlas)
//                variants = listOf(atlas)
//            }
//            val config = OnjParser.parseFile(Gdx.files.internal(configFile).file())
//            cardConfigSchema.assertMatches(config)
//            config as OnjObject
//            val regionResources = config.get<OnjArray>("cards").value.map {
//                it as OnjObject
//                AtlasRegionResource(
//                    "${cardPrefix}${it.get<String>("name")}",
//                    it.get<String>("name"),
//                    atlasResource.handle
//                )
//            }
//            resources.add(atlasResource)
//            resources.addAll(regionResources)
//        }
//
//        this.resources = resources
//    }

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
        FourtyFiveLogger.medium(logTag, "Resources were loaded when the game closed. This could" +
                "be indicative of a memory leak. Summary:")
        FourtyFiveLogger.dump(FourtyFiveLogger.LogLevel.MEDIUM, message.toString())
    }

}
