package com.fourinachamber.fortyfive.config

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.screen.screens.AddMaxHPScreen
import com.fourinachamber.fortyfive.screen.screens.HealOrMaxHPScreen
import com.fourinachamber.fortyfive.screen.screens.ShopScreen
import com.fourinachamber.fortyfive.screen.screenBuilder.FromKotlinScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.FromOnjScreenBuilder
import com.fourinachamber.fortyfive.screen.screenBuilder.ScreenBuilder
import com.fourinachamber.fortyfive.screen.screens.CreditsScreen
import com.fourinachamber.fortyfive.screen.screens.DialogScreen
import com.fourinachamber.fortyfive.screen.screens.GameScreen
import com.fourinachamber.fortyfive.screen.screens.MapScreen
import com.fourinachamber.fortyfive.screen.screens.TitleScreen
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
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

    private lateinit var screenSchema: OnjSchema

    private lateinit var configFiles: List<ConfigFile>
    private lateinit var screens: MutableList<ScreenData>

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
        screenSchema = OnjSchemaParser.parseFile(Gdx.files.internal(onj.get<String>("screenSchema")).file())
        screens = onj
            .get<OnjArray>("screens")
            .value
            .map {
                it as OnjObject
                ScreenData(
                    it.get<String>("name"),
                    it.get<String>("file"),
                    null,
                    null
                )
            }
            .toMutableList()
    }

    private fun addScreen(name: String, creator: () -> ScreenBuilder) {
        screens.add(ScreenData(name, null, creator, null))
    }

    fun screenBuilderFor(screen: String): ScreenBuilder {
        val s = screenOrError(screen)
        s.creator?.let { return it() }
        if (s.onj == null) forceLoadScreen(screen)
        return FromOnjScreenBuilder(s.name, s.onj!!)
    }

    fun forceLoadScreen(screen: String) {
        val s = screenOrError(screen)
        val path = s.path ?: run {
            FortyFiveLogger.warn(logTag, "couldn't load screen $screen because it is not associated with an onj file")
            return
        }
        if (s.onj != null) return
        val onj = OnjParser.parseFile(path)
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
        val onj = try {
            val onj = OnjParser.parseFile(Gdx.files.internal(file.path).file())
            val schema = file.schemaPath?.let { OnjSchemaParser.parseFile(Gdx.files.internal(it).file()) }
            schema?.assertMatches(onj)
            onj
        } catch (e: Exception) {
            FortyFiveLogger.severe(logTag, "Error parsing file: $configFile")
            throw e
        }
        file.onj = onj as OnjObject
    }

    private fun configFileOrError(configFile: String): ConfigFile = configFiles
        .find { it.name == configFile }
        ?: throw RuntimeException("no config file called $configFile")

    private fun screenOrError(screen: String): ScreenData = screens
        .find { it.name == screen }
        ?: throw RuntimeException("no screen called $screen")

    fun addKotlinScreens() {
        val data = listOf(
            "mapScreen" to { FromKotlinScreenBuilder(MapScreen()) },
            "healOrMaxHPScreen" to { FromKotlinScreenBuilder(HealOrMaxHPScreen()) },
//            "healOrMaxHPScreen" to { FromKotlinScreenBuilder(CustomBoxPlaygroundScreen()) },
            "addMaxHPScreen" to { FromKotlinScreenBuilder(AddMaxHPScreen()) },
            "titleScreen" to { FromKotlinScreenBuilder(TitleScreen()) },
            "creditsScreen" to { FromKotlinScreenBuilder(CreditsScreen()) },
            "shopScreen" to { FromKotlinScreenBuilder(ShopScreen()) },
            "dialogScreen" to { FromKotlinScreenBuilder(DialogScreen()) },
            "gameScreen" to { FromKotlinScreenBuilder(GameScreen()) }
        )
        data.forEach { addScreen(it.first,it.second) }
    }

    private data class ConfigFile(
        val name: String,
        val path: String,
        val schemaPath: String?,
        var onj: OnjObject?
    )

    private data class ScreenData(
        val name: String,
        val path: String?,
        val creator: (() -> ScreenBuilder)?,
        var onj: OnjObject?
    )

}
