plugins { id("net.fabricmc.fabric-loom") }

val minecraftVersion = property("minecraft_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-fabric"
group = property("maven_group").toString()
base.archivesName = "client-stacker"

repositories { mavenCentral() }

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
    withSourcesJar()
}

sourceSets.main { java.exclude("**/NeoForgeClient.java") }

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "java" to 25)
    }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.jar {
    dependsOn(":core:classes")
    from(project(":core").layout.buildDirectory.dir("classes/java/main"))
    from(rootProject.file("LICENSE.md"))
}
