package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import java.io.File
import java.net.URLClassLoader

class ManagedPlugin(
    val name: String,
    val pluginClassFqdn: String,
    val directory: File,
    val jarFile: File?,
) {

    private val logTag: String = "plugin-$name"

    var plugin: Plugin? = null
        private set

    fun load() {
        val jarFile = jarFile ?: return
        val classLoader = URLClassLoader(
            arrayOf(jarFile.toURI().toURL()),
            ManagedPlugin::class.java.classLoader
        )
        val instance = try {
            val clazz = Class.forName(pluginClassFqdn, true, classLoader)
            clazz.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            FortyFive.logger.warn(
                logTag,
                "Failed to create plugin class. Make sure the pluginClass is configured correctly," +
                        "extends Plugin and provides a constructor with no parameters"
            )
            FortyFive.logger.stackTrace(e)
            return
        }
        if (instance !is Plugin) {
            FortyFive.logger.warn(logTag, "Plugin class does not extend Plugin")
            return
        }
        plugin = instance
    }

    fun lookForConfigFile(filename: String): File? {
        val file = directory.resolve("config/$filename")
        if (file.exists()) return file
        return null
    }

}
