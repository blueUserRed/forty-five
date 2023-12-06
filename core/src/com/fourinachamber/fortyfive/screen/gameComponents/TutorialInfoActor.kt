package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen

class TutorialInfoActor(
    private val maskedBackgroundTextureName: ResourceHandle,
    screen: OnjScreen
) : CustomFlexBox(screen) {

    private val loadedBackground: Drawable by lazy {
        ResourceManager.get(screen, maskedBackgroundTextureName)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        batch ?: return
        batch.flush()
        batch.shader = shader.shader
        shader.prepare(screen)
        val button = screen.namedActorOrError("shoot_button")
//        shader.shader.setUniformf("u_x", 0.1f)
//        shader.shader.setUniformf("u_y", 0f)
//        shader.shader.setUniformf("u_width", 0.4f)
//        shader.shader.setUniformf("u_height", 0.4f)
        loadedBackground.draw(batch, x, y, width, height)
        batch.flush()
        batch.shader = null
        super.draw(batch, parentAlpha)
    }

    companion object {

        private const val shaderPath: String = "shaders/maskable_background_actor_shader.glsl"

        private val shader: BetterShader by lazy {
            BetterShader.load(shaderPath)
        }

    }

}