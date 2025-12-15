plugins {
    kotlin("jvm") version "2.2.21"
    id("org.graalvm.buildtools.native") version "0.10.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.0.1"
    `maven-publish`
    application
}

group = "com.rkhamatyarov"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.rkhamatyarov.laret.example.MainKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.22")
    implementation("ch.qos.logback:logback-core:1.5.22")
    implementation("org.fusesource.jansi:jansi:2.4.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.20.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.20.1")

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.junit.vintage:junit-vintage-engine:6.0.1")
}

kotlin {
    jvmToolchain(24)
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

graalvmNative {
    binaries {
        create("windows") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }

        create("linux") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
            buildArgs.add("--enable-native-access=ALL-UNNAMED")
            buildArgs.add("-H:+ReportExceptionStackTraces")
        }
    }
}

tasks {
    withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
        archiveClassifier.set("")
        archiveFileName.set("laret-fat.jar")
        manifest {
            attributes["Main-Class"] = "com.rkhamatyarov.laret.example.MainKt"
        }
    }

    jar {
        archiveFileName.set("laret.jar")
        manifest {
            attributes["Main-Class"] = "com.rkhamatyarov.laret.example.MainKt"
        }
    }

    register<Jar>("sourcesJar") {
        from(sourceSets["main"].allSource)
        archiveClassifier.set("sources")
    }

    register<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
    }

    test {
        useJUnitPlatform()
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("Kotlin Laret")
                url.set("https://github.com/rkhamatyarov/laret")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                scm {
                    connection.set("scm:git:github.com/rkhamatyarov/laret.git")
                    url.set("https://github.com/rkhamatyarov/laret")
                }
            }
        }
    }
}
