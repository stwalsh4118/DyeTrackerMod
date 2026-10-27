plugins {
    id("dev.kikugie.stonecutter")
    // 26.1 is unobfuscated: use the no-remap Loom plugin id (NOT the old `fabric-loom`/`-remap`).
    // See docs/delivery/50/research/mc-26x-toolchain-guide.md §1.
    id("net.fabricmc.fabric-loom") version "1.16.2"
    id("org.jetbrains.kotlin.jvm") version "2.3.21"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
}

version = "${property("mod_version")}+${stonecutter.current.version}"
group = property("maven_group") as String

base {
    archivesName.set(property("archives_base_name") as String)
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
}

dependencies {
    // Minecraft version comes from the active Stonecutter subproject name.
    minecraft("com.mojang:minecraft:${stonecutter.current.version}")
    // No `mappings(...)` line: 26.1 is unobfuscated, and the no-remap Loom plugin needs no
    // obfuscation mappings (officialMojangMappings() does NOT apply here). See guide §2.

    // Fabric Loader — no-remap plugin uses plain `implementation`, not `modImplementation`.
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // Fabric Language Kotlin (includes kotlinx.serialization)
    implementation("net.fabricmc:fabric-language-kotlin:${property("fabric_kotlin_version")}")

    // Unit testing (pure JVM; no Minecraft/Fabric runtime).
    // See docs/delivery/33/33-2-junit-guide.md for rationale and coordinates.
    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

loom {
    // Mandatory for the Stonecutter shared-source layout — without this, Loom expects
    // fabric.mod.json under each version subproject's resources rather than the shared root.
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")
}

tasks.processResources {
    // Each node claims only its own Minecraft version (versions/<mc>/gradle.properties).
    val minecraftDependency = project.property("minecraft_dependency")
    inputs.property("version", project.version)
    inputs.property("minecraft_dependency", minecraftDependency)

    filesMatching("fabric.mod.json") {
        expand(mapOf("version" to project.version, "minecraft_dependency" to minecraftDependency))
    }
}

tasks.withType<JavaCompile> {
    options.release.set(25)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25)
    }
}

java {
    // Minecraft 26.1 requires Java 25. The toolchain pins the build JVM to JDK 25
    // (auto-detected from SDKMAN); `main` has no toolchain decl and stays on Java 21.
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName.get()}" }
    }
}
