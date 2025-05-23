
plugins {
    id("eclipse")
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.20" // apply false
    id ("edu.sc.seis.launch4j") version "2.5.4" //apply false
}


buildscript {
    val kotlinVersion by extra("2.0.20")
    val gdxVersion by extra("1.11.0")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
        classpath("edu.sc.seis.launch4j:launch4j:2.5.4")
    }
}


allprojects {
    apply(plugin = "eclipse")

    extra.apply{
        set("appName", "Forty-Five")
        set("roboVMVersion", "2.3.16")
        set("box2DLightsVersion", "1.5")
        set("ashleyVersion", "1.7.4")
        set("aiVersion", "1.8.2")
        set("gdxControllersVersion", "2.2.1")
    }

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
        maven { url = uri("https://jitpack.io") }
    }
}

project(":desktop") {

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")
    apply(plugin = "edu.sc.seis.launch4j")

    launch4j {
        mainClassName = "com.fourinachamber.fortyfive.DesktopLauncher"
        icon = "${projectDir}/icons/myApp.ico"
        headerType = "gui"
    }
    val gdxVersion = rootProject.extra["gdxVersion"]
    dependencies {
        implementation(project(":core"))
        api("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
        api("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
        implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
        implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
    }
}

project(":onj") {
    apply(plugin = "kotlin")
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.0")
    }
}

project(":core") {
    apply(plugin = "kotlin")
    kotlin {
        sourceSets.all {
            languageSettings.enableLanguageFeature("ExplicitBackingFields")
        }
    }
    dependencies {
        val gdxVersion = rootProject.extra["gdxVersion"]
        implementation(project(":onj"))
        api("com.badlogicgames.gdx:gdx:$gdxVersion")
        implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
        api("io.github.libktx:ktx-actors:1.11.0-rc2")
        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        api("com.github.lyze237:gdx-FlexBox:425149b588")
        api("com.github.lyze237:gdx-FlexBox:425149b588:sources")
        implementation("com.code-disaster.steamworks4j:steamworks4j:1.9.0")
    }
}