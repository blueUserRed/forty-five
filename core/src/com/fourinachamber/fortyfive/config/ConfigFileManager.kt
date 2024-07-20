package com.fourinachamber.fortyfive.config

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.general.ScreenBuilder
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

object ConfigFileManager {

    private const val path: String = "config/files.onj"

    private val schema: OnjSchema by lazy {
        OnjSchemaParser.parseFile("onjschemas/files.onjschema")
    }

    private val screenSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile("onjschemas/screen.onjschema")
    }

    private lateinit var configFiles: List<ConfigFile>
    private lateinit var screens: List<ScreenData>

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
        screens = onj
            .get<OnjArray>("screens")
            .value
            .map {
                it as OnjObject
                ScreenData(
                    it.get<String>("name"),
                    it.get<String>("file"),
                    null
                )
            }
    }

    fun screenBuilderFor(screen: String): ScreenBuilder {
        val s = screenOrError(screen)
        if (s.onj == null) forceLoadScreen(screen)
        return ScreenBuilder(s.name, s.onj!!)
    }

    fun getScreen(screen: String): OnjObject {
        val s = screenOrError(screen)
        if (s.onj == null) forceLoadScreen(screen)
        return s.onj!!
    }

    fun forceLoadScreen(screen: String) {
        val s = screenOrError(screen)
        if (s.onj != null) return
        val onj = OnjParser.parseFile(s.path)
        screenSchema.assertMatches(onj)
        onj as OnjObject
        s.onj = onj
    }

    fun getConfigFile(configFile: String): OnjObject {
        val file = configFileOrError(configFile)
        if (file.onj == null) forceLoadConfigFile(configFile)
        return file.onj!!
    }

    fun forceLoadConfigFile(configFile: String) {
        val file = configFileOrError(configFile)
        if (file.onj != null) return
        val onj = OnjParser.parseFile(Gdx.files.internal(file.path).file())
        val schema = file.schemaPath?.let { OnjSchemaParser.parseFile(Gdx.files.internal(it).file()) }
        schema?.assertMatches(onj)
        file.onj = onj as OnjObject
    }

    private fun configFileOrError(configFile: String): ConfigFile = configFiles
        .find { it.name == configFile }
        ?: throw RuntimeException("no config file called $configFile")

    private fun screenOrError(screen: String): ScreenData = screens
        .find { it.name == screen }
        ?: throw RuntimeException("no screen called $screen")

    private data class ConfigFile(
        val name: String,
        val path: String,
        val schemaPath: String?,
        var onj: OnjObject?
    )

    private data class ScreenData(
        val name: String,
        val path: String,
        var onj: OnjObject?
    )

}
