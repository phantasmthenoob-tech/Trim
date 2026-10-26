plugins {
    `java-library`
}

group = "com.entitystudio"
version = "0.1.0"
description = "Visual entity editor for Minecraft Java 1.21.11 (Paper plugin)"

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

// Pinned Paper API for the 1.21.x runtime line (loads on 1.21 through 1.21.11).
// Do NOT float this dependency: version-sensitive internals are isolated in
// the compatibility package and tested against this exact artifact.
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
    filesMatching(listOf("plugin.yml", "config.yml")) {
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
    archiveBaseName = "EntityStudio"
}

// Keeps entity-studio/releases/EntityStudio-latest.jar in sync with every build.
val releaseDir = layout.projectDirectory.dir("releases")
val releaseJar = tasks.register<Copy>("releaseJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    into(releaseDir)
    rename { "EntityStudio-latest.jar" }
}

tasks.build {
    dependsOn(releaseJar)
}
