package com.fourinachamber.fortyfive.screen

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.Texture.TextureFilter
import com.badlogic.gdx.graphics.g2d.*
import com.badlogic.gdx.graphics.g2d.BitmapFont.BitmapFontData
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TiledDrawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.animation.DeferredFrameAnimation
import com.fourinachamber.fortyfive.rendering.BetterShaderPreProcessor
import com.fourinachamber.fortyfive.utils.*
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

    private var promise: Promise<Resource> = Promise()
    private val activePromises: MutableList<Promise<*>> = mutableListOf()

    var startedLoading: Boolean = false
        private set

    @AllThreadsAllowed
    abstract suspend fun prepareLoadingAllThreads()

    @MainThreadOnly
    abstract fun finishLoadingMainThread()

    @MainThreadOnly
    fun <T> get(variantType: KClass<T>): T? where T : Any {
        if (state != ResourceState.LOADED) {
            runBlocking { load() }
        }
        for (variant in variants) if (variantType.isInstance(variant)) return variantType.cast(variant)
        return null
    }

    fun promiseMatches(check: Promise<*>): Boolean = activePromises.any { it === check }

    open fun forceResolve() {
        runBlocking {
            load()
        }
        if (!promise.isResolved) promise.resolve(this)
    }

    open fun <T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, variantType: KClass<T>): T {
        borrow(borrower, lifetime)
        runBlocking {
            load()
        }
        lifetime.onEnd { giveBack(borrower) }
        if (!promise.isResolved) promise.resolve(this)
        return getVariant(variantType)
    }

    open fun <T : Any> request(borrower: ResourceBorrower, lifetime: Lifetime, variantType: KClass<T>): Promise<T> {
        borrow(borrower, lifetime)
        val returnPromise = if (startedLoading) {
            promise.map { getVariant(variantType) }
        } else {
            startedLoading = true

            //// !! prints are left here intentionally !!

//            val startedTime = TimeUtils.millis()
            val message = ServiceThreadMessage.PrepareResource(this)
            FortyFive.serviceThread.sendMessage(message)
            val loadPromise = message.promise.chain {
//                println("${Thread.currentThread().name} $handle")
                FortyFive.mainThreadTask {
//                    val finishedPrep = TimeUtils.millis()
                    runBlocking { load() }
//                    val finished = TimeUtils.millis()
//                    println("$handle: ${finished - startedTime} (${finishedPrep - startedTime} / ${finished - finishedPrep})")
                    this
                }
            }
            loadPromise.onResolve {
                if (!promise.isResolved) promise.resolve(it)
            }
            promise.map { getVariant(variantType) }
        }
        activePromises.add(returnPromise)
        lifetime.onEnd { activePromises.remove(returnPromise) }
        return returnPromise
    }

    protected fun <T : Any> getVariant(variantType: KClass<T>): T {
        val variant = variants.find { variantType.isInstance(it) }
            ?: throw RuntimeException("no variant of type ${variantType.simpleName} for resource $handle")
        return variantType.cast(variant)
    }

    @MainThreadOnly
    protected open suspend fun load() = mutex.withLock {
        if (state == ResourceState.NOT_LOADED) {
            prepareLoadingAllThreads()
            finishLoadingMainThread()
        } else if (state == ResourceState.PREPARED) {
            finishLoadingMainThread()
        }
        state = ResourceState.LOADED
    }

    @AllThreadsAllowed
    open suspend fun prepare() = mutex.withLock {
        if (state != ResourceState.NOT_LOADED) return
        prepareLoadingAllThreads()
        state = ResourceState.PREPARED
    }

    fun borrow(borrower: ResourceBorrower, lifetime: Lifetime) {
        val added = borrowedBy.add(borrower)
        if (!added) return
        lifetime.onEnd { giveBack(borrower) }
    }

    fun borrow(borrower: ResourceBorrower) {
        borrowedBy.add(borrower)
    }

    fun giveBack(borrower: ResourceBorrower) {
        if (!borrowedBy.remove(borrower)) return
        if (borrowedBy.isEmpty()) dispose()
    }

    @MainThreadOnly
    override fun dispose() = synchronized(this) {
        disposables.forEach(Disposable::dispose)
        variants = listOf()
        disposables = listOf()
        promise = Promise()
        startedLoading = false
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
    val tileScale: Float,
    val useMipMaps: Boolean,
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override suspend fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap, useMipMaps)
        if (useMipMaps) texture.setFilter(TextureFilter.MipMapLinearLinear, TextureFilter.Linear)
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

    override suspend fun prepareLoadingAllThreads() {
        pixmap = Pixmap(Gdx.files.internal(imageFile))
        fontData = BitmapFontData(Gdx.files.internal(fontFile), false)
    }

    override fun finishLoadingMainThread() {
        val texture = Texture(pixmap, true)
        val font = BitmapFont(fontData!!, TextureRegion(texture), false)
        texture.setFilter(TextureFilter.MipMapLinearNearest, TextureFilter.Linear)
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

    private val prepMutex = Mutex()
    
    override suspend fun prepareLoadingAllThreads() {
        prepMutex.withLock {
            if (state != ResourceState.NOT_LOADED) return
            val fileHandle = Gdx.files.internal(file)
            val data = TextureAtlasData(fileHandle, fileHandle.parent(), false)
            val pages = mutableMapOf<String, Pixmap>()
            data.pages.forEach { page ->
                pages[page.textureFile.path()] = Pixmap(page.textureFile)
            }
            this@AtlasResource.data = data
            this@AtlasResource.pages = pages
        }
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
        this.pages = null
        super.dispose()
    }
}

