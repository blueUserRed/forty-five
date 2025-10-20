package com.microwavestudios.fortyfive.map.generation

import com.microwavestudios.fortyfive.map.DetailMap
import onj.builder.buildOnjObject
import onj.value.OnjObject
import java.io.File

class StaticMapGenerator(val name: String) : BaseMapGenerator() {

    override fun generate(name: String, seed: Long): DetailMap {
        return DetailMap.readFromFile(File("maps/static_maps/${this.name}.onj"))
    }

    override fun asOnj(): OnjObject = buildOnjObject {
        name("StaticMap")
        "name" with name
    }
}
