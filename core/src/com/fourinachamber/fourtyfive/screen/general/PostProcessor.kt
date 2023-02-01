package com.fourinachamber.fourtyfive.screen.general

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils

/**
 * a postProcessor that is applied to the whole screen
 * @param shader the shader that is applied to the screen
 * @param uniformsToBind the names of the uniforms used by the shader (without prefix)
 * @param arguments additional uniforms to bind
 * @param timeOffset if the shader has a time-uniform, the time will be offset by [timeOffset] ms
 */
data class PostProcessor(
    val shader: ShaderProgram,
    val uniformsToBind: List<String>,
    val arguments: Map<String, Any?>,
    val timeOffset: Int = 0
) : Disposable {

    private val creationTime = TimeUtils.millis()
    private var referenceTime = creationTime + timeOffset

    /**
     * resets the point relative to which the time is calculated to now (+[timeOffset])
     */
    fun resetReferenceTime() {
        referenceTime = TimeUtils.millis() + timeOffset
    }

    override fun dispose() = shader.dispose()

    /**
     * binds the uniforms specified in [uniformsToBind] to the shader
     */
    fun bindUniforms() {
        for (uniform in uniformsToBind) when (uniform) {

            "time" -> {
                val uTime = (TimeUtils.timeSinceMillis(referenceTime) / 100.0).toFloat()
                shader.setUniformf("u_time", uTime)
            }

            "cursorPosition" -> {
                shader.setUniformf(
                    "u_cursorPos",
                    Vector2(
                        Gdx.input.x.toFloat(),
                        Gdx.graphics.height - Gdx.input.y.toFloat()
                    )
                )
            }

            "resolution" -> {
                shader.setUniformf(
                    "u_resolution",
                    Vector2(Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
                )
            }

            else -> throw RuntimeException("unknown uniform: $uniform")
        }
    }

    /**
     * binds the arguments specified in [arguments] to the shader
     */
    fun bindArgUniforms() {
        for ((key, value) in arguments) when (value) {

            is Float -> {
                shader.setUniformf("u_arg_$key", value)
            }

            is Color -> {
                shader.setUniformf("u_arg_$key", value.r,  value.g, value.b, value.a)
            }

            else -> throw RuntimeException("binding uniform arguments of type ${
                value?.let { it::class.simpleName } ?: "null"
            } is currently not supported")

        }
    }

}
