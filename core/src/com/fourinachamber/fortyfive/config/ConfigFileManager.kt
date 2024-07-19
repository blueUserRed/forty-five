package com.fourinachamber.fortyfive.config

import com.badlogic.gdx.Gdx
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject

object ConfigFileManager {

    private const val path: String = "config/files.onj"

    private val schema: OnjSchema by lazy {
        OnjSchemaParser.parseFile("onjschemas/files.onjschema")
    }

    private lateinit var configFiles: List<ConfigFile>

    fun init() {
        val onj = OnjParser.parseFile(path)
        schema.assertMatches(onj)
        onj as OnjObject
        configFiles = onj
            .value
            .map { (name, obj) ->
                obj as OnjObject
                ConfigFile(
                    name,
                    obj.get<String>("file"),
                    obj.get<String>("validatedBy"),
                    null
                )
            }
    }

    fun getConfigFile(configFile: String): OnjObject {
        val file = configFileOrError(configFile)
        if (file.onj == null) forceLoad(configFile)
        return file.onj!!
    }

    fun forceLoad(configFile: String) {
        val file = configFileOrError(configFile)
        if (file.onj != null) return
        val onj = OnjParser.parseFile(Gdx.files.internal(path).file())
        val schema = OnjSchemaParser.parseFile(Gdx.files.internal(file.schemaPath).file())
        schema.assertMatches(onj)
        file.onj = onj as OnjObject
    }

    private fun configFileOrError(configFile: String): ConfigFile = configFiles
        .find { it.name == configFile }
        ?: throw RuntimeException("no config file called $configFile")

    data class ConfigFile(
        val name: String,
        val path: String,
        val schemaPath: String,
        var onj: OnjObject?
    )

}
