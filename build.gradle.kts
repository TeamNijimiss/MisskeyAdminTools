/*
 * Copyright 2024 Nafu Satsuki
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    kotlin("jvm") version "2.0.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("org.jetbrains.kotlin.plugin.lombok") version "2.0.0"
    id("io.freefair.lombok") version "8.6"
}

group = "app.nijimiss"
version = "0.18.0"

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
    implementation("org.slf4j:slf4j-api:2.0.13")
    implementation("ch.qos.logback:logback-core:1.5.6")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    // ClientLib
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.github.devcsrj:slf4j-okhttp3-logging-interceptor:1.0.1")
    compileOnly("com.github.NeoBotDevelopment:NeoBotApi:2.3.0")

    // Ktor
    implementation("io.ktor:ktor-server-core:2.3.11")
    implementation("io.ktor:ktor-server-netty:2.3.11")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.11")
    implementation("io.ktor:ktor-serialization-jackson:2.3.11")

    // Unirest
    implementation("com.konghq:unirest-java:3.14.5")

    // Parser
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.17.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.1")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.17.1")

    // Commons Library
    implementation("commons-io:commons-io:2.16.0")
    implementation("commons-codec:commons-codec:1.16.1")
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("org.apache.tika:tika-core:2.9.2")

    implementation("org.xerial:sqlite-jdbc:3.45.3.0")
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
