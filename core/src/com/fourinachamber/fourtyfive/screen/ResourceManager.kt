package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
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
import com.fourinachamber.fourtyfive.utils.OnjReaderUtils
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

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

    @AllThreadsAllowed
    inline fun <reified T> get(borrower: ResourceBorrower, handle: String): T {
        val toGet = resources.find { it.handle == handle }
            ?: throw RuntimeException("no resource with handle $handle")
        if (borrower !in toGet.borrowedBy) {
            throw RuntimeException("resource $handle not borrowed by $borrower")
        }
        for (variant in toGet.variants) {
            if (variant is T) return variant
        }
        throw RuntimeException("no variant of type ${T::class.simpleName} for handle $handle")
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

    open class Resource(
        val handle: String,
        var variants: List<Any> = listOf(),
        var disposables: List<Disposable> = listOf(),
        val borrowedBy: MutableList<ResourceBorrower> = mutableListOf(),
        val loader: @MainThreadOnly Resource.() -> Unit
    ) : Disposable {

        var isLoaded: Boolean = false

        @MainThreadOnly
        open fun load() {
            if (isLoaded) return
            isLoaded = true
            loader()
        }

        @MainThreadOnly
        fun borrow(borrower: ResourceBorrower): Resource {
            if (!isLoaded) load()
            borrowedBy.add(borrower)
            return this
        }

        @MainThreadOnly
        open fun giveBack(borrower: ResourceBorrower) {
            if (!borrowedBy.remove(borrower)) return
            if (borrowedBy.isEmpty()) dispose()
        }

        @MainThreadOnly
        override fun dispose() {
            if (!isLoaded) return
            disposables.forEach(Disposable::dispose)
            variants = listOf()
            disposables = listOf()
            isLoaded = false
        }

        override fun toString(): String = handle
    }

    class AtlasRegionResource(
        handle: String,
        val regionName: String,
        val atlasResourceHandle: String
    ) : Resource(
        handle = handle,
        loader = atlasRegionLoader
    ), ResourceBorrower {

        @MainThreadOnly
        override fun dispose() {
            giveBack(this, atlasResourceHandle)
            super.dispose()
        }

        companion object {

            val atlasRegionLoader: Resource.() -> Unit = {
                this as AtlasRegionResource
                borrow(this, atlasResourceHandle)
                val atlas = get<TextureAtlas>(this, atlasResourceHandle)
                val region = atlas.findRegion(regionName)
                variants = listOf(region, TextureRegionDrawable(region))
            }

        }

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
            val file = it.get<String>("file")
            val resource = Resource(it.get<String>("name")) {
                val texture = Texture(Gdx.files.internal(file))
                val region = TextureRegion(texture)
                disposables = listOf(texture)
                variants = listOf(texture, region, TextureRegionDrawable(region))
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("fonts").value.forEach {
            it as OnjObject
            val resource = Resource(it.get<String>("name")) {
                val (font, disposable) = OnjReaderUtils.readDistanceFieldFont(it)
                disposables = listOf(font, disposable)
                variants = listOf(font)
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("textureAtlases").value.forEach { obj ->
            obj as OnjObject
            val name = obj.get<String>("name")
            val file = obj.get<String>("file")
            val atlasResource = Resource(
                handle = name,
            ) {
                val atlas = TextureAtlas(Gdx.files.internal(file))
                disposables = listOf(atlas)
                variants = listOf(atlas)
            }
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
            val resource = Resource(it.get<String>("name")) {
                val cursor = OnjReaderUtils.readCursor(it)
                variants = listOf(cursor)
                disposables = listOf(cursor)
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("shaders").value.forEach {
            it as OnjObject
            val resource = Resource(it.get<String>("name")) {
                val shader = OnjReaderUtils.readShader(it)
                variants = listOf(shader)
                disposables = listOf(shader)
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("colorTextures").value.forEach {
            it as OnjObject
            val resource = Resource(it.get<String>("name")) {
                val colorPixmap = OnjReaderUtils.readColorPixmap(it)
                val texture = Texture(colorPixmap)
                val textureRegion = TextureRegion(texture)
                variants = listOf(colorPixmap, texture, textureRegion, TextureRegionDrawable(textureRegion))
                disposables = listOf(colorPixmap, texture)
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("particles").value.forEach {
            it as OnjObject
            val resource = Resource(it.get<String>("name")) {
                val particle = OnjReaderUtils.readParticleEffect(it)
                variants = listOf(particle)
                disposables = listOf(particle)
            }
            resources.add(resource)
        }

        assets.get<OnjArray>("ninepatches").value.forEach {
            it as OnjObject
            val resource = Resource(it.get<String>("name")) {
                val (ninepatch, texture) = OnjReaderUtils.readNinepatch(it)
                variants = listOf(ninepatch, NinePatchDrawable(ninepatch))
                disposables = listOf(texture)
            }
            resources.add(resource)
        }

        assets.ifHas<OnjObject>("cards") { onj ->
            val atlasFile = onj.get<String>("atlas")
            val configFile = onj.get<String>("config")
            val cardPrefix = Card.cardTexturePrefix
            val atlasResource = Resource(
                cardAtlasResourceHandle,
            ) {
                val atlas = TextureAtlas(Gdx.files.internal(atlasFile))
                disposables = listOf(atlas)
                variants = listOf(atlas)
            }
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

    private val cardConfigSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/cards.onjschema").file())
    }

    private const val logTag = "ResourceManager"

    fun end() {
        for (resource in resources) {
            if (!resource.isLoaded) continue
            val message = StringBuilder("resource $resource was still loaded when the Game closed!\n")
            for (borrower in resource.borrowedBy) message.append("is borrowed by: $borrower\n")
            FourtyFiveLogger.medium(logTag, message.toString())
        }
    }

}