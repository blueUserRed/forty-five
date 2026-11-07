package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.screen.actors.BindTarget
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchemaException
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import java.io.IOException

class PluginManager {

    var allPlugins: List<Plugin> = listOf()
        private set

    var activePlugins: MutableList<Plugin> = mutableListOf()
        private set

    fun init() {
        val pluginDir = File(pluginPath)
        val allPlugins = pluginDir
            .walk()
            .maxDepth(1)
            .filter { file -> file.isDirectory && file != pluginDir }
            .mapNotNull { file ->
                val pluginConfig = file.listFiles()?.find { it.isFile && it.name == "plugin.onj" }
                pluginConfig?.let { createPlugin(it, file) }
            }
            .toMutableList()
        val names = mutableSetOf<String>()
        val nameCollisions = mutableSetOf<String>()
        allPlugins.forEach { plugin ->
            if (plugin.name in names) {
                nameCollisions.add(plugin.name)
            } else {
                names.add(plugin.name)
            }
        }
        nameCollisions.forEach { collision ->
            FortyFive.logger.warn(logTag, "Plugin name collision: More than one plugin with name '$collision'")
            allPlugins.removeIf { it.name == collision }
        }
        allPlugins.forEach { FortyFive.globalSave.verifyPluginSaveData(it) }
        val activatedPlugins = mutableListOf<Plugin>()
        allPlugins.forEach { plugin ->
            val data = FortyFive.globalSave.getPluginSaveData(plugin.name)
            if (data.isDisabled) return@forEach
            if (plugin.isRisky && !data.agreedToRisk) return@forEach
            activatedPlugins.add(plugin)
            plugin.load()
        }
        this.allPlugins = allPlugins
        activePlugins = activatedPlugins
        ConfigFileManager.initPluginConfigFiles(activePlugins)
    }

    fun findActivePlugin(name: String): Plugin? = activePlugins.find { it.name == name }

    private fun createPlugin(pluginConfig: File, parentDir: File): Plugin? {
        try {
            val onj = OnjParser.parseFile(pluginConfig)
            pluginSchema.assertMatches(onj)
            onj as OnjObject
            val name = onj.get<String>("name")
            val jarFile = onj.get<String?>("jarFile")?.let { parentDir.resolve(it) }
            if (jarFile != null && (!jarFile.exists() || jarFile.extension != "jar")) {
                FortyFive.logger.warn(logTag, "Can't find jar file of plugin $name: '${jarFile.path}'")
                return null
            }
            val endPoints = onj.getOr<OnjObject?>("endPoints", null)
            val ffListeners = endPoints?.getOr<OnjArray?>("FFListener", null)?.value?.map { it.value as String }
            return Plugin(
                name,
                onj.get<String>("title"),
                onj.get<String>("creator"),
                onj.get<String>("description"),
                parentDir,
                jarFile,
                ffListeners
            )
        } catch (e: OnjParserException) {
            FortyFive.logger.warn(logTag, "Failed to load plugin in directory: '${parentDir.name}'")
            FortyFive.logger.stackTrace(e)
        } catch (e: OnjSchemaException) {
            FortyFive.logger.warn(logTag, "Failed to load plugin in directory: '${parentDir.name}'")
            FortyFive.logger.stackTrace(e)
        } catch (e: IOException) {
            FortyFive.logger.warn(logTag, "Failed to load plugin in directory: '${parentDir.name}'")
            FortyFive.logger.stackTrace(e)
        }
        return null
    }

    fun activatedBindTargetForPlugin(plugin: Plugin): BindTarget<Boolean> = BindTarget(
        Boolean::class,
        { !FortyFive.globalSave.getPluginSaveData(plugin.name).isDisabled },
        { value -> FortyFive.globalSave.setPluginActivation(plugin.name, value) },
        mapOf(true to "enabled", false to "disabled"),
        true,
        plugin.isActive
    )

    fun earlyInitListeners() {
        activePlugins.forEach { it.earlyInitListeners() }
    }

    fun startListeners() {
        activePlugins.forEach { it.startListeners() }
    }

    fun renderListeners() {
        activePlugins.forEach { it.renderListeners() }
    }

    fun endListeners() {
        activePlugins.forEach { it.endListeners() }
    }

    companion object {

        const val pluginPath: String = "plugins/"

        private const val logTag: String = "PluginManager"

        private val pluginSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/plugin/plugin.onjschema")
        }

        private val cardsSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/plugin/cards.onjschema")
        }

        private val descriptionSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/descriptions.onjschema")
        }
    }

}
