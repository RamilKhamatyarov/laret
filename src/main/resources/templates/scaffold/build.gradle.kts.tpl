plugins {
    kotlin("jvm") version "2.0.21"
    application
{{#if graalvm}}
    id("org.graalvm.buildtools.native") version "0.10.3"
{{/if}}
}

group = "${groupId}"
version = "0.1.0"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("com.rkhamatyarov:laret:${laretVersion}")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.3")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.11.3")
}

application {
    mainClass.set("${packageName}.MainKt")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}

{{#if graalvm}}
graalvmNative {
    binaries {
        named("main") {
            imageName.set("${appName}")
            mainClass.set("${packageName}.MainKt")
            buildArgs.addAll(
                "--no-fallback",
                "-Ob",
                "-H:+ReportExceptionStackTraces",
            )
        }
    }
}
{{/if}}

tasks.register("shellTests") {
    description = "Run generated shell precedence tests"
    group = "verification"
    doLast {
        val testsDir = file("tests")
        if (!testsDir.exists()) {
            logger.lifecycle("No tests/ directory found.")
            return@doLast
        }
        testsDir.listFiles()?.forEach { script ->
            logger.lifecycle("Running ${script.name}")
        }
    }
}
