import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import java.security.MessageDigest
import java.time.Instant

plugins {
    id("java")
    id("com.gradleup.shadow") version "9.2.2"
    kotlin("jvm") version "2.3.20"
}

group = "org.maibot"
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
    implementation("ch.qos.logback:logback-classic:1.5.32")

    // argparse4j for command-line argument parsing
    implementation("net.sourceforge.argparse4j:argparse4j:0.9.0")

    // Semver4j for semantic versioning
    implementation("org.semver4j:semver4j:6.0.0")

    // Jackson for configuration
    implementation("tools.jackson.core:jackson-databind:3.1.0")

    // Maven Resolver for running dependency download
    implementation("org.apache.maven.resolver:maven-resolver-supplier-mvn4:2.0.16")

    // JNA for native access
    implementation("com.github.jnr:jnr-posix:3.1.21")

    // Kotlin standard library and reflection
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.20")
    implementation("org.jetbrains.kotlin:kotlin-reflect:2.3.20")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.maibot.launcher.LauncherMain"
    }
}

tasks.shadowJar {
    archiveBaseName.set("OMCL")
    archiveClassifier.set("all")
    archiveVersion.set(project.version.toString())
}

tasks.register("shadowJarAndMove") {
    dependsOn("shadowJar")
    doLast {
        val shadowJar = tasks.named("shadowJar").get() as ShadowJar
        val outputDir = file("run")
        val outputFile = file("${outputDir}/OMCL-${project.version}.jar")
        shadowJar.archiveFile.get().asFile.copyTo(outputFile, overwrite = true)
        println("Shadow JAR created at: ${outputFile.absolutePath}")
    }
}

// Create build-inf.properties
tasks.register("createBuildInfo") {
    val innerVersion = "$version+${calcSrcHash().substring(0, 8)}"

    doLast {
        val outputDir = file("src/main/resources/org/maibot/launcher")
        val outputFile = file("$outputDir/build-inf.properties")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        outputFile.writeText(
            """
            version=$innerVersion
            artifactId=${this.project.group}:${this.project.name}:${this.project.version}
            buildTime=${Instant.now().epochSecond}
        """.trimIndent()
        )
    }
}

tasks.named("processResources") {
    dependsOn("createBuildInfo")
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