plugins {
    `java-library`
}

group = "com.entitystudio"
version = "0.2.0"
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
    maven("https://mvn.lib.co.nz/public/") {
        name = "libsdisguises"
    }
}

// Pinned Paper API for the 1.21.11 server target (verified 2026-10-26:
// 1.21.11-R0.1-SNAPSHOT exists upstream; Attribute.GENERIC_* constants were
// removed in this line, migrated to Attribute.MAX_HEALTH etc.).
// Do NOT float this dependency: version-sensitive internals are isolated in
// the compatibility package and tested against this exact artifact.
val paperApi = "io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT"

dependencies {
    // Paper API is provided by the server at runtime.
    compileOnly(paperApi)
    // LibsDisguises is optional at runtime (softdepend): real mob visuals
    // when installed, costume fallback otherwise. Pinned to the 1.21.11
    // line (11.0.14); match the installed build to this version.
    compileOnly("me.libraryaddict.disguises:libsdisguises:11.0.14")
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
