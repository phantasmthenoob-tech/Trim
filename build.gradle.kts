plugins {
    `java-library`
}

group = "com.unbound"
version = "1.3.0"
description = "Universal enchantment mechanics for the Unbound SMP"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

val paperApi = "io.papermc.paper:paper-api:26.2.build.129-stable"

dependencies {
    // Paper API is provided by the server at runtime.
    compileOnly(paperApi)
    // Unit tests exercise Bukkit types (Material, ItemStack, Vector...) directly.
    testImplementation(paperApi)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Needed to test config parsing against real YAML text.
    testImplementation("org.yaml:snakeyaml:2.2")
}

java {
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
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
    archiveBaseName = "Unbound"
}

// Keeps releases/Unbound-latest.jar in sync with every build, so the
// repository always carries the current plugin jar alongside the source.
val releaseDir = layout.projectDirectory.dir("releases")
val releaseJar = tasks.register<Copy>("releaseJar") {
    dependsOn(tasks.jar)
    from(tasks.jar.map { it.archiveFile })
    into(releaseDir)
    rename { "Unbound-latest.jar" }
}

tasks.build {
    dependsOn(releaseJar)
}
