package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import onj.*

object SaveState {

    const val saveFilePath: String = "saves/savefile.onj"

    var additionalCards: Map<String, Int> = mapOf()
        private set

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/savefile.onjschema").file())
    }

    fun read() {
        val obj = OnjParser.parseFile(Gdx.files.internal(saveFilePath).file())
        savefileSchema.assertMatches(obj)
        obj as OnjObject

        val map = mutableMapOf<String, Int>()
        obj
            .get<OnjArray>("additionalCards")
            .value
            .forEach {
                it as OnjObject
                map[it.get<String>("name")] = it.get<Long>("amount").toInt()
            }
        additionalCards = map
    }

    fun write() {
        val obj = buildOnjObject {
            "additionalCards" with additionalCards.entries.map {
                buildOnjObject {
                    "name" with it.key
                    "amount" with it.value
                }
            }
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
    }

}