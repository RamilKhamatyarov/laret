// Standalone build: deliberately not part of the root Gradle project, so the
// benchmark target cannot affect how Laret itself is built or linted.
plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("info.picocli:picocli:4.7.6")
}

java {
    toolchain {
        // Matches the JDK the Laret JVM target runs on, so the two Java
        // measurements differ by framework rather than by runtime.
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

application {
    mainClass.set("benchmarks.picocli.BenchMain")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveClassifier.set("")
    archiveFileName.set("bench-picocli.jar")
    manifest {
        attributes["Main-Class"] = "benchmarks.picocli.BenchMain"
    }
}
