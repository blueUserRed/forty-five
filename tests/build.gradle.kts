java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

val mainClassName by extra("com.microwavestudios.fortyfive.TestLauncher")
val assetsDir: File by extra(file("../assets"))

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("src/"))
    }
}

eclipse {
    project {
        name = "${rootProject.extra["appName"]}-tests"
    }
}

tasks.register<JavaExec>("run-tests") {
    group = "development"
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("debug-tests") {
    group = "development"
    dependsOn("classes")
    mainClass.set(mainClassName)
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
    workingDir = assetsDir
    isIgnoreExitValue = true
    debug = true
}

