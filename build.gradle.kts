import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar

plugins {
    java
}

group = "dev.civilizations"
version = providers.gradleProperty("projectVersion").getOrElse("0.1.0-SNAPSHOT")

repositories {
    mavenCentral()
    maven {
        name = "HytaleRelease"
        url = uri("https://maven.hytale.com/release")
        mavenContent {
            releasesOnly()
        }
    }
}

val hytaleServerVersion = providers.gradleProperty("hytaleServerVersion").get()
val artifactBaseName = providers.gradleProperty("artifactBaseName").getOrElse("hytale-civ")
val assetPackDir = layout.projectDirectory.dir("asset-pack")

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
    // Hytale remains compileOnly for the shipped plugin; tests need the same API at compile/runtime
    // to verify persistent PlayerSkin and CharacterCreator-backed configuration contracts.
    testImplementation("com.hypixel.hytale:Server:$hytaleServerVersion")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 25
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
    reports {
        junitXml.required = true
        html.required = true
    }
}

val pluginJar = tasks.named<Jar>("jar") {
    archiveBaseName = artifactBaseName
    manifest {
        attributes(
            "Implementation-Title" to "Hytale Civ Plugin",
            "Implementation-Version" to project.version
        )
    }
}

val releaseBundle = tasks.register<Zip>("releaseBundle") {
    group = "distribution"
    description = "Packages the plugin JAR and editable Hytale asset pack into one release ZIP."
    dependsOn(pluginJar)

    archiveBaseName.set(artifactBaseName)
    archiveClassifier.set("bundle")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))

    from(pluginJar.flatMap { it.archiveFile }) {
        rename(".*\\.jar", "$artifactBaseName.jar")
    }

    from(assetPackDir) {
        into("$artifactBaseName-assets")
    }
}

tasks.named("build") {
    dependsOn(releaseBundle)
}

tasks.register<JavaExec>("simulationViewer") {
    group = "development"
    description = "Starts the Hytale-independent desktop simulation viewer."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("dev.civilizations.simulation.viewer.SimulationViewerApp")
}

tasks.register("deployToHytale") {
    group = "development"
    description = "Builds and copies the plugin JAR plus editable asset pack to HYTALE_MODS_DIR (or -PhytaleModsDir)."
    dependsOn(pluginJar)

    doLast {
        val destination = providers.environmentVariable("HYTALE_MODS_DIR")
            .orElse(providers.gradleProperty("hytaleModsDir"))
            .orNull
            ?: throw GradleException(
                "No Hytale mods directory configured. Set HYTALE_MODS_DIR or pass -PhytaleModsDir=/path/to/mods."
            )

        copy {
            from(pluginJar.flatMap { it.archiveFile })
            into(file(destination))
        }

        copy {
            from(assetPackDir)
            into(file(destination).resolve("$artifactBaseName-assets"))
        }

        logger.lifecycle(
            "Deployed {} and {} to {}",
            pluginJar.get().archiveFileName.get(),
            "$artifactBaseName-assets",
            destination
        )
    }
}
