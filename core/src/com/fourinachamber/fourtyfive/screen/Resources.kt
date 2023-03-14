package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fourtyfive.rendering.BetterShaderPreProcessor
import com.fourinachamber.fourtyfive.utils.AllThreadsAllowed
import com.fourinachamber.fourtyfive.utils.Either
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import kotlin.reflect.KClass
import kotlin.reflect.cast


abstract class Resource(
    val handle: String,
    var variants: List<Any> = listOf(),
    var disposables: List<Disposable> = listOf(),
    val borrowedBy: MutableList<ResourceBorrower> = mutableListOf()
) : Disposable {

    var state: ResourceState = ResourceState.NOT_LOADED
        protected set

    @MainThreadOnly
    protected abstract fun loadDirectMainThread()

    @AllThreadsAllowed
    protected abstract fun prepareLoadingAllThreads()

    @MainThreadOnly
    protected abstract fun finishLoadingMainThread()

    @MainThreadOnly
    fun load() = synchronized(this) {
        if (state == ResourceState.NOT_LOADED) {
            loadDirectMainThread()
        } else if (state == ResourceState.PREPARED) {
            finishLoadingMainThread()
        }
        state = ResourceState.LOADED
    }

    @MainThreadOnly
    fun <T> get(variantType: KClass<T>): T? where T : Any = synchronized(this) {
        load()
        for (variant in variants) if (variantType.isInstance(variant)) return variantType.cast(variant)
        return null
    }

    @AllThreadsAllowed
    fun prepare() = synchronized(this) {
        if (state != ResourceState.NOT_LOADED) return
        prepareLoadingAllThreads()
        state = ResourceState.PREPARED
    }

    @MainThreadOnly
    fun borrow(borrower: ResourceBorrower): Resource = synchronized(this) {
        borrowedBy.add(borrower)
        return this
    }

    @MainThreadOnly
    open fun giveBack(borrower: ResourceBorrower) {
        if (!borrowedBy.remove(borrower)) return
        if (borrowedBy.isEmpty()) dispose()
    }

    @MainThreadOnly
    override fun dispose() = synchronized(this) {
        disposables.forEach(Disposable::dispose)
        variants = listOf()
        disposables = listOf()
        state = ResourceState.NOT_LOADED
    }

    override fun toString(): String = handle

    enum class ResourceState {
        NOT_LOADED, PREPARED, LOADED
    }

}

class TextureResource(
    handle: String,
    private val file: String
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override fun loadDirectMainThread() {
        val texture = Texture(Gdx.files.internal(file))
        val region = TextureRegion(texture)
        disposables = listOf(texture)
        variants = listOf(texture, region, TextureRegionDrawable(region))
    }

    override fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap)
        val region = TextureRegion(texture)
        disposables = listOf(texture)
        variants = listOf(texture, region, TextureRegionDrawable(region))
    }

    override fun dispose() {
        pixmap?.dispose()
        pixmap = null
        super.dispose()
    }

}

class FontResource(
    handle: String,
    private val imageFile: String,
    private val fontFile: String,
    private val markupEnabled: Boolean,
) : Resource(handle) {

    private var pixmap: Pixmap? = null
    private var fontData: BitmapFontData? = null

    override fun loadDirectMainThread() {
        val texture = Texture(Gdx.files.internal(imageFile), true)
        val font = BitmapFont(Gdx.files.internal(fontFile), TextureRegion(texture), false)
        texture.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.Linear)
        font.setUseIntegerPositions(false)
        font.color = Color.WHITE
        font.data.markupEnabled = markupEnabled
        disposables = listOf(texture, font)
        variants = listOf(font)
    }

    override fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(imageFile))
        fontData = BitmapFontData(Gdx.files.internal(fontFile), false)
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap, true)
        val font = BitmapFont(fontData!!, TextureRegion(texture), false)
        texture.setFilter(Texture.TextureFilter.MipMapLinearNearest, Texture.TextureFilter.Linear)
        font.setUseIntegerPositions(false)
        font.color = Color.WHITE
        font.data.markupEnabled = markupEnabled
        disposables = listOf(texture, font)
        variants = listOf(font)
    }

    override fun dispose() {
        pixmap?.dispose()
        pixmap = null
        fontData = null
        super.dispose()
    }
}

