import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("eclipse")
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.2.20"
}

buildscript {
    val kotlinVersion by extra("2.2.20")
    val gdxVersion by extra("1.11.0")
    val gdxControllersVersion by extra("2.0.1")

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        google()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
    }
}


allprojects {
    apply(plugin = "eclipse")

    extra.apply {
        set("appName", "Forty-Five")
        set("gdxControllersVersion", "2.2.1")
    }

    repositories {
        mavenLocal()
        mavenCentral()
        google()
        gradlePluginPortal()
        maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
        maven { url = uri("https://oss.sonatype.org/content/repositories/releases/") }
    }
}

project(":desktop") {

    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "java")

    val gdxVersion = rootProject.extra["gdxVersion"]
    val gdxControllersVersion = rootProject.extra["gdxControllersVersion"]
    dependencies {
        implementation(project(":core"))
        api("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
        api("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")
        implementation("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-desktop")
        implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
        implementation("com.badlogicgames.gdx-controllers:gdx-controllers-desktop:${gdxControllersVersion}")
    }
}

project(":onj") {
    apply(plugin = "kotlin")
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.0")
    }
}

project(":core") {
    apply(plugin = "kotlin")
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
        sourceSets.all {
            languageSettings.enableLanguageFeature("ExplicitBackingFields")
        }
    }
    val gdxControllersVersion = rootProject.extra["gdxControllersVersion"]
    dependencies {
        val gdxVersion = rootProject.extra["gdxVersion"]
        implementation(project(":onj"))
        api("com.badlogicgames.gdx:gdx:$gdxVersion")
        implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
        implementation("com.code-disaster.steamworks4j:steamworks4j:1.9.0")
        implementation("com.badlogicgames.gdx:gdx-tools:$gdxVersion")
        implementation("com.badlogicgames.gdx-controllers:gdx-controllers-core:${gdxControllersVersion}")
    }
}