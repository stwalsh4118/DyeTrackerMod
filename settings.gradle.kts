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
        // 26.x line (mc-26x branch): 26.1.2 (PBI 59) and 26.2 (PBI 53). 1.21.x stays on `main`.
        // 26.1.2 is the checked-in source state; 26.2 differences are Stonecutter version comments.
        versions("26.1.2", "26.2")
        vcsVersion = "26.1.2"
    }
}

rootProject.name = "dye-tracker-mod"
