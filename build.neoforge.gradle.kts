plugins { id("net.neoforged.moddev") }

val minecraftVersion = property("minecraft_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-neoforge"
group = property("maven_group").toString()
base.archivesName = "client-stacker"

repositories { mavenCentral() }

dependencies { implementation(project(":core")) }

java {
    toolchain.languageVersion = JavaLanguageVersion.of(if (minecraftVersion.startsWith("26.")) 25 else 21)
    withSourcesJar()
}

neoForge {
    version = property("neoforge_version").toString()
    runs {
        register("client") { client(); gameDirectory = file("run") }
    }
    mods { register("client_stacker") { sourceSet(sourceSets.main.get()) } }
}

sourceSets.main { java.exclude("**/FabricClient.java") }

stonecutter {
    replacements.string {
        direction = eval(current.version, ">1.21.10")
        replace("ResourceLocation", "Identifier")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version, "minecraft" to minecraftVersion)
    }
    exclude("fabric.mod.json")
}

tasks.jar {
    dependsOn(":core:classes")
    from(project(":core").layout.buildDirectory.dir("classes/java/main"))
    from(rootProject.file("LICENSE.md"))
}
