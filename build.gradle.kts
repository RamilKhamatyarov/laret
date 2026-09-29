plugins {
    kotlin("jvm") version "2.4.20"
    id("org.graalvm.buildtools.native") version "1.1.14"
    id("com.gradleup.shadow") version "9.6.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.diffplug.spotless") version "8.10.3"
    id("pmd")
    `maven-publish`
    signing
    application
}

group = "io.github.laretframework"
version = "0.2.1"

application {
    mainClass.set("io.github.laretframework.example.MainKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    implementation("org.jline:jline:4.4.6")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.22.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.22.3")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.22.3")
    implementation("com.github.ajalt.mordant:mordant:3.1.0")
    implementation("com.github.ajalt.mordant:mordant-coroutines:3.1.0")
    implementation("com.github.ajalt.mordant:mordant-markdown:3.1.0")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.assertj:assertj-core:3.27.7")
    testImplementation("io.mockk:mockk:1.14.11")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.1.3")
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
}

val generateBuildInfo = tasks.register("generateBuildInfo") {
    val outputDir = layout.buildDirectory.dir("generated/buildinfo")
    val projectVersion = version.toString()
    inputs.property("version", projectVersion)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().asFile.resolve("io/github/laretframework")
        packageDir.mkdirs()
        packageDir.resolve("BuildInfo.kt").writeText(
            """
            package io.github.laretframework

            /** Generated from the Gradle project version. Do not edit. */
            internal object BuildInfo {
                const val VERSION: String = "$projectVersion"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvmToolchain(25)
    sourceSets["main"].kotlin.srcDir(generateBuildInfo)
}

val bench: SourceSet = sourceSets.create("bench") {
    compileClasspath += sourceSets["main"].output + sourceSets["main"].compileClasspath
    runtimeClasspath += sourceSets["main"].output + sourceSets["main"].runtimeClasspath
}

// The bench payloads are tested like any other code, so the unit tests see
// the bench source set; nothing in main depends on it.
sourceSets.named("test") {
    compileClasspath += bench.output
    runtimeClasspath += bench.output
}

pmd {
    toolVersion = "7.16.0"
    isConsoleOutput = true
    isIgnoreFailures = false
    ruleSets = listOf()
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    format("shellGenerators") {
        target(
            "src/**/completion/BashCompletionGenerator.kt",
            "src/**/completion/ZshCompletionGenerator.kt",
            "src/**/completion/PowerShellCompletionGenerator.kt",
        )
    }

    kotlin {
        target("src/**/*.kt")
        targetExclude(
            "src/**/completion/BashCompletionGenerator.kt",
            "src/**/completion/ZshCompletionGenerator.kt",
            "src/**/completion/PowerShellCompletionGenerator.kt",
        )
        ktlint("1.7.1")
            .setEditorConfigPath(".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        ktlint("1.7.1")
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

ktlint {
    version.set("1.7.1")
    android.set(false)
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)

    filter {
        exclude { "generated" in it.file.absolutePath }
    }

    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
}

tasks.named("ktlintCheck") {
    dependsOn("spotlessApply")
}

graalvmNative {
    binaries {
        val reflectConfig = "$projectDir/src/main/resources/META-INF/native-image/reflect-config.json"
        val resourceConfig = "$projectDir/src/main/resources/META-INF/native-image/resource-config.json"
        val commonArgs =
            listOf(
                "--no-fallback",
                "-Ob",
                "--enable-native-access=ALL-UNNAMED",
                "--install-exit-handlers",
                // Serial GC with an 8 MiB young generation. Measured on the
                // concurrency benchmark: peak RSS falls from 46 to 29 MiB in the
                // event storm and from 66 to 29 MiB in the 100k-line pipeline,
                // and the pipeline gets faster. A 10,000-coroutine fan-out burst
                // pays a few milliseconds for the extra young collections. See
                // the laret-native-performance-targets ADR.
                "--gc=serial",
                "-R:MaxNewSize=8m",
                "-H:+ReportExceptionStackTraces",
                "-H:ReflectionConfigurationFiles=$reflectConfig",
                "-H:ResourceConfigurationFiles=$resourceConfig",
                "-H:IncludeResources=templates/.*\\.tpl$",
            )
        named("main") {
            imageName.set("laret")
            mainClass.set("io.github.laretframework.example.MainKt")
            buildArgs.addAll(commonArgs)
        }
        create("windows") {
            imageName.set("laret")
            mainClass.set("io.github.laretframework.example.MainKt")
            buildArgs.addAll(commonArgs)
        }
        create("linux") {
            imageName.set("laret")
            mainClass.set("io.github.laretframework.example.MainKt")
            buildArgs.addAll(commonArgs)
        }
        // nativeBenchCompile -> build/native/nativeBenchCompile/laret-bench
        create("bench") {
            imageName.set("laret-bench")
            mainClass.set("io.github.laretframework.bench.BenchMainKt")
            classpath.from(bench.runtimeClasspath)
            buildArgs.addAll(commonArgs)
        }
    }
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        archiveFileName.set("laret-fat.jar")
        manifest {
            attributes["Main-Class"] = "io.github.laretframework.example.MainKt"
        }
    }

    // build/libs/laret-bench-fat.jar: the Laret JVM benchmark target.
    register<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("benchShadowJar") {
        group = "benchmark"
        description = "Builds the minimal Laret benchmark app as a fat jar"
        archiveFileName.set("laret-bench-fat.jar")
        from(bench.output)
        configurations.set(listOf(project.configurations["runtimeClasspath"]))
        from(sourceSets["main"].output)
        manifest {
            attributes["Main-Class"] = "io.github.laretframework.bench.BenchMainKt"
        }
    }

    jar {
        archiveFileName.set("laret.jar")
        manifest {
            attributes["Main-Class"] = "io.github.laretframework.example.MainKt"
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

    register("checkAll") {
        description = "Run all code quality checks in correct order"
        group = "verification"

        dependsOn(
            "spotlessApply",
            "ktlintCheck",
            "spotlessCheck",
            "pmdMain",
            "pmdTest",
            "test",
        )
    }

    named("check") {
        dependsOn("spotlessCheck", "ktlintCheck", "pmdMain", "pmdTest")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenKotlin") {
            from(components["kotlin"])
            artifact(tasks["sourcesJar"])
            artifact(tasks["javadocJar"])

            pom {
                name.set("Laret")
                description.set(
                    "A Cobra-like CLI framework for Kotlin with GraalVM Native Image support",
                )
                url.set("https://github.com/laretframework/laret")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("laretframework")
                        name.set("Laret Framework")
                        url.set("https://github.com/laretframework")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/laretframework/laret.git")
                    developerConnection.set("scm:git:ssh://git@github.com/laretframework/laret.git")
                    url.set("https://github.com/laretframework/laret")
                }
            }
        }
    }
    repositories {
        maven {
            name = "sonatype"
            url = uri(
                if (version.toString().endsWith("SNAPSHOT")) {
                    "https://s01.oss.sonatype.org/content/repositories/snapshots/"
                } else {
                    "https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/"
                },
            )
            credentials {
                username = project.findProperty("ossrhUsername") as String?
                    ?: System.getenv("OSSRH_USERNAME")
                password = project.findProperty("ossrhPassword") as String?
                    ?: System.getenv("OSSRH_TOKEN")
            }
        }

        maven("GitHubPackages") {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/RamilKhamatyarov/laret")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    isRequired = !signingKey.isNullOrBlank()
    if (isRequired) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["mavenKotlin"])
    }
}
