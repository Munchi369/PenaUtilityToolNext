plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

val appVersion = providers.gradleProperty("appVersion").get()

version = appVersion

val generateAppVersion by tasks.registering {
    val outputDirectory = layout.buildDirectory.dir("generated/source/appVersion")
    val versionText = appVersion
    inputs.property("appVersion", versionText)
    outputs.dir(outputDirectory)
    doLast {
        val file = outputDirectory.get().file("io/github/munchi369/penautilitytoolnext/AppVersion.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("package io.github.munchi369.penautilitytoolnext\n\nconst val APP_VERSION = \"$versionText\"\n")
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir(generateAppVersion)
    }
}

ktlint {
    filter {
        exclude { it.file.path.contains("generated") }
    }
}

repositories {
    mavenCentral()
    google()
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(libs.compose.material3)
    implementation(libs.white.wind.table)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.derby)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.snakeyaml)

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

compose.desktop {
    application {
        mainClass = "io.github.munchi369.penautilitytoolnext.AppKt"

        nativeDistributions {
            packageName = "PenaUtilityToolNext"
            packageVersion = appVersion
            modules("java.sql")
        }
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    inputs
        .files(rootProject.layout.projectDirectory.dir("knowledge"), rootProject.layout.projectDirectory.dir("docs"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("knowledgeBundle")
    systemProperty(
        "skiko.data.path",
        layout.buildDirectory
            .dir("skiko")
            .get()
            .asFile.absolutePath,
    )
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run") {
        systemProperty("pena.app.dir", rootProject.projectDir.absolutePath)
        systemProperty("pena.development", "true")
    }
}
