pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.4"
}

stonecutter {
    create(rootProject) {
        // 26.x line (mc-26x branch), pinned to 26.1.2. 1.21.x stays on `main`. 26.2 is added in PBI-53.
        versions("26.1.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "dye-tracker-mod"
