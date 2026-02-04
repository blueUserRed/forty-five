package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.utils.Utils
import java.io.File
import java.net.URLClassLoader

class ManagedPlugin(
    val name: String,
    val title: String,
    val creator: String,
    val description: String,
    val pluginClassFqdn: String,
    val directory: File,
    val jarFile: File?,
) {

    private val logTag: String = "plugin-$name"

    var plugin: Plugin? = null
        private set

    val isRisky: Boolean
        get() = jarFile != null

    var isActive: Boolean = false
        private set

    val jarFileHash: String? by lazy {
        jarFile?.let { Utils.hashFile(it) }
    }

    fun load() {
        if (jarFile == null) {
            isActive = true
            return
        }
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
        isActive = true
    }

    fun earlyInit() {
        try {
            plugin?.earlyInit()
        } catch (e: Exception) {
            FortyFive.logger.warn(logTag, "Exception in plugin '$name'")
            FortyFive.logger.stackTrace(e)
        }
    }

    fun start() {
        try {
            plugin?.start()
        } catch (e: Exception) {
            FortyFive.logger.warn(logTag, "Exception in plugin '$name'")
            FortyFive.logger.stackTrace(e)
        }
    }

    fun onRender() {
        try {
            plugin?.onRender()
        } catch (e: Exception) {
            FortyFive.logger.warn(logTag, "Exception in plugin '$name'")
            FortyFive.logger.stackTrace(e)
        }
    }

    fun onEnd() {
        try {
            plugin?.onEnd()
        } catch (e: Exception) {
            FortyFive.logger.warn(logTag, "Exception in plugin '$name'")
            FortyFive.logger.stackTrace(e)
        }
    }

    fun lookForConfigFile(filename: String): File? {
        val file = directory.resolve("config/$filename")
        if (file.exists()) return file
        return null
    }

}
