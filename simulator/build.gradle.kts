plugins {
    java
    application
}

repositories {
    mavenCentral()
}
dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.19.2")
}
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}
sourceSets {
    main {
        java {
            setSrcDirs(listOf("../src/main/java", "src/main/java"))
            include("dev/civilizations/core/**")
            include("dev/civilizations/simulation/**")
            include("dev/civilizations/scenario/WoodcutterBasicScenario.java")
        }
    }
}
application {
    mainClass.set("dev.civilizations.simulation.cli.SimulatorCli")
}
tasks.named<Jar>("jar") {
    manifest {
        attributes("Main-Class" to "dev.civilizations.simulation.cli.SimulatorCli")
    }
}
