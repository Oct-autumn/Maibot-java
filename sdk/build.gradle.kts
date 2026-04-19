import java.security.MessageDigest
import java.time.Instant

plugins {
    id("java")
    id("maven-publish")
    kotlin("jvm") version "2.3.20"
}

group = "org.maibot"
description = "MaiBot SDK"

// SDK Version
// Update this version when releasing a new SDK version
// Format: MAJOR.MINOR.PATCH-PRERELEASE
// Do not add build metadata here; it will be appended automatically during the build process
version = "0.1.0-Alpha"

repositories {
    mavenCentral()
}

java {

    withSourcesJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J for logging
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Netty for networking
    implementation("io.netty:netty-all:4.2.12.Final")

    // Jackson for JSON parsing
    implementation("tools.jackson.core:jackson-databind:3.1.0")
    // Jackson Schema module for JSON Schema generation
    implementation("tools.jackson.module:jackson-module-jsonSchema:3.1.0")
    // Jackson Kotlin module for better Kotlin support
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.0")

    // Hibernate for ORM
    implementation("org.hibernate.orm:hibernate-core:7.2.7.Final")

    // JCache for caching
    implementation("javax.cache:cache-api:1.1.1")

    // Jetbrains Annotations
    implementation("org.jetbrains:annotations:24.0.1")

    // OpenAI API
    implementation("com.openai:openai-java:4.30.0")
}

// Create build-inf.properties
tasks.register("createBuildInf") {
    val outputDir = file("src/main/resources/org/maibot/sdk")
    val outputFile = file("$outputDir/build-inf.properties")

    val innerVersion = "$version+${calcSrcHash().substring(0, 8)}"

    doLast {
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        outputFile.writeText(
            """
            version=$innerVersion
            buildTime=${Instant.now().epochSecond}
        """.trimIndent()
        )
    }
}

publishing {
    // Publish sdk Jar to local Maven repository
    publications {
        create<MavenPublication>("sdk") {
            groupId = project.group as String?
            artifactId = project.name
            version = project.version as String?

            artifact(tasks.named("jar"))
            artifact(tasks.named("sourcesJar")) {
                classifier = "sources"
            }
        }
    }
    repositories {
        mavenLocal()
    }
}

tasks.named("publishToMavenLocal") {
    doLast {
        // Move jar to launcher/run/.maibot-launcher/libs/org/maibot/sdk/{version}
        val sdkVersion = project.version.toString()
        val sourceFile = file("${layout.buildDirectory.get()}/libs/${project.name}-$sdkVersion.jar")
        val targetDir = file("../launcher/run/.maibot-launcher/libs/org/maibot/sdk/$sdkVersion")
        val targetFile = file("$targetDir/${project.name}-$sdkVersion.jar")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Compare source and target files, only copy if they are different to avoid unnecessary file operations
        if (targetFile.exists()) {
            val sourceHash =
                MessageDigest.getInstance("SHA-256")
                    .digest(sourceFile.readBytes())
                    .joinToString("") { "%02x".format(it) }
            val targetHash =
                MessageDigest.getInstance("SHA-256")
                    .digest(targetFile.readBytes())
                    .joinToString("") { "%02x".format(it) }
            if (sourceHash == targetHash) {
                println("Target file is identical to source file, SKIPPED")
                return@doLast
            }
        }

        sourceFile.copyTo(targetFile, overwrite = true)
    }
}

tasks.named("processResources") {
    dependsOn("createBuildInf")
}

tasks.test {
    useJUnitPlatform()
}

// Calculate source code hash for build identification
fun calcSrcHash(): String {
    val srcDir = file("src/main/java")
    val digest = MessageDigest.getInstance("SHA-256")

    srcDir.walkTopDown().filter { it.isFile }.forEach { file ->
        file.inputStream().use { fis ->
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
    }

    return digest.digest().joinToString("") { "%02x".format(it) }
}
kotlin {
    jvmToolchain(21)
}