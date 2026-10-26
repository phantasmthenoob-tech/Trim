plugins {
    `java-library`
}

group = "com.minetranslate"
version = "1.0.0"
description = "Google Translate for Minecraft - translate chat, signs and books on Paper 1.21"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

val paperApi = "io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT"

dependencies {
    // Paper API is provided by the server at runtime.
    compileOnly(paperApi)
    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:unchecked"))
}

tasks.processResources {
    val props = mapOf("version" to project.version.toString())
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        showStackTraces = true
        showExceptions = true
    }
}

tasks.jar {
    archiveBaseName = "MineTranslate"
}

// Keeps releases/MineTranslate-latest.jar in sync with every build.
val releaseDir = layout.projectDirectory.dir("releases")
val releaseJar = tasks.register<Copy>("releaseJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    into(releaseDir)
    rename { "MineTranslate-latest.jar" }
}

tasks.build {
    dependsOn(releaseJar)
}
