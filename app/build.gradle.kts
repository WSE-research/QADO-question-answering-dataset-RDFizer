/*
 * This file was generated by the Gradle 'init' task.
 *
 * This generated file contains a sample Kotlin application project to get you started.
 * For more details take a look at the 'Building Java & JVM projects' chapter in the Gradle
 * User Manual available at https://docs.gradle.org/7.3/userguide/building_java_projects.html
 */

plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"

    // Apply the application plugin to add support for building a CLI application in Java.
    application
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()

    maven {
        url = uri("https://jitpack.io/")
    }
}

dependencies {
    // Align versions of all Kotlin components
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))

    // Use the Kotlin JDK 8 standard library.
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // This dependency is used by the application.
    implementation("com.google.guava:guava:30.1.1-jre")

    // https://mvnrepository.com/artifact/be.ugent.rml/rmlmapper
    implementation("be.ugent.rml:rmlmapper:4.12.0")

    implementation("io.ktor:ktor-server-core:1.6.7")
    implementation("io.ktor:ktor-server-netty:1.6.7")
    implementation("ch.qos.logback:logback-classic:1.2.5")
    implementation("io.ktor:ktor-serialization:1.6.7")

    // Use the Kotlin test library.
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // Use the Kotlin JUnit integration.
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    // Define the main class for the application.
    mainClass.set("question.answering.dataset.rdfizer.AppKt")
}
