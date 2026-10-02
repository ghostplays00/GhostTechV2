dependencies {
    // Paper + Triumph are provided by the server at runtime (see plugin.yml `libraries:`),
    // so they must never leak into the published jar.
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly("dev.triumphteam:triumph-gui:${rootProject.property("triumphVersion")}")
    compileOnly("org.xerial:sqlite-jdbc:${rootProject.property("sqliteVersion")}")
}

// The API module publishes a plain jar. Modules put it on their compile classpath only.
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "GhostTechV2-API",
            "Implementation-Version" to project.version.toString(),
            "GhostTech-Api-Version" to rootProject.property("apiVersion").toString(),
        )
    }
}