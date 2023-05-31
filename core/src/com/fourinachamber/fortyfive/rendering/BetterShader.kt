package com.fourinachamber.fortyfive.rendering

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.screen.general.OnjScreen

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
        bindUniforms()
    }

    private fun bindUniforms() {
        uniformsToBind.forEach { bindUniform(it) }
    }

    private fun bindUniform(uniform: String) = when (uniform) {

        "u_time" -> {
            val uTime = (TimeUtils.timeSinceMillis(referenceTime) / 100.0).toFloat()
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

        else -> throw RuntimeException("unknown uniform: $uniform")
    }

    override fun dispose() {
        shader.dispose()
    }
}
