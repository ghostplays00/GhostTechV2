plugins {
    id("xyz.jpenilla.run-paper")
}

dependencies {
    // Everything the API exposes is compiled against, never bundled.
    compileOnly("io.papermc.paper:paper-api:${rootProject.property("paperApiVersion")}")
    compileOnly("dev.triumphteam:triumph-gui:${rootProject.property("triumphVersion")}")
    compileOnly("org.xerial:sqlite-jdbc:${rootProject.property("sqliteVersion")}")

    // The API *is* bundled so modules only need the plugin jar at runtime.
    implementation(project(":ghosttech-api"))
}

tasks.named<Jar>("jar") {
    from(project(":ghosttech-api").sourceSets["main"].output)

    manifest {
        attributes(
            "Implementation-Title" to "GhostTechV2",
            "Implementation-Version" to project.version.toString(),
            "GhostTech-Api-Version" to rootProject.property("apiVersion").toString(),
        )
    }
}

tasks.named<ProcessResources>("processResources") {
    val tokens = mapOf(
        "version" to project.version,
        "description" to rootProject.property("description"),
        "apiVersion" to rootProject.property("apiVersion"),
    )
    inputs.properties(tokens)
    filesMatching("plugin.yml") {
        expand(tokens)
    }
}

// Dev-server task: `./gradlew :ghosttech-plugin:runServer` downloads Paper and starts it
// with this plugin plus every built module jar deployed into plugins/GhostTech/modules.
tasks.named<xyz.jpenilla.runpaper.task.RunServer>("runServer") {
    minecraftVersion(providers.gradleProperty("minecraftVersion").get())
    runDirectory.set(layout.buildDirectory.dir("run").get().asFile)
    jvmArgs("-Xms2G", "-Xmx2G", "-Dcom.mojang.eula.agree=true")
}