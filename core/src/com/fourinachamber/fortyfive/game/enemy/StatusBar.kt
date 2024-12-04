package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.PolygonRegion
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.Vector2
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2

class StatusBar(screen: OnjScreen, enemy: Enemy) : CustomGroup(screen), ResourceBorrower {

    private val mainBar: Promise<Drawable> = ResourceManager.request(this, screen, "enemy_status_bar_main_bar")
    private val whiteTexture: Promise<TextureRegion> = ResourceManager.request(this, screen, "white_texture")
    private val sliderShader: Promise<BetterShader> = ResourceManager.request(this, screen, "enemy_status_bar_shader")

    private val polygonBatch: PolygonSpriteBatch = PolygonSpriteBatch()

    init {
        screen.onEnd { polygonBatch.dispose() }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        batch ?: return

        val mainBar = mainBar.getOrNull() ?: return
        val whiteTexture = whiteTexture.getOrNull() ?: return
        val sliderShader = sliderShader.getOrNull() ?: return

        val barHeight = width * (mainBar.minHeight / mainBar.minWidth)
        val barY = y + height / 2 - barHeight / 2

        val firstHeight = barHeight * 0.74f
        val secondHeight = barHeight * 0.91f

        batch.end()
        polygonBatch.begin()
        polygonBatch.shader = sliderShader.shader
        sliderShader.prepare(screen)
        sliderShader.shader.setUniformf("u_pos", 1.0f)
        val region = PolygonRegion(
            whiteTexture,
            floatArrayOf(
                0f, (secondHeight - firstHeight) / 2,
                0f, firstHeight,
                width * 0.93f, secondHeight,
                width * 0.93f, 0f,
            ),
            shortArrayOf(
                2, 1, 0,
                3, 2, 0,
            )
        )
        fixUvCoords(region)
        val (gX, gY) = localToStageCoordinates(Vector2(0f, 0f))
        polygonBatch.draw(region, gX + x + 4, gY + barY + 1)
        polygonBatch.end()
        batch.begin()
        mainBar.draw(batch, x, barY, width, barHeight)
    }

    private fun fixUvCoords(polygonRegion: PolygonRegion) {
        val vertices = polygonRegion.vertices
        val uvs = polygonRegion.textureCoords

        val minX = (0..<vertices.size step 2).minOf { vertices[it] }
        val minY = (1..<vertices.size step 2).minOf { vertices[it] }
        val maxX = (0..<vertices.size step 2).maxOf { vertices[it] }
        val maxY = (1..<vertices.size step 2).maxOf { vertices[it] }

        for (i in 0..<uvs.size step 2) {
            val x = vertices[i]
            val y = vertices[i + 1]
            uvs[i] = (x - minX) / (maxX - minX)
            uvs[i + 1] = (y - minY) / (maxY - minY)
        }
    }

}
