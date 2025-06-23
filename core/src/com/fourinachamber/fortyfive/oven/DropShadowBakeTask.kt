package com.fourinachamber.fortyfive.oven

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.FrameBuffer
import com.badlogic.gdx.utils.viewport.StretchViewport
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.TextureResource
import com.fourinachamber.fortyfive.utils.EndableLifetime
import java.io.File

class DropShadowBakeTask(private val incremental: Boolean) : BakeTask, ResourceBorrower {

    override fun bake() {
        val dropShadows = FortyFive
            .resourceManager
            .resources
            .filterIsInstance<TextureResource>()
            .filter { it.dropShadowColor != null }
            .map { it.handle to it.dropShadowColor!! }
        createDropShadows(dropShadows, incremental)
    }

    private fun createDropShadows(dropShadowsToDraw: List<Pair<String, Color>>, incremental: Boolean) {
        if (dropShadowsToDraw.isEmpty()) return

        val taskLifetime = EndableLifetime()

        val dropShadowShader = FortyFive.resourceManager.forceGet<BetterShader>(
            this,
            taskLifetime,
            "drop_shadow_shader"
        )

        val batch = SpriteBatch()

        batch.setBlendFunction(-1, -1)
        Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, GL20.GL_ONE, GL20.GL_ONE)
        val multiplier = 0.3f
        batch.shader = dropShadowShader.shader
        dropShadowShader.shader.bind()
        dropShadowShader.shader.setUniformf("u_multiplier", multiplier)
        dropShadowShader.shader.setUniformf("u_maxOpacity", 0.3f)
        val scale = (1 + multiplier)

        dropShadowsToDraw.forEach { (textureHandle, color) ->
            val fileHandle = FileHandle(File("drop_shadows/$textureHandle${ResourceManager.DROP_SHADOW_END}.png"))
            if (incremental && fileHandle.exists()) return@forEach

            val dropShadowLifetime = EndableLifetime()

            val texture = FortyFive.resourceManager.forceGet<Texture>(this, dropShadowLifetime, textureHandle)
            dropShadowShader.shader.setUniformf("u_color", color)
            val baseWidth = texture.width
            val baseHeight = texture.height
            val width = (baseWidth * scale).toInt()
            val height = (baseHeight * scale).toInt()
            dropShadowShader.shader.setUniformf("u_resolution", width.toFloat(), height.toFloat())

            val fbo = FrameBuffer(Pixmap.Format.RGBA8888, width, height, false)
            dropShadowLifetime.tieDisposable(fbo)

            val viewport = StretchViewport(width.toFloat(), height.toFloat())
            viewport.update(width, height, true)
            batch.projectionMatrix = viewport.camera.combined

            fbo.begin()
            batch.begin()

            viewport.update(width, height)

            Gdx.gl.glClearColor(0f, 0f, 0f, 0f)
            Gdx.gl.glClear(GL_COLOR_BUFFER_BIT)

            batch.draw(texture, 0f, 0f, width.toFloat(), height.toFloat())
            batch.end()

            val pixmap = Pixmap.createFromFrameBuffer(0, 0, width, height)
            dropShadowLifetime.tieDisposable(pixmap)

            fbo.end()

            PixmapIO.writePNG(fileHandle, pixmap)
            dropShadowLifetime.die()
            FortyFive.logger.debug("DropShadowBakeTask", "Created DropShadow for $textureHandle")
        }
        taskLifetime.die()
    }

}
