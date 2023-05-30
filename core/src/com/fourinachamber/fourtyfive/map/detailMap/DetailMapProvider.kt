package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.fourinachamber.fourtyfive.map.MapManager
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

interface DetailMapProvider {

    fun get(): DetailMap

}

// TODO: this may be useless
object DetailMapProviderFactory {

    private val detailMapProviderCreators: Map<String, (onj: OnjObject) -> DetailMapProvider> = mapOf(
        "FromFileDetailMapProvider" to { FromFileDetailMapProvider(Gdx.files.internal(it.get<String>("file"))) },
//        "FromSeededGeneratorDetailMapProvider" to { onj ->
//            FromSeededGeneratorDetailMapProvider(
//                onj.get<Long>("seed"),
//                onj.get<String>("startArea"),
//                onj.get<String>("endArea"),
//                onj.get<OnjArray>("otherAreas").value.map { it.value as String }
//            )
//        },
        "CurrentMapProvider" to { CurrentMapProvider() }
    )

    fun get(onj: OnjNamedObject): DetailMapProvider =
        detailMapProviderCreators[onj.name]?.invoke(onj)
            ?: throw RuntimeException("unknown detail map provider ${onj.name}")

}

class FromFileDetailMapProvider(
    private val file: FileHandle
) : DetailMapProvider {

    override fun get(): DetailMap = DetailMap.readFromFile(file)

}

//class FromSeededGeneratorDetailMapProvider(
//    private val seed: Long,
//    private val startArea: String,
//    private val endArea: String,
//    private val otherAreas: List<String>,
//
//    ) : DetailMapProvider {
//
//    override fun get(): DetailMap {
//        val generator =
//            SeededMapGenerator(seed, MapRestriction(startArea = startArea, endArea = endArea, otherAreas = otherAreas))
//        return generator.generate("anonymous")
//    }
//}

class CurrentMapProvider : DetailMapProvider {

    override fun get(): DetailMap = MapManager.currentDetail
}
