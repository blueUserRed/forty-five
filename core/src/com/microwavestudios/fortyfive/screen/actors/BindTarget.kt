package com.microwavestudios.fortyfive.screen.actors

import com.microwavestudios.fortyfive.FortyFive
import kotlin.reflect.KClass

object BindTargetFactory {

    private val bindTargets: Map<String, BindTarget<*>> = mapOf(
        "masterVolume" to BindTarget(
            Float::class,
            getter = { FortyFive.globalSave.masterVolume },
            setter = { FortyFive.globalSave.masterVolume = it },
            mapOf()
        ),
        "musicVolume" to BindTarget(
            Float::class,
            getter = { FortyFive.globalSave.musicVolume },
            setter = { FortyFive.globalSave.musicVolume = it },
            mapOf()
        ),
        "soundEffectsVolume" to BindTarget(
            Float::class,
            getter = { FortyFive.globalSave.soundEffectVolume },
            setter = { FortyFive.globalSave.soundEffectVolume = it },
            mapOf()
        ),
        "enableScreenShake" to BindTarget(
            Boolean::class,
            getter = { FortyFive.globalSave.enableScreenShake },
            setter = { FortyFive.globalSave.enableScreenShake = it },
            mapOf(true to "yes", false to "no")
        ),
        "skipIntroScreen" to BindTarget(
            Boolean::class,
            getter = { FortyFive.globalSave.skipIntroScreen },
            setter = { FortyFive.globalSave.skipIntroScreen = it },
            mapOf(true to "yes", false to "no")
        ),
        "useBorderlessWindowFullscreen" to BindTarget(
            Boolean::class,
            getter = { FortyFive.globalSave.useBorderlessWindowFullscreen },
            setter = { FortyFive.globalSave.useBorderlessWindowFullscreen = it },
            mapOf(true to "yes", false to "no")
        ),
        "fullscreen" to BindTarget(
            Boolean::class,
            getter = { FortyFive.globalSave.fullscreen },
            setter = { FortyFive.globalSave.fullscreen = it },
            mapOf(true to "on", false to "off")
        ),
    )

    fun <T : Any> get(name: String, clazz: KClass<T>): BindTarget<T> {
        val bindTarget = bindTargets[name] ?: throw RuntimeException("unknown bindTarget: $name")
        if (bindTarget.dataClass != clazz) throw RuntimeException("bindTarget $name doesn't conform to type ${clazz.simpleName}")
        @Suppress("UNCHECKED_CAST") // safe
        return bindTarget as BindTarget<T>
    }

    fun getAnyType(name: String): BindTarget<*> =
        bindTargets[name] ?: throw RuntimeException("unknown bindTarget: $name")

    inline fun <reified T : Any> get(name: String): BindTarget<T> = get(name, T::class)
}

data class BindTarget<T : Any>(
    val dataClass: KClass<T>,
    val getter: () -> T,
    val setter: (T) -> Unit,
    val mappings: Map<T, String>,
    val needsApply: Boolean = false,
    val activeValue: T? = null
) {
    val inSync: Boolean
        get() = !needsApply || activeValue == getter()
}
