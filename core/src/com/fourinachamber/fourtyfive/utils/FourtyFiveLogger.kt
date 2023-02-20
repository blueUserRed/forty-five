package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.Gdx
import com.sun.jdi.BooleanValue
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * utility for logging to the console or to files
 */
object FourtyFiveLogger {

    /**
     * file containing the config for this logger
     */
    const val configFilePath = "logging/log_config.onj"

    /**
     * file containing the schema for the config file
     */
    const val schemaFilePath = "onjschemas/log_config.onjschema"

    private lateinit var output: PrintStream
    private var logLevel: LogLevel = LogLevel.DEBUG

    private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val detailTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")

    /**
     * initializes the logger
     */
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

    /**
     * logs a debug message
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    fun debug(tag: String, message: String) {
        if (!logLevel.shouldLog(LogLevel.DEBUG)) return
        output.println(formatMessage(tag, message, LogLevel.DEBUG))
    }

    /**
     * logs a message with medium priority
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    fun medium(tag: String, message: String) {
        if (!logLevel.shouldLog(LogLevel.MEDIUM)) return
        output.println(formatMessage(tag, message, LogLevel.MEDIUM))
    }

    /**
     * logs a message with information about some kind of severe failure
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    fun severe(tag: String, message: String) {
        output.println(formatMessage(tag, message, LogLevel.SEVERE))
    }

    /**
     * logs a message
     * @param level the level of the message
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    fun log(level: LogLevel, tag: String, message: String) {
        if (!logLevel.shouldLog(level)) return
        output.println(formatMessage(tag, message, level))
    }

    /**
     * logs larger stings that contain multiple line breaks
     * @param level the level of the message
     */
    fun dump(level: LogLevel, message: String, title: String? = null) {
        if (!logLevel.shouldLog(level)) return
        title?.let { output.println("-> $it:") }
        val lines = message.lines()
        val max = lines.size.toString().length
        lines.forEachIndexed { index, line ->
            output.println("${(index + 1).toString().padStart(max, '0')}| $line")
        }
    }

    /**
     * logs the stackTrace of an exception. will always be logged, regardless of the log level
     */
    fun stackTrace(e: Exception) {
        e.printStackTrace(output)
    }

    /**
     * logs a message formatted as a title. This should be used when a big change in the state of the game happens
     * and makes navigating the log file easier
     */
    fun title(message: String) {
        output.println("-------------$message-------------")
    }

    /**
     * logs the current frameRate, only logs when the logLevel is set to debug
     */
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

    /**
     * possible log levels
     */
    enum class LogLevel {
        DEBUG {
            override fun toString(): String = "debug"
            override fun shouldLog(logLevel: LogLevel): Boolean = true
        },
        MEDIUM {
            override fun toString(): String = "medium"
            override fun shouldLog(logLevel: LogLevel): Boolean = logLevel in arrayOf(SEVERE, MEDIUM)
        },
        SEVERE {
            override fun toString(): String = "severe"
            override fun shouldLog(logLevel: LogLevel): Boolean = logLevel == SEVERE
        }

        ;

        /**
         * checks if a message with the level [logLevel] should be logged when the current logLevel is `this`
         */
        abstract fun shouldLog(logLevel: LogLevel): Boolean

        abstract override fun toString(): String
    }

}