class AtlasResource(
    handle: String,
    private val file: String
) : Resource(handle) {

    private var data: TextureAtlasData? = null
    private var pages: MutableMap<String, Pixmap>? = null

    override fun loadDirectMainThread() {
        val atlas = TextureAtlas(Gdx.files.internal(file))
        variants = listOf(atlas)
        disposables = listOf(atlas)
    }

    override fun prepareLoadingAllThreads() {
        val fileHandle = Gdx.files.internal(file)
        val data = TextureAtlasData(fileHandle, fileHandle.parent(), false)
        val pages = mutableMapOf<String, Pixmap>()
        data.pages.forEach { page ->
            pages[page.textureFile.path()] = Pixmap(page.textureFile)
        }
        this.data = data
        this.pages = pages
    }

    override fun finishLoadingMainThread() {
        val data = data!!
        val pages = pages!!
        val atlas = TextureAtlas()
        data.pages.forEach { page ->
            page.texture = Texture(pages[page.textureFile.path()], page.useMipMaps)
        }
        atlas.load(data)
        variants = listOf(atlas)
        disposables = listOf(atlas)
    }

    override fun dispose() {
        this.data = null
        pages?.values?.forEach(Disposable::dispose)
        this.pages = null
        super.dispose()
    }
}

class AtlasRegionResource(
    handle: String,
    val regionName: String,
    val atlasResourceHandle: String
) : Resource(handle), ResourceBorrower {

    override fun prepareLoadingAllThreads() {
        ResourceManager.borrow(this, atlasResourceHandle)
    }

    override fun finishLoadingMainThread() {
        val atlas = ResourceManager.get<TextureAtlas>(this, atlasResourceHandle)
        val region = atlas.findRegion(regionName)
        variants = listOf(region, TextureRegionDrawable(region))
    }

    override fun loadDirectMainThread() {
        ResourceManager.borrow(this, atlasResourceHandle)
        val atlas = ResourceManager.get<TextureAtlas>(this, atlasResourceHandle)
        val region = atlas.findRegion(regionName)
        variants = listOf(region, TextureRegionDrawable(region))
    }

    @MainThreadOnly
    override fun dispose() {
        val atlas = ResourceManager.resources.find { it.handle == atlasResourceHandle }!!
        if (this in atlas.borrowedBy) ResourceManager.giveBack(this, atlasResourceHandle)
        super.dispose()
    }

}

class CursorResource(
    handle: String,
    private val file: String,
    private val hotspotX: Int,
    private val hotspotY: Int
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override fun loadDirectMainThread() {
        val cursorPixmap = Pixmap(Gdx.files.internal(file))
        val pixmap = Pixmap(cursorPixmap.width, cursorPixmap.height, Pixmap.Format.RGBA8888)
        pixmap.drawPixmap(cursorPixmap, 0, 0)
        cursorPixmap.dispose()
        val cursor = Gdx.graphics.newCursor(pixmap, hotspotX, hotspotY)
        pixmap.dispose()
        disposables = listOf(cursor)
        variants = listOf(cursor)
    }

    override fun prepareLoadingAllThreads() {
        val cursorPixmap = Pixmap(Gdx.files.internal(file))
        val pixmap = Pixmap(cursorPixmap.width, cursorPixmap.height, Pixmap.Format.RGBA8888)
        pixmap.drawPixmap(cursorPixmap, 0, 0)
        cursorPixmap.dispose()
        this.pixmap = pixmap
    }

    override fun finishLoadingMainThread() {
        val pixmap = pixmap!!
        val cursor = Gdx.graphics.newCursor(pixmap, hotspotX, hotspotY)
        pixmap.dispose()
        disposables = listOf(cursor)
        variants = listOf(cursor)
    }
}

