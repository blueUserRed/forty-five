package com.microwavestudios.fortyfive.config

import com.microwavestudios.fortyfive.run.Run
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject
import java.io.File

class RunConfig {

    fun loadRun(name: String): Run {
        val runFile = File("$runDirectory/$name.onj")
        if (!runFile.exists()) throw RuntimeException("no run with name '$name' found")
        val onj = OnjParser.parseFile(runFile)
        runSchema.assertMatches(onj)
        onj as OnjObject
        assert(name == onj.get<String>("name")) { "names of special/progress runs need to match file name" }
        return Run.fromOnj(onj)
    }

    companion object {
        const val runDirectory: String = "maps/runs"
        const val runSchemaFile: String = "imports/run.onjschema"

        val runSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(runSchemaFile)
        }
    }

}
