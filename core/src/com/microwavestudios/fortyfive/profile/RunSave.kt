package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import onj.builder.buildOnjObject
import onj.value.OnjArray
import onj.value.OnjObject
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class RunSave(val run: Run, val profile: Profile) {

    private var data: RunSaveData = RunSaveData(
        0, null, 100, mutableListOf()
    )

    val mapGenerator: BaseMapGenerator = BaseMapGenerator.fromOnj(run.mapGeneratorData)

    private val map: DetailMap

    init {
        val mapFileHandle = Gdx.files.internal(profile.profilePath.path + "/run_map.onj")
        if (mapFileHandle.exists()) {
            map = DetailMap.readFromFile(mapFileHandle.file())
        } else {
            map = mapGenerator.generate("todo")
            val file = mapFileHandle.file()
            file.writeText(map.asOnjObject().toString())
        }
    }

    var currentNodeIndex: Int by DataDelegate(RunSaveData::currentNode)
    var lastNodeIndex: Int? by DataDelegate(RunSaveData::lastNode)
    var playerHealth: Int by DataDelegate(RunSaveData::playerHealth)

    val mapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@RunSave::currentNodeIndex
        override var lastNodeIndex: Int? by this@RunSave::lastNodeIndex
        override val currentMapName: String = "run_map"
        override val currentMap: DetailMap by this@RunSave::map
    }

    private var _backpack: MutableList<String> by DataDelegate(RunSaveData::backpack)
    val backpack: List<String>
        get() = _backpack

    fun dirty() {
        profile.dirty()
    }

    fun asOnj(): OnjObject = data.asOnj()

    private data class RunSaveData(
        var currentNode: Int,
        var lastNode: Int?,
        var playerHealth: Int,
        var backpack: MutableList<String>
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "currentNode" with currentNode
            "lastNode" with lastNode
            "playerHealth" with playerHealth
            "backpack" with backpack
        }

        companion object {

            fun fromOnj(onj: OnjObject): RunSaveData = RunSaveData(
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<Long>("playerHealth").toInt(),
                onj.get<OnjArray>("backpack").value.map { it.value as String }.toMutableList()
            )
        }
    }

    private inner class DataDelegate<T>(val property: KMutableProperty<T>) {

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T = property.getter.call(data)

        operator fun setValue(thisRef: Any?, p: KProperty<*>, value: T) {
            val old = getValue(thisRef, p)
            if (old === value) return
            property.setter.call(data, value)
            dirty()
        }
    }

}
