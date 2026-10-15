import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

// The app's data layer + Android shims, in its own module so its huge
// generated tables compile once instead of on every UI edit.
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

val app = rootDir.resolve("../app/src/main/kotlin/com/pokedaisy/app")
val appSrc = fileTree(app) {
    listOf("companion/data/**", "companion/*.kt", "companion/i18n/**", "MgbaCore.kt", "Prefs.kt", "cheats/Cheats.kt", "GbaControls.kt", "Hotkeys.kt")
        .forEach { include(it) }
}
sourceSets["main"].kotlin.srcDir("shims")
sourceSets["test"].kotlin.apply {
    srcDir(rootDir.resolve("../app/src/test/kotlin"))
    include("**/PinballReaderTest.kt")
    include("**/PinballCollectionReaderTest.kt")
}
tasks.named<KotlinCompile>("compileKotlin") { source(appSrc) }
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies {
    api(compose.desktop.currentOs)
    testImplementation("junit:junit:4.13.2")
}

// Optional user-owned ROM + private headless captures. Nothing binary is bundled or copied.
sourceSets["main"].kotlin.srcDir("check")
tasks.register<JavaExec>("pinballCapture") {
    description = "Decodes private Pinball RAM captures: -Prom=/private/game.gba -Pcaptures=/private/captures"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("check.PinballCaptureCheckKt")
    val rom = providers.gradleProperty("rom")
    val captures = providers.gradleProperty("captures")
    doFirst {
        require(rom.isPresent && captures.isPresent) { "Pass -Prom and -Pcaptures; keep both outside the repository." }
        args = listOf(File(rom.get()).absolutePath, File(captures.get()).absolutePath) +
            if (providers.gradleProperty("collectionDetails").orNull == "true") listOf("--collection-details") else emptyList()
    }
}
