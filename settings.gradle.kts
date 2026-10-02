pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
}

rootProject.name = "GhostTechV2"

// Core plugin API - compiled against by modules, shaded into the plugin jar.
include("ghosttech-api")

// The plugin itself: all runtime logic.
include("ghosttech-plugin")

// Example module, proving the module API is usable from a separate jar.
include("modules:example-module")

project(":modules:example-module").projectDir = file("modules/example-module")

// ":modules" only exists to give the module a directory namespace. Point it at a build file that
// does not exist so it gets no plugins applied and produces no empty jar.
project(":modules").buildFileName = "modules-container.gradle.kts"
