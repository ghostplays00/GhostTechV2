dependencies {
    // A module compiles against the API only. It never sees the plugin internals.
    compileOnly(project(":ghosttech-api"))
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
}

// Modules are plain jars, loaded by GhostTech at runtime from plugins/GhostTech/modules/.
tasks.named<Jar>("jar") {
    manifest {
        attributes(
            "Implementation-Title" to "GhostTechV2-ExampleModule",
            "Implementation-Version" to project.version.toString(),
        )
    }
}