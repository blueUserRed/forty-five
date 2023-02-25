package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjNamedObject
import onj.value.OnjObject

interface DetailMapProvider {

    fun get(): DetailMap

}

object DetailMapProviderFactory {

    private val detailMapProviderCreators: Map<String, (onj: OnjObject) -> DetailMapProvider> = mapOf(
        "FromFileDetailMapProvider" to { FromFileDetailMapProvider(Gdx.files.internal(it.get<String>("file"))) },
        "FromSeededGeneratorDetailMapProvider" to { FromSeededGeneratorDetailMapProvider(it.get<Long>("seed")) }
    )

    fun get(onj: OnjNamedObject): DetailMapProvider =
        detailMapProviderCreators[onj.name]?.invoke(onj)
            ?: throw RuntimeException("unknown detail map provider ${onj.name}")

}

class FromFileDetailMapProvider(
    private val file: FileHandle
) : DetailMapProvider {

    override fun get(): DetailMap {
        val onj = OnjParser.parseFile(file.file())
        mapOnjSchema.assertMatches(onj)
        onj as OnjObject
        return DetailMap.readFromOnj(onj)
    }

    companion object {

        val mapOnjSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/detail_map.onjschema").file())
        }

    }
}

class FromSeededGeneratorDetailMapProvider(
    private val seed: Long
) : DetailMapProvider {

    override fun get(): DetailMap {
        val generator = SeededMapGenerator(seed)
        generator.generate()
        return DetailMap(generator.build())
    }
}
