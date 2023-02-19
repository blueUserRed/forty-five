package com.fourinachamber.fourtyfive.rendering

import com.badlogic.gdx.graphics.glutils.ShaderProgram
import com.badlogic.gdx.utils.Disposable

// TODO: come up with better name
class BetterShader(val shaderProgram: ShaderProgram) : Disposable {

    override fun dispose() {
        shaderProgram.dispose()
    }
}
