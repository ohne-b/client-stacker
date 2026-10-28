plugins { id("net.fabricmc.fabric-loom-remap") }

val minecraftVersion = property("minecraft_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-fabric"
group = property("maven_group").toString()
base.archivesName = "client-stacker"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
    maven("https://repo.sleeping.town/") { content { includeGroup("folk.sisby") } }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:${property("fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api")}")
    implementation(project(":core"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
    withSourcesJar()
}

sourceSets.main { java.exclude("**/NeoForgeClient.java") }

if (minecraftVersion == "1.21.11") {
    if (providers.gradleProperty("vtpTest").isPresent) {
        dependencies {
            modLocalRuntime("maven.modrinth:trading-plus:j6SJOwxf")
            // Loom does not put a dependency's nested library on the development classpath.
            runtimeOnly("folk.sisby:kaleido-config:0.3.3+1.3.2")
        }
    }
    fabricApi {
        configureTests {
            createSourceSet = true
            modId = "client_stacker_tests"
            enableGameTests = false
            enableClientGameTests = true
            clearRunDirectory = false
            username = "StackerTest"
        }
    }
    sourceSets.named("gametest") {
        java.setSrcDirs(listOf(rootProject.file("src/gametest/java")))
        resources.setSrcDirs(listOf(rootProject.file("src/gametest/resources")))
    }
}

stonecutter {
    replacements.string {
        direction = eval(current.version, ">1.21.10")
        replace("ResourceLocation", "Identifier")
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    filesMatching("fabric.mod.json") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "java" to 21)
    }
    exclude("META-INF/neoforge.mods.toml")
}

tasks.jar {
    dependsOn(":core:classes")
    from(project(":core").layout.buildDirectory.dir("classes/java/main"))
    from(rootProject.file("LICENSE.md"))
}
