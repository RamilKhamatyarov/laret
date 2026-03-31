plugins {
    kotlin("jvm") version "2.3.20"
    id("org.graalvm.buildtools.native") version "1.0.0"
    id("com.gradleup.shadow") version "9.4.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("com.diffplug.spotless") version "8.4.0"
    id("pmd")
    `maven-publish`
    application
}

group = "com.rkhamatyarov"
version = "0.2.0-SNAPSHOT"

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

    implementation("org.jline:jline:4.0.9")
    implementation("org.fusesource.jansi:jansi:2.4.3")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.21.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-toml:2.21.2")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.21.2")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("com.github.ajalt.mordant:mordant-coroutines:3.0.2")
    implementation("com.github.ajalt.mordant:mordant-markdown:3.0.2")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.3")
}

kotlin {
    jvmToolchain(25)
}

pmd {
    toolVersion = "7.7.0"
    isConsoleOutput = true
    ruleSets = listOf()
    ruleSetFiles = files("config/pmd/ruleset.xml")
}

spotless {
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    format("shellGenerators") {
        target(
            "src/**/completion/BashCompletionGenerator.kt",
            "src/**/completion/ZshCompletionGenerator.kt",
            "src/**/completion/PowerShellCompletionGenerator.kt"
        )
    }

    kotlin {
        target("src/**/*.kt")
        targetExclude(
            "src/**/completion/BashCompletionGenerator.kt",
            "src/**/completion/ZshCompletionGenerator.kt",
            "src/**/completion/PowerShellCompletionGenerator.kt"
        )
        ktlint("1.0.1")
            .setEditorConfigPath(".editorconfig")
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        ktlint("1.0.1")
        target("*.gradle.kts")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

ktlint {
    version.set("1.0.1")
    android.set(false)
    ignoreFailures.set(false)
    enableExperimentalRules.set(false)

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
                "-H:+ReportExceptionStackTraces",
                "-H:ReflectionConfigurationFiles=$reflectConfig",
                "-H:ResourceConfigurationFiles=$resourceConfig",
                "-H:IncludeResources=templates/.*\\.tpl$"
            )
        create("windows") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.addAll(commonArgs)
        }
        create("linux") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.addAll(commonArgs)
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

    register("checkAll") {
        description = "Run all code quality checks in correct order"
        group = "verification"

        dependsOn(
            "spotlessApply",
            "ktlintCheck",
            "spotlessCheck",
            "pmdMain",
            "pmdTest",
            "test"
        )
    }

    named("check") {
        dependsOn("spotlessCheck", "ktlintCheck")
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
    repositories {
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
