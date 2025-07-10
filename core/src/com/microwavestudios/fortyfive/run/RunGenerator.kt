package com.microwavestudios.fortyfive.run

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import onj.value.OnjNamedObject

class RunGenerator {

    fun generateRun(forDifficulty: Int, forBiome: String, type: RunType): Run {
        val onj = ConfigFileManager.getConfigFile("mapConfig")
        val mapGenOnj = onj.access<OnjNamedObject>(".generatorConfig.maps.0")
        val mapGen = BaseMapGenerator.fromOnj(mapGenOnj)
        return Run(
            RunLength.MEDIUM,
            type,
            forDifficulty,
            listOf(RunReward.Cash(100)),
            forBiome,
            mapGen
        )
    }

}