plugins { id("net.neoforged.moddev") }

val minecraftVersion = property("minecraft_version").toString()
val neoForgeVersion = property("neoforge_version").toString()
version = "${property("mod_version")}+mc$minecraftVersion-neoforge"
group = property("maven_group").toString()
base.archivesName = "client-stacker"

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies { implementation(project(":core")) }
evaluationDependsOn(":core")

java {
    toolchain.languageVersion = JavaLanguageVersion.of(if (minecraftVersion.startsWith("26.")) 25 else 21)
    withSourcesJar()
}

neoForge {
    version = neoForgeVersion
    runs {
        register("client") { client(); gameDirectory = file("run") }
    }
    mods {
        register("client_stacker") {
            sourceSet(sourceSets.main.get())
            sourceSet(project(":core").extensions.getByType<SourceSetContainer>()["main"])
        }
    }
}

sourceSets.main { java.exclude("**/FabricClient.java") }

if (providers.gradleProperty("smokeTest").isPresent) {
    apply(from = rootProject.file("gradle/smoke.gradle.kts"))
    neoForge.mods { register("client_stacker_smoke") { sourceSet(sourceSets["smoke"]) } }
    neoForge.runs.named("client") {
        systemProperty("clientstacker.smoke.result", rootProject.file("artifacts/smoke-${project.name}.txt").absolutePath)
        systemProperty("clientstacker.smoke.automation", (minecraftVersion != "26.3").toString())
    }
}
if (providers.gradleProperty("vtpTest").isPresent && providers.gradleProperty("vtp_test_version").isPresent) {
    dependencies { runtimeOnly("maven.modrinth:trading-plus:${property("vtp_test_version")}") }
}

stonecutter {
    replacements.string {
        direction = eval(current.version, ">1.21.10")
        replace("ResourceLocation", "Identifier")
    }
    if (eval(current.version, ">=26.1")) {
        replacements.string { replace("ClickType", "ContainerInput"); direction = true }
        replacements.string { replace("handleInventoryMouseClick", "handleContainerInput"); direction = true }
    }
    if (eval(current.version, ">=26.1")) {
        for ((old, currentName) in listOf(
            "GuiGraphics" to "GuiGraphicsExtractor", "\"render\"" to "\"extractRenderState\"",
            "render(" to "extractRenderState(", "drawString" to "text", "drawCenteredString" to "centeredText",
            "renderWidget" to "extractWidgetRenderState", "renderContents" to "extractContents",
            "renderItemDecorations" to "itemDecorations", "renderItem(" to "item("
        )) replacements.string { replace(old, currentName); direction = true }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft", minecraftVersion)
    inputs.property("neoforge", neoForgeVersion)
    filesMatching("META-INF/neoforge.mods.toml") {
        expand("version" to project.version, "minecraft" to minecraftVersion, "neoforge" to neoForgeVersion)
    }
    exclude("fabric.mod.json")
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
