plugins {
    id("xyz.jpenilla.run-paper") version "3.1.0" apply false
}

// Shared coordinates for every subproject.
allprojects {
    group = "org.ghostplays"
    version = providers.gradleProperty("pluginVersion").get()
}

// ":modules" exists only to namespace the modules directory and has no build file, so it must not
// receive the java-library plugin or it emits a pointless empty jar.
val containerProjects = setOf(":modules")

subprojects {
    if (path in containerProjects) {
        return@subprojects
    }

    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    tasks.withType<Jar>().configureEach {
        // Reproducible, module-info free jars.
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
