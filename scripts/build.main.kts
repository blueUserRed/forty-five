import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.system.exitProcess

val assetDirs = arrayOf(
    "blobs",
    "drop_shadows",
    "config",
    "error_logs",
    "imports",
    "logging",
    "maps",
    "onjschemas",
    "profiles",
    "plugins",
    "shaders",
    "test_logs",
    "test_maps",
    "logo.png",
    "steam_appid.txt",
)

val trustedPluginsDir = "../trustedPlugins"
val trustedPlugins = arrayOf(
    PluginConfig("moreAnims", install = true, enableByDefault = true)
)

build()

fun build() {
    if (!isInCorrectDirectory()) {
        error("The build script should be executed in the root of the project\nCurrent dir: ${File(".").canonicalPath}")
    }
    val (tag, dirName, tmpDir, convertToExe) = interviewUser()
    debug("running gradle dist task")
    command("./gradlew.bat", "dist")
    if (tmpDir.exists()) tmpDir.deleteRecursively()
    tmpDir.mkdirs()
    copyJar(tmpDir)
    cleanupJar(tmpDir)
    copyAssets(tmpDir)
    cleanupAssets(tmpDir)
    setupPlugins(tmpDir)
    changeLoggingVersionTag(tmpDir, tag)
    if (convertToExe) {
        copyJre(tmpDir)
        createExe(tmpDir)
        debug("removing jar")
        (tmpDir / "forty-five.jar").delete()
    }

    debug("compressing build")
    shellCommand(tmpDir, "7z", "a", "$dirName.zip", "./*")

    debug("copying archive")
    val outputFile = File("build/script/$dirName.zip")
    if (outputFile.exists()) outputFile.delete()
    (tmpDir / "$dirName.zip").copyTo(outputFile)

    debug("removing tmp directories")
    tmpDir.deleteRecursively()

    println("\u001B[34mBuild successful. Finished Build can be found at: build/script/$dirName.zip\u001B[0m")
}

fun interviewUser(): BuildConfig {
    val isReleaseBuild = askYesNoQuestion("Is this a release build?", default = false)
    if (isReleaseBuild) return BuildConfig.create("rc", true)
    val letter = ask("What build string do you want to use?", default = "b")
    val convertToExe = askYesNoQuestion("Do you want to convert the result to an .exe?", default = true)
    return BuildConfig.create(letter, convertToExe)
}

fun copyJar(tmpDir: File) {
    debug("copying jar file")
    File("desktop/build/libs/desktop.jar").copyTo(tmpDir / "forty-five.jar")
}

fun cleanupJar(tmpDir: File) {
    debug("cleaning up jar file")
    shellCommand(tmpDir, "7z", "d", "forty-five.jar", *assetDirs)
}

fun copyAssets(tmpDir: File) {
    debug("copying assets")
    File("assets").copyRecursively(tmpDir)
    File("LICENSE").copyTo(tmpDir / "LICENSE")
    File("readme.md").copyTo(tmpDir / "readme.md")
}

fun setupPlugins(tmpDir: File) {
    val trustedPluginsDir = File(trustedPluginsDir)
    val pluginsInstallDir = tmpDir / "plugins"
    val onjFile = StringBuilder()
    onjFile.newLine().append("""
        // This file contains Information that allows the game to identify trusted (official) plugins.
        // Changes made here may cause the game to trust/automatically enable untrusted plugins and run foreign code!
        // Do not edit!
    """.trimIndent()).newLine().newLine()
    onjFile.append("plugins: [").newLine()
    debug("configuring trusted plugins")
    trustedPlugins.forEach { plugin ->
        val pluginDir = trustedPluginsDir / plugin.dir
        if (!pluginDir.exists()) error("no dir for plugin ${plugin.name}")
        val jars = pluginDir
            .listFiles()!!
            .filter { it.extension == "jar" }
        if (jars.size != 1) error("trusted plugin must contain exactly one jar at the top level")
        val jar = jars.first()
        val pluginConfigFile = pluginDir / "plugin.onj"
        debug("hashing files for plugin ${plugin.name}")
        val jarHash = hashFile(jar)
        val configHash = hashFile(pluginConfigFile)
        if (plugin.install) {
            debug("installing plugin ${plugin.name}")
            pluginDir.copyRecursively(pluginsInstallDir / pluginDir.name)
        }
        onjFile.indent().append("{").newLine()
        onjFile
            .indent(2).append("name: \"").append(plugin.name).append("\",").newLine()
            .indent(2).append("jarFileShaHash: \"").append(jarHash).append("\",").newLine()
            .indent(2).append("configFileHash: \"").append(configHash).append("\",").newLine()
            .indent(2).append("enableByDefault: ").append(plugin.enableByDefault).append(",").newLine()
        onjFile.indent().append("},").newLine()
    }
    onjFile.append("]").newLine()
    val configFile = tmpDir / "config/trusted_plugins.onj"
    configFile.createNewFile()
    configFile.writeText(onjFile.toString())
}

