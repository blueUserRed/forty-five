package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Either

// TODO: come up with better name
class BetterShader(
    val shader: ShaderProgram,
    val uniformsToBind: List<String>
) : Disposable {

    private var referenceTime = TimeUtils.millis()

    fun resetReferenceTime() {
        referenceTime = TimeUtils.millis()
    }

    fun prepare(screen: OnjScreen) {
        shader.setUniformMatrix("u_projTrans", screen.viewport.camera.combined)
        bindUniforms(screen)
    }

    private fun bindUniforms(screen: OnjScreen) {
        uniformsToBind.forEach { bindUniform(it, screen) }
    }

    private fun bindUniform(uniform: String, screen: OnjScreen) = when (uniform) {

        "u_time" -> {
            val uTime = TimeUtils.timeSinceMillis(referenceTime).toFloat() / 1000f
            shader.setUniformf("u_time", uTime)
        }

        "u_cursorPosition" -> {
            shader.setUniformf(
                "u_cursorPos",
                Vector2(
                    Gdx.input.x.toFloat(),
                    Gdx.graphics.height - Gdx.input.y.toFloat()
                )
            )
        }

        "u_resolution" -> {
            shader.setUniformf(
                "u_resolution",
                Vector2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
            )
        }

        "u_perlin512x512" -> {
            // TODO: slightly ugly
            val texture = ResourceManager.get<Texture>(screen, "prerendered_noise_perlin_512x512")
            texture.bind(1)
            shader.setUniformi("u_perlin512x512", 1)
            Gdx.gl.glActiveTexture(GL_TEXTURE0)
        }

        else -> throw RuntimeException("unknown uniform: $uniform")
    }

    override fun dispose() {
        shader.dispose()
    }

    companion object {

        fun load(file: String, constantArgs: Map<String, Any> = mapOf()): BetterShader {
            val preProcessor = BetterShaderPreProcessor(Gdx.files.internal(file), constantArgs)
            val code = preProcessor.preProcess()
            if (code !is Either.Left) throw RuntimeException("shader $file is only meant for exporting")
            return preProcessor.compile(code.value)
        }

    }

}
