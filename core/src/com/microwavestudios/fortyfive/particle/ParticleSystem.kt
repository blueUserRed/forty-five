package com.microwavestudios.fortyfive.particle

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.unit
import com.microwavestudios.fortyfive.utils.withMag

class ParticleSystem(
    val worldWidth: Float,
    val worldHeight: Float,
) {

    private val _emitters: MutableList<Emitter> = mutableListOf()
    val emitters: List<Emitter>
        get() = _emitters

    private val baseForce: Vector2 = Vector2()

    fun addBaseForce(forceX: Float, forceY: Float) {
        baseForce.x += forceX
        baseForce.y += forceY
    }

    fun addEmitter(emitter: Emitter) {
        _emitters.add(emitter)
    }

    inline fun emitter(builder: Emitter.() -> Unit): Emitter {
        val emitter = Emitter(this)
        addEmitter(emitter)
        builder(emitter)
        return emitter
    }

    fun update() {
        _emitters.forEach { emitter ->
            emitter.update()
            emitter.particles.forEach { particle ->
                particle.x += particle.velocity.x
                particle.y += particle.velocity.y
                particle.velocity.x += baseForce.x + particle.forces.x
                particle.velocity.y += baseForce.y + particle.forces.y
                if (particle.speedCap < 0) return@forEach
                val mag = particle.velocity.len()
                if (mag < particle.speedCap) return@forEach
                particle.velocity.set(particle.velocity.withMag(particle.speedCap))
            }
        }
    }

    fun render(batch: Batch, screen: OnjScreen) {
        _emitters.forEach { it.renderer?.render(batch, screen) }
    }


    data class Particle(
        var x: Float,
        var y: Float,
        val velocity: Vector2,
        var speedCap: Float,
        val ttl: Long,
        val spawnedAt: Long,
        var alpha: Float,
        var active: Boolean,
        val forces: Vector2
    ) {

        fun applyForce(x: Float, y: Float) {
            forces.x += x
            forces.y += y
        }
    }


    class Emitter(
        val system: ParticleSystem
    ) {

        private val _particles: MutableList<Particle> = mutableListOf()
        val particles: List<Particle>
            get() = _particles

        var xRange: ClosedFloatingPointRange<Float> = (0f..0f)
        var yRange: ClosedFloatingPointRange<Float> = (0f..0f)
        var xVelocityRange: ClosedFloatingPointRange<Float> = (0f..0f)
        var yVelocityRange: ClosedFloatingPointRange<Float> = (0f..0f)
        var speedCap: ClosedFloatingPointRange<Float> = (-1f..-1f)

        var ttlRange: LongRange? = null

        private var syncWithActor: Actor? = null

        var renderer: ParticleRenderer? = null

        private val particleInitializers: MutableList<(Particle) -> Unit> = mutableListOf()
        private val particleUpdaters: MutableList<(Particle) -> Unit> = mutableListOf()

        fun setRangesToActor(actor: Actor) {
            val stageCoords = actor.localToStageCoordinates(Vector2(0f, 0f))
            val lowerX = stageCoords.x
            val upperX = lowerX + actor.width
            val lowerY = stageCoords.y
            val upperY = lowerY + actor.height
            xRange = (lowerX..upperX)
            yRange = (lowerY..upperY)
        }

        fun syncSpawnPosWithActor(actor: Actor?) {
            actor?.let { setRangesToActor(it) }
            syncWithActor = actor
        }

        fun initParticle(block: (Particle) -> Unit) {
            particleInitializers.add(block)
        }

        fun updateParticles(block: (Particle) -> Unit) {
            particleUpdaters.add(block)
        }

        fun spawn(): Particle {
            val particle = Particle(
                xRange.random(), yRange.random(),
                Vector2(xVelocityRange.random(), yVelocityRange.random()),
                speedCap.random(),
                ttlRange?.random() ?: -1L,
                TimeUtils.millis(),
                1f,
                true,
                Vector2()
            )
            println(particle)
            particleInitializers.forEach { it(particle) }
            _particles.add(particle)
            return particle
        }

        fun update() {
            val now = TimeUtils.millis()
            _particles.removeIf { particle ->
                val toRemove = particle.ttl != -1L && now > particle.spawnedAt + particle.ttl
                if (toRemove) particle.active = false
                toRemove
            }
            _particles.forEach { particle -> particleUpdaters.forEach { it(particle) } }
            syncWithActor?.let { setRangesToActor(it) }
        }

    }
}
