import org.gradle.kotlin.dsl.sourceSets

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
    
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

sourceSets {
    named("main") {
        java.setSrcDirs(listOf("Onj/src/main/kotlin"))
    }
}