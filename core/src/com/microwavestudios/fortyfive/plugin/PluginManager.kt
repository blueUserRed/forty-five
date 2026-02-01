package com.microwavestudios.fortyfive.plugin

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.screen.actors.BindTarget
import onj.parser.OnjParser
import onj.parser.OnjParserException
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.schema.OnjSchemaException
import onj.value.OnjObject
import java.io.File
import java.io.IOException

class PluginManager {

    private var _activePlugins: MutableList<ManagedPlugin> = mutableListOf()
    val activePlugins: List<ManagedPlugin>
        get() = _activePlugins

    private var _allPlugins: MutableList<ManagedPlugin> = mutableListOf()
    val allPlugins: List<ManagedPlugin>
        get() = _allPlugins

    fun init() {
        val pluginDir = File(pluginPath)
        _allPlugins = pluginDir
            .walk()
            .maxDepth(1)
            .filter { file -> file.isDirectory && file != pluginDir }
            .mapNotNull { file ->
                val pluginConfig = file.listFiles()?.find { it.isFile && it.name == "plugin.onj" }
                pluginConfig?.let { createPlugin(it, file) }
            }
            .toMutableList()
        val activatedPlugins = mutableListOf<ManagedPlugin>()
        _allPlugins.forEach { plugin ->
            val data = FortyFive.globalSave.getPluginSaveData(plugin.name)
            if (data.isDisabled) return@forEach
            if (plugin.isRisky && !data.agreedToRisk) return@forEach
            activatedPlugins.add(plugin)
            plugin.load()
        }
        _activePlugins = activatedPlugins
    }

    fun findActivePlugin(name: String): ManagedPlugin? = _activePlugins.find { it.name == name }

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
                onj.get<String>("title"),
                onj.get<String>("creator"),
                onj.get<String>("description"),
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

    fun collectCardFiles(): List<Pair<String, OnjObject>> = doFileCollection("cards.onj", cardsSchema)

    fun collectAssetFiles(): List<Pair<String, OnjObject>> = doFileCollection("assets.onj", assetsSchema)

    fun collectDescriptionFiles(): List<Pair<String, OnjObject>> = doFileCollection("descriptions.onj", descriptionSchema)

    private fun doFileCollection(name: String, schema: OnjSchema): List<Pair<String, OnjObject>> =
        _activePlugins.mapNotNull { plugin ->
            val file = plugin.lookForConfigFile(name) ?: return@mapNotNull null
            catchPluginExceptions("Failed to load $name", plugin) {
                val onj = OnjParser.parseFile(file)
                schema.assertMatches(onj)
                onj as OnjObject
            }?.let { plugin.name to it }
        }

    private inline fun <T> catchPluginExceptions(message: String, plugin: ManagedPlugin, block: () -> T): T? {
        return try {
            block()
        } catch (e: OnjParserException) {
            FortyFive.logger.warn(logTag, "${plugin.name}: $message")
            FortyFive.logger.stackTrace(e)
            null
        } catch (e: OnjSchemaException) {
            FortyFive.logger.warn(logTag, "${plugin.name}: $message")
            FortyFive.logger.stackTrace(e)
            null
        } catch (e: IOException) {
            FortyFive.logger.warn(logTag, "${plugin.name}: $message")
            FortyFive.logger.stackTrace(e)
            null
        }
    }

    fun earlyInit() {
        activePlugins.forEach { it.earlyInit() }
    }

    fun start() {
        activePlugins.forEach { it.start() }
    }

    fun onRender() {
        activePlugins.forEach { it.onRender() }
    }

    fun onEnd() {
        activePlugins.forEach { it.onEnd() }
    }

    fun activatedBindTargetForPlugin(plugin: ManagedPlugin): BindTarget<Boolean> = BindTarget(
        Boolean::class,
        { !FortyFive.globalSave.getPluginSaveData(plugin.name).isDisabled },
        { value -> FortyFive.globalSave.setPluginActivation(plugin.name, value) },
        mapOf(true to "enabled", false to "disabled"),
        true,
        plugin.isActive
    )

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

        private val descriptionSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/descriptions.onjschema")
        }
    }

}
