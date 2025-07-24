package com.microwavestudios.fortyfive.config

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.resources.ResourceHandle
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

object ConfigFileManager {

    private const val logTag: String = "ConfigFileManager"
    private const val path: String = "config/files.onj"

    private val schema: OnjSchema by lazy {
        OnjSchemaParser.parseFile("onjschemas/files.onjschema")
    }

    private lateinit var configFiles: List<ConfigFile>

    val mapConfig: MapConfig by lazy {
        val onj = getConfigFile("mapConfig")
        MapConfig.fromOnj(onj)
    }

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


    data class MapConfig(
        val displayNames: Map<String, String>,
        val images: List<MapImageData>
    ) {
        companion object {

            fun fromOnj(onj: OnjObject) = MapConfig(
                onj
                    .get<OnjArray>("displayNames")
                    .value
                    .associate {
                        it as OnjObject
                        it.get<String>("name") to it.get<String>("display")
                    },
                onj
                    .get<OnjArray>("mapImages")
                    .value
                    .map { MapImageData.fromOnj(it as OnjObject) }
            )
        }
    }

    data class MapImageData(
        val name: String,
        val resourceHandle: ResourceHandle,
        val width: Float,
        val height: Float,
        val type: Type
    ) {

        enum class Type {
            SIGN, NAME
        }

        companion object {

            fun fromOnj(onj: OnjObject): MapImageData = MapImageData(
                onj.get<String>("name"),
                onj.get<String>("image"),
                onj.get<Double>("width").toFloat(),
                onj.get<Double>("height").toFloat(),
                when (val type = onj.get<String>("type")) {
                    "sign" -> Type.SIGN
                    "name" -> Type.NAME
                    else -> throw RuntimeException("unknown MapImageData.Type '$type'")
                }
            )
        }
    }

}
