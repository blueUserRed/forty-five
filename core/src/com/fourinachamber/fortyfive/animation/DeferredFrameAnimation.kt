package com.fourinachamber.fortyfive.animation

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.utils.ServiceThreadMessage
import com.fourinachamber.fortyfive.utils.mapSecond
import com.fourinachamber.fortyfive.utils.zip

class DeferredFrameAnimation(
    val previewHandle: ResourceHandle,
    val atlasHandle: ResourceHandle,
    val frameTime: Int,
) : AnimationPart, ResourceBorrower {

    private var loadedFrameAnimation: FrameAnimation? = null

    private val previewDrawable: Drawable by lazy {
        ResourceManager.borrow(this, previewHandle)
        ResourceManager.get(this, previewHandle)
    }

    override val duration: Int
        get() = loadedFrameAnimation?.duration ?: Int.MAX_VALUE

    private val serviceThreadMessage = ServiceThreadMessage.LoadSingleResource(atlasHandle)

    init {
        FortyFive.serviceThread.sendMessage(serviceThreadMessage)
    }

    override fun getFrame(progress: Int): Drawable {
        if (loadedFrameAnimation == null && serviceThreadMessage.finished) loadFrameAnimation()
        return loadedFrameAnimation?.getFrame(progress) ?: previewDrawable
    }

    private fun loadFrameAnimation() {
        ResourceManager.borrow(this, atlasHandle)
        val atlas = ResourceManager.get<TextureAtlas>(this, atlasHandle)
        val frames = atlas
            .regions
            .zip { it.name }
            .mapSecond { it.toInt() }
            .sortedBy { it.second }
            .map { TextureRegionDrawable(it.first) }
            .toTypedArray()
        loadedFrameAnimation = FrameAnimation(frames, listOf(), frameTime)
    }

    override fun dispose() {
        synchronized(serviceThreadMessage) {
            if (loadedFrameAnimation == null) {
                if (serviceThreadMessage.finished) loadFrameAnimation()
                else serviceThreadMessage.cancelled = true
            }
        }
        loadedFrameAnimation?.dispose()
        ResourceManager.giveBack(this, previewHandle)
        ResourceManager.giveBack(this, atlasHandle)
    }
}
