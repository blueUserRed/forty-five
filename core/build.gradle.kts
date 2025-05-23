java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

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
        name = "${rootProject.extra["appName"]}-core"
    }
}