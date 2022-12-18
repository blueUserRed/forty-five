package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.Gdx
import onj.OnjNamedObject
import onj.OnjObject
import onj.OnjParser
import onj.OnjSchemaParser
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


object FourtyFiveLogger {

    const val configFilePath = "logging/log_config.onj"
    const val schemaFilePath = "onjschemas/log_config.onjschema"

    private lateinit var output: PrintStream
    private var logLevel: LogLevel = LogLevel.DEBUG

    private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val detailTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")

    fun init() {
        val config = OnjParser.parseFile(Gdx.files.internal(configFilePath).file())
        val schema = OnjSchemaParser.parseFile(Gdx.files.internal(schemaFilePath).file())
        schema.assertMatches(config)
        config as OnjObject

        output = outputOrError(config.get<OnjNamedObject>("logTarget"))
        logLevel = logLevelOrError(config.get<String>("logLevel"))

        val time = detailTimeFormatter.format(LocalDateTime.now())

        output.println("""
           **** fourty-five log
           **** produced by version '${config.get<String>("versionTag")}'
           **** LogLevel is $logLevel
           **** time is $time
          
        """.trimIndent())
    }

    fun debug(tag: String, message: String) {
        if (logLevel != LogLevel.DEBUG) return
        output.println(formatMessage(tag, message, LogLevel.DEBUG))
    }

    fun medium(tag: String, message: String) {
        if (logLevel != LogLevel.DEBUG && logLevel != LogLevel.MEDIUM) return
        output.println(formatMessage(tag, message, LogLevel.MEDIUM))
    }

    fun severe(tag: String, message: String) {
        output.println(formatMessage(tag, message, LogLevel.SEVERE))
    }

    fun stackTrace(e: Exception) {
        e.printStackTrace(output)
    }

    fun title(message: String) {
        output.println("-------------$message-------------")
    }

    fun fps() {
        if (logLevel != LogLevel.DEBUG) return
        output.println("-[fps]- ${Gdx.graphics.framesPerSecond}")
    }

    private fun formatMessage(tag: String, message: String, level: LogLevel): String {
        //TODO: this could use TemplateString
        val time = messageTimeFormatter.format(LocalDateTime.now())
        return "[$time $tag] $message"
    }

    private fun outputOrError(config: OnjNamedObject): PrintStream = when (val name = config.name) {

        "Console" -> System.out
        "File" -> {
            val path = config.get<String>("path")
            val file = Gdx.files.local(path).file()
            if (file.exists()) file.delete()
            file.createNewFile()
            PrintStream(file)
        }

        else -> throw RuntimeException("unknown log target: $name")
    }

    private fun logLevelOrError(logLevel: String): LogLevel = when (logLevel) {
        "debug" -> LogLevel.DEBUG
        "medium" -> LogLevel.MEDIUM
        "severe" -> LogLevel.SEVERE
        else -> throw RuntimeException("unknown log level: $logLevel")
    }

    enum class LogLevel {
        DEBUG {
            override fun toString(): String = "debug"
        },
        MEDIUM {
            override fun toString(): String = "medium"
        },
        SEVERE {
            override fun toString(): String = "severe"
        }

        ;

        abstract override fun toString(): String
    }

}