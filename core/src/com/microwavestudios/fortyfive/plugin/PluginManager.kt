package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchemaException
import onj.value.OnjObject
import java.io.File
import java.io.IOException

class PluginManager {

    private lateinit var _plugins: List<ManagedPlugin>
    val plugins: List<ManagedPlugin>
        get() = _plugins

    fun init() {
        val pluginDir = File(pluginPath)
        _plugins = pluginDir
            .walk()
            .maxDepth(1)
            .filter { file -> file.isDirectory && file != pluginDir }
            .mapNotNull { file ->
                val pluginConfig = file.listFiles()?.find { it.isFile && it.name == "plugin.onj" }
                pluginConfig?.let { createPlugin(it, file) }
            }
            .toList()

        plugins.forEach { it.load() }
        plugins.forEach { it.plugin?.start() }
    }

    fun findPlugin(name: String): ManagedPlugin? = _plugins.find { it.name == name }

    private fun createPlugin(pluginConfig: File, parentDir: File): ManagedPlugin? {
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
            return ManagedPlugin(
                name,
                onj.get<String>("pluginClass"),
                parentDir,
                jarFile
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

    fun collectCardFiles(): List<Pair<String, OnjObject>> {
        return _plugins.mapNotNull { plugin ->
            val file = plugin.lookForConfigFile("cards.onj") ?: return@mapNotNull null
            try {
                val onj = OnjParser.parseFile(file)
                cardsSchema.assertMatches(onj)
                onj as OnjObject
            } catch (e: OnjParserException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            } catch (e: OnjSchemaException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            } catch (e: IOException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            }?.let { plugin.name to it }
        }
    }

    fun collectAssetFiles(): List<Pair<String, OnjObject>> {
        return _plugins.mapNotNull { plugin ->
            val file = plugin.lookForConfigFile("assets.onj") ?: return@mapNotNull null
            try {
                val onj = OnjParser.parseFile(file)
                assetsSchema.assertMatches(onj)
                onj as OnjObject
            } catch (e: OnjParserException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            } catch (e: OnjSchemaException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            } catch (e: IOException) {
                FortyFive.logger.warn(logTag, "Failed to load cards.onj")
                FortyFive.logger.stackTrace(e)
                null
            }?.let { plugin.name to it }
        }
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

        private val assetsSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/assets.onjschema")
        }
    }

}
