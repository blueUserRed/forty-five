package com.fourinachamber.fortyfive.game.enemy

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.PolygonRegion
import com.badlogic.gdx.graphics.g2d.PolygonSpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.gameWidgets.TextEffectEmitter
import com.fourinachamber.fortyfive.screen.gameWidgets.textEffectEmitter
import com.fourinachamber.fortyfive.screen.general.CustomGroup
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.AnimatedActor
import com.fourinachamber.fortyfive.utils.Color
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.Vector2
import com.fourinachamber.fortyfive.utils.component1
import com.fourinachamber.fortyfive.utils.component2
import com.fourinachamber.fortyfive.utils.epsilonEquals
import kotlin.math.abs

class StatusBar(screen: OnjScreen, private val enemy: Enemy) : CustomGroup(screen), ResourceBorrower, AnimatedActor {

    override val animationsNeedingUpdate: MutableList<AnimatedActor.NeedsUpdate> = mutableListOf()

    private val mainBar: Promise<Drawable> = ResourceManager.request(this, screen, "enemy_status_bar_main_bar")
    private val hpLabel: Promise<Drawable> = ResourceManager.request(this, screen, "enemy_status_bar_hp_label")
    private val whiteTexture: Promise<TextureRegion> = ResourceManager.request(this, screen, "white_texture")
    private val sliderShader: Promise<BetterShader> = ResourceManager.request(this, screen, "enemy_status_bar_shader")

    private val roadgeek: BitmapFont = ResourceManager.forceGet(this, screen, "roadgeek")

    private val hpGlyphLayout: GlyphLayout = GlyphLayout(roadgeek, "", Color.FortyWhite, 100f, Align.center, false)

    private val polygonBatch: PolygonSpriteBatch = PolygonSpriteBatch()

    private var currentDisplayPercent: Float = 1f
    private var targetPercent: Float = 1f

    private val emitter = textEffectEmitter(TextEffectEmitter.standardTextAnimConfigs)

    private var lastKnownHealth: Int = enemy.health

    init {
        screen.onEnd { polygonBatch.dispose() }
        enemy.enemyEvents.watchFor<Enemy.HealthChangedEvent> { hpChanged() }
    }

    fun hpChanged() {
        val newPercent = enemy.currentHealth.toFloat() / enemy.health.toFloat()
        targetPercent = newPercent
        val diff = enemy.currentHealth - lastKnownHealth
        emitter.playNumberChangeAnimation(diff)
        lastKnownHealth = enemy.currentHealth
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateHpBar()
        updateAnimations()

        super.draw(batch, parentAlpha)
        batch ?: return

        val mainBar = mainBar.getOrNull() ?: return
        val whiteTexture = whiteTexture.getOrNull() ?: return
        val sliderShader = sliderShader.getOrNull() ?: return

        val barWidth = width * 0.8f
        val barHeight = barWidth * (mainBar.minHeight / mainBar.minWidth)
        val barX = x + (width - barWidth) / 2
        val barY = y + height - barHeight

        val firstHeight = barHeight * 0.74f
        val secondHeight = barHeight * 0.91f

        batch.end()
        polygonBatch.begin()
        polygonBatch.shader = sliderShader.shader
        sliderShader.prepare(screen)
        sliderShader.shader.setUniformf("u_pos", currentDisplayPercent)
        val region = PolygonRegion(
            whiteTexture,
            floatArrayOf(
                0f, (secondHeight - firstHeight) / 2,
                0f, firstHeight,
                barWidth * 0.93f, secondHeight,
                barWidth * 0.93f, 0f,
            ),
            shortArrayOf(
                2, 1, 0,
                3, 2, 0,
            )
        )
        fixUvCoords(region)
        val (gX, gY) = localToStageCoordinates(Vector2(0f, 0f))
        polygonBatch.draw(region, gX + barX + 4, gY + barY + 1)
        polygonBatch.end()
        batch.begin()
        mainBar.draw(batch, barX, barY, barWidth, barHeight)
        drawHpLabel(batch, barX, barY, barWidth)
    }

    private fun drawHpLabel(batch: Batch, barX: Float, barY: Float, barWidth: Float) {
        val background = hpLabel.getOrNull() ?: return
        val labelWidth = barWidth * 0.37f
        val labelHeight = labelWidth * (background.minHeight / background.minWidth)
        val layout = hpGlyphLayout
        roadgeek.data.setScale(0.5f)
        val text = "${enemy.currentHealth}/${enemy.health}"
        layout.setText(roadgeek, text, Color.White, labelWidth, Align.center, false)
        val labelX = barX + barWidth - labelWidth + 15f
        val labelY = barY - 5f
        background.draw(batch, labelX, labelY, labelWidth, labelHeight)
        roadgeek.draw(batch, layout, labelX, labelY + layout.height + 5f)
    }

    private fun updateHpBar() {
        val diff = abs(currentDisplayPercent - targetPercent)
        val moveDist = hpBarAnimationSpeed * Gdx.graphics.deltaTime * diff
        when {
            targetPercent.epsilonEquals(currentDisplayPercent, epsilon = 0.001f) -> currentDisplayPercent = targetPercent
            targetPercent < currentDisplayPercent -> currentDisplayPercent -= moveDist.toFloat()
            targetPercent > currentDisplayPercent -> currentDisplayPercent += moveDist.toFloat()
        }
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

    companion object {
        const val hpBarAnimationSpeed = 5.0
    }

}
