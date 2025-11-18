plugins {
    kotlin("jvm") version "2.2.20"
    id("org.graalvm.buildtools.native") version "0.10.4"
    id("com.github.johnrengelman.shadow") version "8.1.1"
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

    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:6.0.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:6.0.1")
    testImplementation("org.junit.vintage:junit-vintage-engine:5.9.2")
}

kotlin {
    jvmToolchain(24)
}

graalvmNative {
    binaries {
        named("main") {
            imageName.set("laret")
            mainClass.set("com.rkhamatyarov.laret.example.MainKt")
            buildArgs.add("--no-fallback")
            buildArgs.add("-Ob")
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
