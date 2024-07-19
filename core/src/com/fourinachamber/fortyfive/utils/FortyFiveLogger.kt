package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.config.ConfigFileManager
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.io.PrintStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * utility for logging to the console or to files
 */
object FortyFiveLogger {

    private lateinit var outputs: List<Pair<PrintStream, Boolean>>
    private var logLevel: LogLevel = LogLevel.DEBUG

    private val messageTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private val detailTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss.SSS")

    lateinit var versionTag: String
        private set

    /**
     * initializes the logger
     */
    fun init() {
        val config = ConfigFileManager.getConfigFile("logConfig")

        outputs = config
            .get<OnjArray>("logTargets")
            .value
            .map { outputOrError(it as OnjNamedObject) }

        logLevel = logLevelOrError(config.get<String>("logLevel"))
        versionTag = config.get<String>("versionTag")

        val time = detailTimeFormatter.format(LocalDateTime.now())

        writeln("""
           **** forty-five log
           **** produced by version '$versionTag'
           **** LogLevel is $logLevel
           **** time is $time
          
        """.trimIndent())
    }

    private fun writeln(s: String) = outputs.forEach { it.first.println(s) }

    private fun writelnFormatted(tag: String, message: String, level: LogLevel) {
        //TODO: this could use TemplateString
        val time = messageTimeFormatter.format(LocalDateTime.now())

        val (beginAnsi, endAnsi) = when (level) {
            LogLevel.DEBUG -> ANSI.blue to ANSI.reset
            LogLevel.MEDIUM -> ANSI.yellow to ANSI.reset
            LogLevel.SEVERE -> ANSI.red to ANSI.reset
        }

        outputs.forEach { (stream, shouldUseAnsi) ->
            if (shouldUseAnsi) stream.println("$beginAnsi[$time $tag] $message$endAnsi")
            else stream.println("[$time $tag] $message")
        }
    }

    /**
     * logs a debug message
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    @AllThreadsAllowed
    fun debug(tag: String, message: String) {
        if (!logLevel.shouldLog(LogLevel.DEBUG)) return
        writelnFormatted(tag, message, LogLevel.DEBUG)
    }

    /**
     * logs a message with medium priority
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    @AllThreadsAllowed
    fun warn(tag: String, message: String) {
        if (!logLevel.shouldLog(LogLevel.MEDIUM)) return
        writelnFormatted(tag, message, LogLevel.MEDIUM)
    }

    /**
     * logs a message with information about some kind of severe failure
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    @AllThreadsAllowed
    fun severe(tag: String, message: String) {
        writelnFormatted(tag, message, LogLevel.SEVERE)
    }

    /**
     * logs the exception and exits the application
     */
    @AllThreadsAllowed
    fun fatal(exception: java.lang.Exception) {
        severe("fatal",  "Encountered an exception that could not be recovered from")
        stackTrace(exception)
        FortyFive.cleanExit = false
        Gdx.app.exit()
    }

    /**
     * logs the message and exits the application
     */
    fun fatal(message: String) {
        severe("fatal",  "Encountered an error that could not be recovered from")
        severe("fatal", message)
        FortyFive.cleanExit = false
        Gdx.app.exit()
    }

    /**
     * logs a message
     * @param level the level of the message
     * @param tag should give the reader information about where this message
     * came from (which class, which instance, ...)
     */
    fun log(level: LogLevel, tag: String, message: String) {
        if (!logLevel.shouldLog(level)) return
        writelnFormatted(tag, message, level)
    }

    /**
     * logs larger stings that contain multiple line breaks
     * @param level the level of the message
     */
    fun dump(level: LogLevel, message: String, title: String? = null) {
        if (!logLevel.shouldLog(level)) return
        title?.let { writeln("-> $it:") }
        val lines = message.lines()
        val max = lines.size.toString().length
        lines.forEachIndexed { index, line ->
            writeln("${(index + 1).toString().padStart(max, '0')}| $line")
        }
    }

    /**
     * logs the stackTrace of an exception. will always be logged, regardless of the log level
     */
    @AllThreadsAllowed
    fun stackTrace(e: Exception) {
        outputs.forEach { e.printStackTrace(it.first) }
    }

    /**
     * logs a message formatted as a title. This should be used when a big change in the state of the game happens
     * and makes navigating the log file easier
     */
    @AllThreadsAllowed
    fun title(message: String) {
        writeln("-------------$message-------------")
    }

    /**
     * logs the current frameRate, only logs when the logLevel is set to debug
     */
    @AllThreadsAllowed
    fun fps() {
        if (logLevel != LogLevel.DEBUG) return
        writeln("-[fps]- ${Gdx.graphics.framesPerSecond}")
    }

    private fun outputOrError(config: OnjNamedObject): Pair<PrintStream, Boolean> = when (val name = config.name) {

        "Console" -> System.out to true
        "File" -> {
            val path = config.get<String>("path")
            val file = Gdx.files.local(path).file()
            if (file.exists()) file.delete()
            file.createNewFile()
            PrintStream(file) to false
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