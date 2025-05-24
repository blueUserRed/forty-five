import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/"))
        resources.setSrcDirs(listOf("../assets"))
    }
}

val mainClassName by extra("com.fourinachamber.fortyfive.DesktopLauncher")
val assetsDir: File by extra(file("../assets"))


eclipse {
    project {
        name = "${rootProject.extra["appName"]}-desktop"
    }
}


tasks.register<JavaExec>("run") {
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true

    if (OperatingSystem.current() == OperatingSystem.MAC_OS) {
        // Required to run on macOS
        val list = mutableListOf<String>("-XstartOnFirstThread")
        list.addAll(jvmArgs as Collection<String>)
        jvmArgs = list
    }
}


tasks.register<JavaExec>("debug") {
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
    debug = true
}

tasks.register<Jar>("dist") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = mainClassName
    }
    dependsOn(configurations.runtimeClasspath)
    configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    }
    with(tasks.getByName("jar") as CopySpec)
}

//tasks.register<Zip>("createSendableZip") { //was not converted, is outdated
//    from("launch4j/")
//    include("*")
//    include("*/*") //to include contents of a folder present inside Reports directory
//    archiveFileName.set("forty-five.zip")
//    destinationDirectory.set(file(layout.buildDirectory.dir("launch4j")))
//}

tasks.register<Copy>("copyFilesToExe") {
    from(layout.buildDirectory.dir("resources/main"))
    into(layout.buildDirectory.dir("launch4j"))
}

tasks.register("createExeFile") {
    launch4j {
        mainClassName = "com.fourinachamber.fortyfive.DesktopLauncher"
        icon = "${projectDir}/icons/myApp.ico"
        headerType = "gui"
    }
    dependsOn(tasks.named("launch4j"))
    dependsOn(tasks.named("build"))
    dependsOn(tasks.named("copyFilesToExe"))
//        dependsOn(tasks.named("createSendableZip"))
//        logger.lifecycle("my debug message: ${layout.buildDirectory}")
}

tasks.named("dist") {
    dependsOn("classes")
}