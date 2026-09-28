import java.util.jar.JarFile
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

val hytaleServerVersion = providers.gradleProperty("hytaleServerVersion").getOrElse("latest.release")
val artifactBaseName = providers.gradleProperty("artifactBaseName").getOrElse("hytale-civ")
val assetPackDir = layout.projectDirectory.dir("asset-pack")

val hytaleInspection by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    compileOnly("com.hypixel.hytale:Server:$hytaleServerVersion")
    hytaleInspection("com.hypixel.hytale:Server:$hytaleServerVersion")

    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
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

val snapshotHytaleApi = tasks.register("snapshotHytaleApi") {
    group = "verification"
    description = "Creates a proof-of-concept API snapshot from the resolved Hytale Server JAR."
    notCompatibleWithConfigurationCache("Proof-of-concept task inspects the resolved external JAR at execution time.")

    val outputDir = layout.buildDirectory.dir("hytale-api-snapshot")
    outputs.dir(outputDir)

    doLast {
        val artifact = hytaleInspection.resolvedConfiguration.resolvedArtifacts.single()
        val serverJar = artifact.file
        val snapshotDir = outputDir.get().asFile
        val signaturesDir = snapshotDir.resolve("signatures")

        snapshotDir.deleteRecursively()
        signaturesDir.mkdirs()

        val classes = JarFile(serverJar).use { jar ->
            jar.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".class") && !it.name.contains("module-info") }
                .map { it.name.removeSuffix(".class").replace('/', '.') }
                .sorted()
                .toList()
        }

        snapshotDir.resolve("classes.txt").writeText(classes.joinToString(separator = "\n", postfix = "\n"))

        val module = artifact.moduleVersion.id
        snapshotDir.resolve("metadata.txt").writeText(
            buildString {
                appendLine("module=${module.group}:${module.name}:${module.version}")
                appendLine("jar=${serverJar.name}")
                appendLine("classCount=${classes.size}")
            }
        )

        val targetSimpleNames = listOf(
            "CommandBuffer",
            "CameraManager",
            "InteractiveCustomUIPage",
            "PrefabStore",
            "TriggerVolumeManager",
            "UnarmedInteractions",
            "RootInteraction",
            "OpenCustomUIInteraction",
            "RunRootInteraction",
            "TargetUtil"
        )

        val javaLauncher = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }.get()
        val javapName = if (System.getProperty("os.name").lowercase().contains("win")) "javap.exe" else "javap"
        val javap = javaLauncher.metadata.installationPath.file("bin/$javapName").asFile

        val findings = mutableListOf<String>()

        for (simpleName in targetSimpleNames) {
            val matches = classes.filter { it.substringAfterLast('.') == simpleName }
            if (matches.isEmpty()) {
                findings += "$simpleName -> MISSING"
                continue
            }

            for (className in matches) {
                val process = ProcessBuilder(
                    javap.absolutePath,
                    "-classpath",
                    serverJar.absolutePath,
                    "-protected",
                    className
                )
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    throw GradleException("javap failed for $className:\n$output")
                }

                signaturesDir.resolve("${className.replace('.', '_')}.txt").writeText(output)
                findings += "$simpleName -> $className"
            }
        }

        snapshotDir.resolve("findings.txt").writeText(findings.joinToString(separator = "\n", postfix = "\n"))

        logger.lifecycle("Hytale API snapshot written to {}", snapshotDir)
        findings.forEach { logger.lifecycle(it) }
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
