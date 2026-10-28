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

if (providers.gradleProperty("smokeTest").isPresent) {
    apply(from = rootProject.file("gradle/smoke.gradle.kts"))
    loom.mods {
        register("client_stacker") { sourceSet(sourceSets.main.get()) }
        register("client_stacker_smoke") { sourceSet(sourceSets["smoke"]) }
    }
    loom.runs.named("client") {
        source(sourceSets["smoke"])
        vmArg("-Dclientstacker.smoke.result=${rootProject.file("artifacts/smoke-${project.name}.txt").absolutePath}")
        vmArg("-Dclientstacker.smoke.automation=true")
    }
}

if (providers.gradleProperty("vtpTest").isPresent) {
    dependencies {
        modLocalRuntime("maven.modrinth:trading-plus:${property("vtp_test_version")}")
        // Loom omits the dependency's nested library from the development classpath.
        if (minecraftVersion == "1.21.11") runtimeOnly("folk.sisby:kaleido-config:0.3.3+1.3.2")
    }
}

if (minecraftVersion == "1.21.11") {
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
    loom.runs.named("clientGameTest") {
        vmArg("-Dclientstacker.test.artifacts=${rootProject.file("artifacts").absolutePath}")
        // Use real packet timing; Fabric's packet synchronizer can stall world startup.
        vmArg("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
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

tasks.named<Jar>("sourcesJar") {
    from(rootProject.file("core/src/main/java"))
    from(rootProject.file("LICENSE.md"))
}
