plugins {
    kotlin("jvm") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.plugin.lombok") version "1.8.10"
    id("io.freefair.lombok") version "6.4.3"
}

group = "app.nijimiss"
version = "0.11.1"

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set(project.name + "." + archiveExtension.get())

    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            mapOf(
                "Main-Class" to "app.nijimiss.mat.Main"
            )
        )
    }
}

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io")
}

dependencies {
    testImplementation(kotlin("test"))

    // Logger
    implementation("org.slf4j:slf4j-api:2.0.5")
    implementation("ch.qos.logback:logback-core:1.4.12")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    // ClientLib
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.10.0")
    implementation("com.github.devcsrj:slf4j-okhttp3-logging-interceptor:1.0.1")
    compileOnly("com.github.NeoBotDevelopment:NeoBotApi:2.0.0-alpha.6")

    implementation("com.konghq:unirest-java:3.14.5")


    // Parser
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.16.1")

    // Commons Library
    implementation("commons-io:commons-io:2.15.1")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.tika:tika-core:2.9.1")

    implementation("org.xerial:sqlite-jdbc:3.42.0.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("module.yaml") {
        expand("version" to version)
    }
}

kotlin {
    jvmToolchain(17)
}
