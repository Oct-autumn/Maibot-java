import java.time.Instant

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
}

group = "org.maibot.core"
description = "MaiBot Core"
version = "0.1.0-Alpha"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J and Logback for logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.19")

    // TOML for configuration
    implementation("io.hotmoka:toml4j:0.7.3")

    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    // SQLite and Hibernate for database access
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.hibernate.orm:hibernate-core:7.1.3.Final")
    implementation("org.hibernate.orm:hibernate-c3p0:7.1.3.Final")
    implementation("org.hibernate.orm:hibernate-community-dialects:7.1.3.Final")

    // Semver4j for semantic versioning
    implementation("org.semver4j:semver4j:6.0.0")

    // Netty for networking
    implementation("io.netty:netty-all:4.2.6.Final")

    // Command-line interface
    implementation("info.picocli:picocli-shell-jline3:4.7.7")
    implementation("org.fusesource.jansi:jansi:2.4.2")

    // SDK Dependency
    implementation(files("../sdk/build/libs/sdk-0.1.0-Alpha.jar"))
}

tasks.test {
    useJUnitPlatform()
}

// Create Version.Properties
tasks.register("createVersionProperties") {
    val outputDir = file("src/main/resources/org/maibot/core")
    val outputFile = file("$outputDir/version.properties")

    doLast {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        outputFile.writeText("version=$version\n")
        outputFile.appendText("buildTime=${Instant.now().epochSecond}\n")
    }
}

tasks.named("compileJava") {
    dependsOn(":sdk:jar")
}

tasks.named("processResources") {
    dependsOn("createVersionProperties")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.maibot.core.Main"
    }
}