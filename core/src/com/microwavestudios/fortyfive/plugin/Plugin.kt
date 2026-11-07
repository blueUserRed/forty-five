package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.commonComponents.EnemyActorFactory
import com.microwavestudios.fortyfive.utils.Utils
import java.io.File
import java.net.URLClassLoader

class Plugin(
    val name: String,
    val title: String,
    val creator: String,
    val description: String,
    val directory: File,
    val jarFile: File?,
    private val ffListenerFqdns: List<String>?,
    private val enemyExtensionPointFqdns: List<String>?,
) {

    private val logTag: String = "plugin-$name"

    val isRisky: Boolean
        get() = jarFile != null

    var isActive: Boolean = false
        private set

    val jarFileHash: String? by lazy {
        jarFile?.let { Utils.hashFile(it) }
    }

    private val ffListeners: MutableList<FFListener> = mutableListOf()
    private val enemyExtensionPoints: MutableList<EnemyExtensionPoint> = mutableListOf()

    fun load() {
        if (jarFile == null) {
            isActive = true
            return
        }
        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            Plugin::class.java.classLoader
        )
        ffListenerFqdns?.forEach { fqdn ->
            val listener = instantiateClass<FFListener>(classLoader, fqdn) ?: return@forEach
            ffListeners.add(listener)
        }
        enemyExtensionPointFqdns?.forEach { fqdn ->
            val extensionPoint = instantiateClass<EnemyExtensionPoint>(classLoader, fqdn) ?: return@forEach
            enemyExtensionPoints.add(extensionPoint)
        }
        isActive = true
    }

    fun earlyInitListeners() {
        ffListeners.forEach { it.earlyInit() }
    }

    fun startListeners() {
        enemyExtensionPoints.forEach { extensionPoint ->
            extensionPoint.enemyActorSelector()?.let { EnemyActorFactory.addSelector(it) }
        }
        ffListeners.forEach { it.start() }
    }

    fun renderListeners() {
        ffListeners.forEach { it.render() }
    }

    fun endListeners() {
        ffListeners.forEach { it.end() }
    }

    private inline fun <reified T> instantiateClass(classLoader: ClassLoader, fqdn: String): T? {
        val instance = try {
            val clazz = Class.forName(fqdn, true, classLoader)
            clazz.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            FortyFive.logger.warn(
                logTag,
                "Failed to create plugin class '$fqdn'. Make sure the pluginClass is configured correctly," +
                        "extends ${T::class.qualifiedName} and provides a constructor with no parameters"
            )
            FortyFive.logger.stackTrace(e)
            null
        }
        if (instance !is T) {
            FortyFive.logger.warn(logTag, "Plugin class does not extend ${T::class.qualifiedName}")
            return null
        }
        return instance
    }

}