class AtlasRegionResource(
    handle: ResourceHandle,
    val regionName: String,
    val atlasResourceHandle: String
) : Resource(handle), ResourceBorrower {

    private val atlasResource: AtlasResource by lazy {
        val atlasResource = ResourceManager.resources.find { it.handle == atlasResourceHandle }
            ?: throw RuntimeException("No atlas with handle $atlasResourceHandle")
        atlasResource as? AtlasResource
            ?: throw RuntimeException("resource with handle $atlasResourceHandle is not an atlas")
        atlasResource
    }

    private var atlasPromise: Promise<TextureAtlas>? = null

    override fun <T : Any> request(borrower: ResourceBorrower, lifetime: Lifetime, variantType: KClass<T>): Promise<T> {
        atlasPromise = ResourceManager.request<TextureAtlas>(borrower, lifetime, atlasResourceHandle)
        return atlasPromise!!.map {
            loadFromAtlas()
            getVariant(variantType)
        }
    }

    override fun forceResolve() {
        atlasResource.forceResolve()
    }

    override fun <T : Any> forceGet(borrower: ResourceBorrower, lifetime: Lifetime, variantType: KClass<T>): T {
        if (atlasPromise == null) request(borrower, lifetime, variantType)
        forceResolve()
        return getVariant(variantType)
    }

    private fun loadFromAtlas() {
        val atlas = atlasResource.get(TextureAtlas::class)!!
        val region = atlas.findRegion(regionName)
        variants = listOf(region, TextureRegionDrawable(region))
    }

    override suspend fun prepareLoadingAllThreads() {
    }

    override fun finishLoadingMainThread() {
    }

    @MainThreadOnly
    override fun dispose() {
        atlasPromise = null
    }

}

class CursorResource(
    handle: ResourceHandle,
    private val file: String,
    private val hotspotX: Int,
    private val hotspotY: Int
) : Resource(handle) {

    private var pixmap: Pixmap? = null

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
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

    override suspend fun prepareLoadingAllThreads() {
        anim = DeferredFrameAnimation(previewHandle, atlasHandle, frameTime)
    }

    override fun finishLoadingMainThread() {
        variants = listOf(anim!!)
        disposables = listOf(anim!!)
    }

}

class SoundResource(
    handle: ResourceHandle,
    private val file: String,
) : Resource(handle) {

    private var sound: Sound? = null

    override suspend fun prepareLoadingAllThreads() {
        sound = Gdx.audio.newSound(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        variants = listOf(sound!!)
        disposables = listOf(sound!!)
    }

    override fun dispose() {
        super.dispose()
        sound?.dispose()
    }
}

class MusicResource(
    handle: ResourceHandle,
    private val file: String,
) : Resource(handle) {

    private var music: Music? = null


    override suspend fun prepareLoadingAllThreads() {
        music = Gdx.audio.newMusic(Gdx.files.internal(file))
    }

    override fun finishLoadingMainThread() {
        variants = listOf(music!!)
        disposables = listOf(music!!)
    }

    override fun dispose() {
        super.dispose()
        music?.dispose()
    }
}