class ShaderResource(
    handle: String,
    private val file: String,
    private val constantArgs: Map<String, Any>
) : Resource(handle) {

    private var preProcessor: BetterShaderPreProcessor? = null
    private var preProcessedCode: Pair<String, String>? = null

    override fun loadDirectMainThread() {
        val preProcessor = BetterShaderPreProcessor(Gdx.files.internal(file), constantArgs)
        val code = preProcessor.preProcess()
        if (code !is Either.Left) throw RuntimeException("shader $handle is only meant for exporting")
        val shader = preProcessor.compile(code.value)
        disposables = listOf(shader)
        variants = listOf(shader, shader.shader)
    }

    override fun prepareLoadingAllThreads() {
        val preProcessor = BetterShaderPreProcessor(Gdx.files.internal(file), constantArgs)
        val code = preProcessor.preProcess()
        if (code !is Either.Left) throw RuntimeException("shader $handle is only meant for exporting")
        preProcessedCode = code.value
        this.preProcessor = preProcessor
    }

    override fun finishLoadingMainThread() {
        val shader = preProcessor!!.compile(preProcessedCode!!)
        disposables = listOf(shader)
        variants = listOf(shader, shader.shader)
        preProcessor = null
        preProcessedCode = null
    }
}

class ColorTextureResource(
    handle: String,
    private val color: Color
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override fun loadDirectMainThread() {
        val colorPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        colorPixmap.setColor(color)
        colorPixmap.fill()
        val texture = Texture(colorPixmap)
        val textureRegion = TextureRegion(texture)
        variants = listOf(colorPixmap, texture, textureRegion, TextureRegionDrawable(textureRegion))
        disposables = listOf(texture, colorPixmap)
    }

    override fun prepareLoadingAllThreads() {
        val colorPixmap = Pixmap(1, 1, Pixmap.Format.RGBA8888)
        colorPixmap.setColor(color)
        colorPixmap.fill()
        pixmap = colorPixmap
    }

    override fun finishLoadingMainThread() {
        val colorPixmap = pixmap!!
        val texture = Texture(colorPixmap)
        val textureRegion = TextureRegion(texture)
        variants = listOf(colorPixmap, texture, textureRegion, TextureRegionDrawable(textureRegion))
        disposables = listOf(texture, colorPixmap)
    }

    override fun dispose() {
        pixmap = null // disposed via disposables list
        super.dispose()
    }
}

class ParticleResource(
    handle: String,
    private val file: String,
    private val textureDirectory: String,
    private val scale: Float
) : Resource(handle) {

    override fun loadDirectMainThread() {
        val effect = ParticleEffect()
        effect.load(Gdx.files.internal(file), Gdx.files.internal(textureDirectory))
        effect.scaleEffect(scale)
        variants = listOf(effect)
        disposables = listOf(effect)
    }

    override fun prepareLoadingAllThreads() {
        // TODO: figure out how to load resources early
    }

    override fun finishLoadingMainThread() {
        val effect = ParticleEffect()
        effect.load(Gdx.files.internal(file), Gdx.files.internal(textureDirectory))
        effect.scaleEffect(scale)
        variants = listOf(effect)
        disposables = listOf(effect)
    }

}

class NinepatchResource(
    handle: String,
    private val file: String,
    private val left: Int,
    private val right: Int,
    private val top: Int,
    private val bottom: Int,
    private val scale: Float
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override fun loadDirectMainThread() {
        val texture = Texture(Gdx.files.internal(file))
        val ninepatch = NinePatch(
            TextureRegion(texture),
            left, right, top, bottom
        )
        ninepatch.scale(scale, scale)
        disposables = listOf(texture)
        variants = listOf(ninepatch, NinePatchDrawable(ninepatch))
    }

    override fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap!!)
        val ninepatch = NinePatch(
            TextureRegion(texture),
            left, right, top, bottom
        )
        ninepatch.scale(scale, scale)
        disposables = listOf(texture)
        variants = listOf(ninepatch, NinePatchDrawable(ninepatch))
    }

    override fun dispose() {
        pixmap?.dispose()
        pixmap = null
        super.dispose()
    }
}