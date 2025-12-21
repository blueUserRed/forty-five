package com.microwavestudios.fortyfive.config

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.run.Run
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

/**
 * retrieves, stores and caches onj config files globally. Config files are validated automatically.
 * Use the [getConfigFile] function to retrieve a parsed config file.
 *
 * See `assets/config/files.onj` for available files
 *
 * Use [mapConfig], [runConfig] and [npcConfig] to access maps, runs and npcs.
 */
object ConfigFileManager {

    private const val logTag: String = "ConfigFileManager"
    private const val path: String = "config/files.onj"

    private val schema: OnjSchema by lazy {
        OnjSchemaParser.parseFile("onjschemas/files.onjschema")
    }

    private lateinit var configFiles: List<ConfigFile>
    private lateinit var displayNames: Map<String, String>

    val mapConfig: MapConfig by lazy {
        val onj = getConfigFile("mapConfig")
        MapConfig.fromOnj(onj)
    }

    val npcConfig: NpcConfig = NpcConfig()
    val runConfig: RunConfig = RunConfig()

    fun init() {
        val onj = OnjParser.parseFile(path)
        schema.assertMatches(onj)
        onj as OnjObject
        configFiles = onj
            .get<OnjObject>("configFiles")
            .value
            .map { (name, obj) ->
                obj as OnjObject
                ConfigFile(
                    name,
                    obj.get<String>("file"),
                    obj.get<String?>("validatedBy"),
                    null
                )
            }
        displayNames = onj
            .get<OnjArray>("displayNames")
            .value
            .associate { names ->
                names as OnjArray
                names.get<String>(0) to names.get<String>(1)
            }
    }

    fun getDisplayName(internalName: String): String {
        val name = displayNames[internalName]
        if (name != null) return name
        FortyFive.logger.warn(logTag, "No display name for '$internalName' found")
        return internalName
    }

    fun getConfigFile(configFile: String): OnjObject {
        val file = configFileOrError(configFile)
        if (file.onj == null) forceLoadConfigFile(configFile)
        return file.onj!!
    }

    fun forceLoadConfigFile(configFile: String) {
        val file = configFileOrError(configFile)
        if (file.onj != null) return
        val onj = try {
            val onj = OnjParser.parseFile(Gdx.files.internal(file.path).file())
            val schema = file.schemaPath?.let { OnjSchemaParser.parseFile(Gdx.files.internal(it).file()) }
            schema?.assertMatches(onj)
            onj
        } catch (e: Exception) {
            FortyFive.logger.severe(logTag, "Error parsing file: $configFile")
            throw e
        }
        file.onj = onj as OnjObject
    }

    private fun configFileOrError(configFile: String): ConfigFile = configFiles
        .find { it.name == configFile }
        ?: throw RuntimeException("no config file called $configFile")

    private data class ConfigFile(
        val name: String,
        val path: String,
        val schemaPath: String?,
        var onj: OnjObject?
    )

}

/**
 * retrieves the display name for [internalName]. If no name is found, a warning gets logged and [internalName]
 * is returned. Display names are configured in `assets/config/files.onj`
 */
@Suppress("NOTHING_TO_INLINE")
inline fun displayName(internalName: String): String = ConfigFileManager.getDisplayName(internalName)
