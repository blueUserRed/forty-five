package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.GL20.GL_TEXTURE0
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g3d.Shader
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*

// TODO: come up with better name
class BetterShader(
    val shader: ShaderProgram,
    val uniformsToBind: List<String>,
    private val neededTextures: List<String>,
) : Disposable, ResourceBorrower, Lifetime {

    private var referenceTime = TimeUtils.millis()

    private val lifetime: EndableLifetime = EndableLifetime()

    private val textures: MutableMap<String, Promise<Texture>> = neededTextures
        .associateWith {
            ResourceManager.request<Texture>(this, this, uniformResourceNameMapping[it]!!)
        }
        .toMutableMap()

    override fun onEnd(callback: () -> Unit) = lifetime.onEnd(callback)

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
            val texture = getTexture(uniformResourceNameMapping[uniform]!!)
            texture.bind(1)
            shader.setUniformi("u_perlin512x512", 1)
            Gdx.gl.glActiveTexture(GL_TEXTURE0)
        }

        "u_iceTexture" -> {
            // TODO: This fails if both textures are included in a shader
            val texture = getTexture(uniformResourceNameMapping[uniform]!!)
            texture.bind(1)
            shader.setUniformi("u_iceTexture", 1)
            Gdx.gl.glActiveTexture(GL_TEXTURE0)
        }

        else -> throw RuntimeException("unknown uniform: $uniform")
    }

    private fun getTexture(name: String): Texture {
        val promise = textures[name]!!
        if (!promise.isResolved) ResourceManager.forceResolve(promise)
        return promise.getOrError()
    }

    override fun dispose() {
        lifetime.die()
        shader.dispose()
    }

    companion object {

        fun load(file: String, constantArgs: Map<String, Any> = mapOf()): BetterShader {
            val preProcessor = BetterShaderPreProcessor(Gdx.files.internal(file), constantArgs)
            val code = preProcessor.preProcess()
            if (code !is Either.Left) throw RuntimeException("shader $file is only meant for exporting")
            return preProcessor.compile(code.value)
        }

        private val uniformResourceNameMapping = mapOf(
            "u_perlin512x512" to "prerendered_noise_perlin_512x512",
            "u_iceTexture" to "ice_texture"
        )

    }

}
