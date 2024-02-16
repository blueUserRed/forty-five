package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fortyfive.animation.DeferredFrameAnimation
import com.fourinachamber.fortyfive.rendering.BetterShaderPreProcessor
import com.fourinachamber.fortyfive.utils.AllThreadsAllowed
import com.fourinachamber.fortyfive.utils.Either
import com.fourinachamber.fortyfive.utils.MainThreadOnly
import com.fourinachamber.fortyfive.utils.PixmapFont
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass
import kotlin.reflect.cast


abstract class Resource(
    val handle: String,
    var variants: List<Any> = listOf(),
    var disposables: List<Disposable> = listOf(),
    val borrowedBy: MutableSet<ResourceBorrower> = mutableSetOf()
) : Disposable {

    var state: ResourceState = ResourceState.NOT_LOADED
        protected set

    private val mutex = Mutex()

    @MainThreadOnly
    protected abstract fun loadDirectMainThread()

    @AllThreadsAllowed
    protected abstract fun prepareLoadingAllThreads()

    @MainThreadOnly
    protected abstract fun finishLoadingMainThread()


    @MainThreadOnly
    fun <T> get(variantType: KClass<T>): T? where T : Any {
        if (state != ResourceState.LOADED) {
            runBlocking {
                mutex.withLock { load() }
            }
        }
        for (variant in variants) if (variantType.isInstance(variant)) return variantType.cast(variant)
        return null
    }

    @MainThreadOnly
    private fun load() {
        if (state == ResourceState.NOT_LOADED) {
            loadDirectMainThread()
        } else if (state == ResourceState.PREPARED) {
            finishLoadingMainThread()
        }
        state = ResourceState.LOADED
    }

    @AllThreadsAllowed
    suspend fun prepare() = mutex.withLock {
        if (state != ResourceState.NOT_LOADED) return
        prepareLoadingAllThreads()
        state = ResourceState.PREPARED
    }

    @MainThreadOnly
    fun borrow(borrower: ResourceBorrower): Resource {
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
    val file: String,
    val tileable: Boolean,
    val tileScale: Float
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override fun loadDirectMainThread() {
        val texture = Texture(Gdx.files.internal(file))
        val region = TextureRegion(texture)
        val drawable =
            if (tileable) TiledDrawable(region).apply { scale = tileScale } else TextureRegionDrawable(region)
        disposables = listOf(texture)
        variants = listOf(texture, region, drawable)
    }

    override fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap)
        val region = TextureRegion(texture)
        val drawable =
            if (tileable) TiledDrawable(region).apply { scale = tileScale } else TextureRegionDrawable(region)
        disposables = listOf(texture)
        variants = listOf(texture, region, drawable)
    }

    override fun dispose() {
        pixmap?.dispose()
        pixmap = null
        super.dispose()
    }

}

class FontResource(
    handle: ResourceHandle,
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
    handle: ResourceHandle,
    val file: String
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
            val pagePixmap = pages[page.textureFile.path()]!!
            page.texture = Texture(pagePixmap, page.useMipMaps)
            pagePixmap.dispose()
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
    handle: ResourceHandle,
    val regionName: String,
    val atlasResourceHandle: String
) : Resource(handle), ResourceBorrower {

    override fun prepareLoadingAllThreads() {
    }

    override fun finishLoadingMainThread() {
        ResourceManager.borrow(this, atlasResourceHandle)
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
    handle: ResourceHandle,
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
    handle: ResourceHandle,
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
    handle: ResourceHandle,
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
    handle: ResourceHandle,
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
    handle: ResourceHandle,
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

class PixmapFontResource(
    handle: ResourceHandle,
    private val fontFile: String,
) : Resource(handle) {

    private var font: PixmapFont? = null

    override fun loadDirectMainThread() {
        val font = PixmapFont(Gdx.files.internal(fontFile))
        variants = listOf(font)
        disposables = listOf(font)
    }

    override fun prepareLoadingAllThreads() {
        font = PixmapFont(Gdx.files.internal(fontFile))
    }

    override fun finishLoadingMainThread() {
        variants = listOf(font!!)
        disposables = listOf(font!!)
    }
}

class DeferredFrameAnimationResource(
    handle: ResourceHandle,
    val previewHandle: ResourceHandle,
    val atlasHandle: ResourceHandle,
    val frameTime: Int
) : Resource(handle) {

    private var anim: DeferredFrameAnimation? = null

    override fun loadDirectMainThread() {
        val anim = DeferredFrameAnimation(previewHandle, atlasHandle, frameTime)
        variants = listOf(anim)
        disposables = listOf(anim)
    }

    override fun prepareLoadingAllThreads() {
        anim = DeferredFrameAnimation(previewHandle, atlasHandle, frameTime)
    }

    override fun finishLoadingMainThread() {
        variants = listOf(anim!!)
        disposables = listOf(anim!!)
    }

}
