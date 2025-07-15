package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.profile.Profile.Companion.dataFileSchema
import com.microwavestudios.fortyfive.run.Run
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class RunSave private constructor(val profile: Profile) {

    private lateinit var data: RunSaveData

    private var dirty: Boolean = true

    var currentNodeIndex: Int by DataDelegate(RunSaveData::currentNode)
    var lastNodeIndex: Int? by DataDelegate(RunSaveData::lastNode)
    var playerHealth: Int by DataDelegate(RunSaveData::playerHealth)

    var run: Run by DataDelegate(RunSaveData::run)
        private set

    val mapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@RunSave::currentNodeIndex
        override var lastNodeIndex: Int? by this@RunSave::lastNodeIndex
        override val currentMapName: String = "run_map"
        override val currentMap: DetailMap by this@RunSave::map
    }

    val runMapFile: File = File(profile.profilePath.path + "/runMap.onj")
    val runDataFile: File = File(profile.profilePath.path + "/run_data.onj")

    private var _backpack: MutableList<String> by DataDelegate(RunSaveData::backpack)
    val backpack: List<String>
        get() = _backpack

    private lateinit var map: DetailMap

    fun dirty() {
        dirty = true
    }

    private fun loadMap() {
        map = DetailMap.readFromFile(runMapFile)
    }

    fun readFromDisc() {
        dirty = false
        if (!runDataFile.exists()) {
            runDataFile.createNewFile()
            runDataFile.writeText(data.asOnj().toString())
            return
        }
        val onj = OnjParser.parseFile(runDataFile)
        dataFileSchema.check(onj)
        onj as OnjObject
        data = RunSaveData.fromOnj(onj)
    }

    fun write() {
        if (!dirty) return
        if (!runDataFile.exists()) {
            runDataFile.createNewFile()
        }
        runDataFile.writeText(data.asOnj().toString())
    }

    fun writeRunMap() {
        runMapFile.writeText(map.asOnjObject().toMinifiedString())
    }

    data class RunSaveData(
        var currentNode: Int,
        var lastNode: Int?,
        var playerHealth: Int,
        var backpack: MutableList<String>,
        var run: Run
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "currentNode" with currentNode
            "lastNode" with lastNode
            "playerHealth" with playerHealth
            "backpack" with backpack
            "run" with run.asOnj()
        }

        companion object {

            fun fromOnj(onj: OnjObject): RunSaveData = RunSaveData(
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<Long>("playerHealth").toInt(),
                onj.get<OnjArray>("backpack").value.map { it.value as String }.toMutableList(),
                Run.fromOnj(onj.get<OnjObject>("run"))
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

    companion object {

        fun load(profile: Profile): RunSave? {
            val dataFile = File(profile.profilePath.path + "/run_data.onj")
            if (!dataFile.exists()) return null
            val save = RunSave(profile)
            save.readFromDisc()
            save.loadMap()
            return save
        }

        fun newRun(profile: Profile, run: Run): RunSave {
            val mapGenerator = run.mapGenerator
            val map = mapGenerator.generate("run_map", TimeUtils.millis())
            val save = RunSave(profile)
            save.data = RunSaveData(0, null, 100, mutableListOf(), run)
            save.write()
            val runMapFile = save.runMapFile
            if (runMapFile.exists()) runMapFile.delete()
            runMapFile.createNewFile()
            runMapFile.writeText(map.asOnjObject().toMinifiedString())
            save.map = map
            return save
        }

    }

}
