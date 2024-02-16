package com.fourinachamber.fortyfive.animation

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.utils.ServiceThreadMessage
import com.fourinachamber.fortyfive.utils.Timeline
import org.w3c.dom.Text
import kotlin.system.measureTimeMillis

class DeferredFrameAnimation(
    val previewHandle: ResourceHandle,
    val atlasHandle: ResourceHandle,
    val frameTime: Int,
) : AnimationPart, ResourceBorrower {

    private var loadedFrameAnimation: FrameAnimation? = null

    private val previewDrawableDelegate: Lazy<Drawable> = lazy {
        ResourceManager.borrow(this, previewHandle)
        ResourceManager.get(this, previewHandle)
    }

    private val previewDrawable: Drawable by previewDrawableDelegate

    private var hasBeenDisposed: Boolean = false

    private val loadingTimeline: Timeline = Timeline().apply { startTimeline() }

    private var atlas: TextureAtlas? = null

    override val duration: Int
        get() = loadedFrameAnimation?.duration ?: Int.MAX_VALUE

    private val serviceThreadMessage = ServiceThreadMessage.LoadAnimationResource(atlasHandle)

    init {
        FortyFive.serviceThread.sendMessage(serviceThreadMessage)
    }

    override fun getFrame(progress: Int, frameOffset: Int): Drawable {
        if (loadedFrameAnimation == null && serviceThreadMessage.finished && loadingTimeline.isFinished) {
            loadFrameAnimation()
        }
        return loadedFrameAnimation?.getFrame(progress, frameOffset) ?: previewDrawable
    }

    override fun update() {
        loadingTimeline.updateTimeline()
    }

    override fun width(): Float = previewDrawable.minWidth

    override fun height(): Float = previewDrawable.minHeight

    private fun loadFrameAnimation() {
        val data = serviceThreadMessage.data!!
        val pages = serviceThreadMessage.pages!!
        val atlas = TextureAtlas()
        loadingTimeline.appendAction(Timeline.timeline {
            data.pages.forEach { page ->
                action {
                    val pagePixmap = pages[page.textureFile.path()]!!
                    page.texture = Texture(pagePixmap, false)
                    pagePixmap.dispose()
                }
                delay(100)
            }
            action {
                atlas.load(data)
                this@DeferredFrameAnimation.atlas = atlas
                val frames = arrayOfNulls<Drawable>(atlas.regions.size)
                atlas
                    .regions
                    .forEach { region ->
                        val number = region.name.toInt()
                        frames[number] = TextureRegionDrawable(region)
                    }
                @Suppress("UNCHECKED_CAST") // this cast is safe, because every element was replaced
                loadedFrameAnimation = FrameAnimation(frames as Array<out Drawable>, listOf(), frameTime)
            }
        }.asAction())
    }

    override fun dispose() {
        if (hasBeenDisposed) return
        hasBeenDisposed = true
        synchronized(serviceThreadMessage) {
            if (loadedFrameAnimation == null) {
                if (serviceThreadMessage.finished) {
                    if (loadingTimeline.isFinished) {
                        serviceThreadMessage.pages!!.values.forEach { it.dispose() }
                        return@synchronized
                    }
                    // TODO: might cause long delays when the screen is closed while the animation is still loading
                    while (!loadingTimeline.isFinished) loadingTimeline.updateTimeline()
                }
                else serviceThreadMessage.cancelled = true
            }
        }
        loadingTimeline.stopTimeline()
        atlas?.dispose()
        loadedFrameAnimation?.dispose()
        // avoid giving back the preview if it has never been needed
        if (previewDrawableDelegate.isInitialized()) {
            ResourceManager.giveBack(this, previewHandle)
        }
    }
}
