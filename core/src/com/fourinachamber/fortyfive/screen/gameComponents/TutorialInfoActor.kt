package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.BoundedActor
import com.fourinachamber.fortyfive.utils.Vector2
import java.lang.Float.max

class TutorialInfoActor(
    private val maskedBackgroundTextureName: ResourceHandle,
    screen: OnjScreen
) : CustomFlexBox(screen) {

    private val loadedBackground: Drawable by lazy {
        ResourceManager.get(screen, maskedBackgroundTextureName)
    }

    var focusActor: BoundedActor? = null

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch ?: return
        val focusActor = focusActor
        if (focusActor == null) {
            loadedBackground.draw(batch, x, y, width, height)
            super.draw(batch, parentAlpha)
            return
        }
        batch.flush()
        batch.shader = shader.shader
        shader.prepare(screen)
        val bounds = focusActor.getScreenSpaceBounds(screen)
        val center = Vector2(0, 0)
        bounds.getCenter(center)
        shader.shader.setUniformf("u_center", center)
        shader.shader.setUniformf("u_radius", max(bounds.width, bounds.height) / 1.0f)
        loadedBackground.draw(batch, x, y, width, height)
        batch.flush()
        batch.shader = null
        super.draw(batch, parentAlpha)
    }

    fun focusActor(name: String) {
        val actor = screen.namedActorOrError(name)
        if (actor !is BoundedActor) {
            throw RuntimeException("Actor '$name' must implement BoundedActor to be focused by TutorialInfoActor")
        }
        focusActor = actor
    }

    companion object {

        private const val shaderPath: String = "shaders/tutorial_actor_shader.glsl"

        private val shader: BetterShader by lazy {
            BetterShader.load(shaderPath)
        }

    }

}