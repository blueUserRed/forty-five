package com.fourinachamber.fortyfive.screen.general.customActor

import com.fourinachamber.fortyfive.game.UserPrefs
import kotlin.reflect.KClass

object BindTargetFactory {

    private val bindTargets: Map<String, BindTarget<*>> = mapOf(
        "masterVolume" to BindTarget(
            Float::class,
            getter = { UserPrefs.masterVolume },
            setter = { UserPrefs.masterVolume = it },
            mapOf()
        ),
        "musicVolume" to BindTarget(
            Float::class,
            getter = { UserPrefs.musicVolume },
            setter = { UserPrefs.musicVolume = it },
            mapOf()
        ),
        "soundEffectsVolume" to BindTarget(
            Float::class,
            getter = { UserPrefs.soundEffectsVolume },
            setter = { UserPrefs.soundEffectsVolume = it },
            mapOf()
        ),
        "enableScreenShake" to BindTarget(
            Boolean::class,
            getter = { UserPrefs.enableScreenShake },
            setter = { UserPrefs.enableScreenShake = it },
            mapOf(true to "on", false to "off")
        ),
        "startScreen" to BindTarget(
            UserPrefs.StartScreen::class,
            getter = { UserPrefs.startScreen },
            setter = { UserPrefs.startScreen = it },
            mapOf(
                UserPrefs.StartScreen.MAP to "Map screen",
                UserPrefs.StartScreen.TITLE to "Title screen",
                UserPrefs.StartScreen.INTRO to "Intro screen",
            )
        ),
        "disableRt" to BindTarget(
            Boolean::class,
            getter = { UserPrefs.disableRtMechanics },
            setter = { UserPrefs.disableRtMechanics = it },
            mapOf(true to "disabled", false to "enabled")
        )
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
    val mappings: Map<T, String>
)