fun cleanupAssets(tmpDir: File) {
    debug("cleaning up assets...")
    debug("removing extra jre")
    (tmpDir / "blobs/jre").deleteRecursively()

    debug("removing error logs")
    (tmpDir / "error_logs")
        .listFiles()?.forEach { it.delete() }

    debug("removing raw animation frames")
    (tmpDir / "blobs/animations")
        .listFiles()
        ?.filter { it.name != "packed" }
        ?.forEach { it.deleteRecursively() }

    debug("removing test dirs")
    (tmpDir / "blobs/test_logs").deleteRecursively()
    (tmpDir / "blobs/test-maps").deleteRecursively()

    debug("removing log file")
    (tmpDir / "logging/forty-five.log").delete()

    debug("removing profiles")
    (tmpDir / "profiles")
        .listFiles()!!
        .forEach { it.deleteRecursively() }

    debug("removing onj env")
    (tmpDir / ".onj")
        .deleteRecursively()

    debug("removing plugins")
    (tmpDir / "plugins")
        .listFiles()
        ?.forEach { it.deleteRecursively() }

    debug("removing font files")
    (tmpDir / "blobs/fonts")
        .listFiles()!!
        .filter { it.extension in arrayOf("ttf", "otf") }
        .forEach { it.delete() }

    (tmpDir / "error_logs").let { errorLogs ->
        if (!errorLogs.exists()) errorLogs.mkdir()
    }
}

fun changeLoggingVersionTag(tmpDir: File, newTag: String) {
    debug("changing version tag in log_config.onj")
    val logConfig = (tmpDir / "logging/log_config.onj").readText()
    val newConfig = logConfig.replace("--dev--", newTag)
    (tmpDir / "logging/log_config.onj").writeText(newConfig)
}

fun copyJre(tmpDir: File) {
    debug("copying jre")
    File("assets/blobs/jre").copyRecursively(tmpDir / "jre")
}

fun createExe(tmpDir: File) {
    debug("copying launch4j config file")
    File("scripts/launch4j_config.xml").copyTo(tmpDir / "launch4j_config.xml")
    debug("invoking launch4j")
    shellCommand(tmpDir, "launch4jc", "launch4j_config.xml")
    debug("removing launch4j config file")
    (tmpDir / "launch4j_config.xml").delete()
}

data class BuildConfig(
    val tag: String,
    val dirName: String,
    val tmpDir: File,
    val convertToExe: Boolean
) {
    companion object {
        fun create(letter: String, convertToExe: Boolean): BuildConfig {
            val date = SimpleDateFormat("yyMMdd").format(Date())
            val tag = "$letter$date"
            var dirName = tag
            if (!convertToExe) dirName += "-jar"
            return BuildConfig(tag, dirName, File("build/script/$dirName"), convertToExe)
        }
    }
}

data class PluginConfig(
    val dir: String,
    val name: String = dir,
    val install: Boolean = false,
    val enableByDefault: Boolean = false
)

// should be good enough
fun isInCorrectDirectory(): Boolean =
    File("LICENSE").exists() &&
    File("readme.md").exists() &&
    File("build.gradle.kts").exists()

fun shellCommand(vararg command: String) = command("powershell.exe", *command)

fun shellCommand(workingDir: File, vararg command: String) = command(workingDir, "powershell.exe", *command)

fun command(vararg command: String) = command(File("."), *command)

fun command(workingDir: File, vararg command: String) {
    val process = ProcessBuilder(*command)
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
    process.waitFor()
    if (process.exitValue() != 0) {
        error("command '${command[0]}' exited with code ${process.exitValue()}")
    }
}

fun error(message: String): Nothing {
    System.err.println(message)
    exitProcess(1)
}

fun debug(message: String) = println(message)

fun ask(question: String, default: String): String {
    println("$question [$default]")
    print("> ")
    val answer = readln()
    return answer.ifBlank { default }
}

fun askYesNoQuestion(question: String, default: Boolean): Boolean {
    println("$question y/n [${if (default) "y" else "n"}]")
    print("> ")
    val answer = readln()
    if (answer.isBlank()) return default
    return when (answer.trim().lowercase()) {
        "yes", "y", "j", "ja", "jo", "sure", "why not", "yay" -> true
        "no", "n", "nein", "na", "nah", "i dont think so" -> false
        "idk" -> Random.nextBoolean().also { println("'${if (it) "yes" else "no"}' was chosen")}
        else -> {
            println("answer 'y' or 'n'")
            askYesNoQuestion(question, default)
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun hashFile(file: File): String {
    val buffer = ByteArray(4096)
    val stream = BufferedInputStream(FileInputStream(file))
    val digest = MessageDigest.getInstance("SHA-256")
    var count = 0
    while (true) {
        count = stream.read(buffer)
        if (count <= 0) break
        digest.update(buffer, 0, count)
    }
    stream.close()
    val hash = digest.digest()
    return Base64.encode(hash)
}

operator fun File.div(childPath: String): File = File(this.canonicalPath + "/$childPath")

fun StringBuilder.newLine(): StringBuilder = append("\n")
fun StringBuilder.indent(): StringBuilder = append("    ")
fun StringBuilder.indent(amount: Int): StringBuilder {
    repeat(amount) { indent() }
    return this
}
