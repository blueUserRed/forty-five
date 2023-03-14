package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.fourinachamber.fourtyfive.map.detailMap.DetailMap
import com.fourinachamber.fourtyfive.map.detailMap.MapRestriction
import com.fourinachamber.fourtyfive.map.detailMap.SeededMapGenerator
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.random.Random

class MapGenerator(
    private val configFile: FileHandle
) {

    fun generate() {
        val onj = OnjParser.parseFile(configFile.file())
        schema.assertMatches(onj)
        onj as OnjObject
        val outputDir = Gdx.files.local(onj.get<String>("outputDirectory")).file()
        onj
            .get<OnjArray>("maps")
            .value
            .forEach { map ->
                generateMap(map as OnjObject, outputDir)
            }
    }

    private fun generateMap(onj: OnjObject, outputDir: File) {
        val name = onj.get<String>("name")
        val mapRestriction = MapRestriction.fromOnj(onj.get<OnjObject>("restrictions"))
        val generator = SeededMapGenerator(Random.nextLong(), mapRestriction)
        val map = generator.generate()
        val path = "${outputDir.toPath()}/$name.onj"
        val file = File(path)
        file.createNewFile()
        file.writeText(map.asOnjObject().toString())
    }

    companion object {

        val schema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/map_generator_config.onjschema").file())
        }

    }

}