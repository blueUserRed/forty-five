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
        val list = mutableListOf<String>()
        list.addAll(jvmArgs as Collection<String>)
        list.add("-XstartOnFirstThread")
        jvmArgs = list
    }
}

val ffArgs: String by project


tasks.register<JavaExec>("createDropShadows") {
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true

    args = mutableListOf("createDropShadows")
    delete("../assets/drop_shadows")
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
    from(
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    )
    with(tasks.getByName("jar") as CopySpec)
}

tasks.named<Jar>("dist") {
    dependsOn("classes")
}