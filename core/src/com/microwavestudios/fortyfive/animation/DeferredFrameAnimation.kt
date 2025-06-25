package com.microwavestudios.fortyfive.animation

import com.badlogic.gdx.graphics.g2d.TextureAtlas
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.Resource
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.*

class DeferredFrameAnimation(
    val previewHandle: ResourceHandle,
    val atlasHandle: ResourceHandle,
    val frameTime: Int,
) : AnimationPart, ResourceBorrower {

    private var loadedFrameAnimation: FrameAnimation? = null

    private val _lifetime: EndableLifetime = EndableLifetime()
    val lifetime: Lifetime
        get() = _lifetime

    private val previewDrawable: Promise<Drawable> = FortyFive.resourceManager.request(this, lifetime, previewHandle)

    private var hasBeenDisposed: Boolean = false

    override val duration: Int
        get() = loadedFrameAnimation?.duration ?: Int.MAX_VALUE

    override fun getFrame(progress: Int, frameOffset: Int): Drawable? {
        return loadedFrameAnimation?.getFrame(progress, frameOffset) ?: previewDrawable.getOrNull()
    }

    private fun load() {
        FortyFive
            .resourceManager
            .request<TextureAtlas>(this, lifetime, atlasHandle)
            .then(::createFrameAnimation)
    }

    private fun createFrameAnimation(textureAtlas: TextureAtlas) {
        val frames = arrayOfNulls<Drawable>(textureAtlas.regions.size)
        textureAtlas
            .regions
            .forEach { region ->
                val index = region.name.toInt()
                frames[index] = TextureRegionDrawable(region)
            }
        @Suppress("UNCHECKED_CAST")
        loadedFrameAnimation = FrameAnimation(frames as Array<out Drawable>, listOf(), frameTime)
    }

    override fun update() {
        if (loadedFrameAnimation != null) return
        val loadingResources = FortyFive.resourceManager.resources
            .filter { it.startedLoading && it.state != Resource.ResourceState.LOADED }
            .size
        if (loadingResources > 3) return // magic number
        load()
    }

    override fun width(): Float = previewDrawable.getOrNull()?.minWidth ?: 0f

    override fun height(): Float = previewDrawable.getOrNull()?.minHeight ?: 0f

    override fun dispose() {
        if (hasBeenDisposed) return
        hasBeenDisposed = true
        _lifetime.die()
    }
}
