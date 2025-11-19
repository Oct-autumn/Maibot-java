import java.security.MessageDigest
import java.time.Instant

plugins {
    id("java")
}

group = "org.maibot"
description = "MaiBot Core"

// Core Version
// Update this version when releasing a new Core version
// Format: MAJOR.MINOR.PATCH-PRERELEASE
// Do not add build metadata here; it will be appended automatically during the build process
version = "0.1.0-Alpha"

repositories {
    mavenCentral()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

    // Exclude src/main/java/org/maibot/core/cache/mcache from compiling
    sourceSets {
        main {
            java {
                exclude("org/maibot/core/cache/mcache/**")
            }
        }
    }
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J and Logback for logging
    implementation("org.slf4j:slf4j-api:2.0.17")
    implementation("ch.qos.logback:logback-classic:1.5.19")

    // Jackson for configuration
    implementation("tools.jackson.core:jackson-databind:3.0.1")
    implementation("tools.jackson.dataformat:jackson-dataformat-toml:3.0.1")
    implementation("tools.jackson.dataformat:jackson-dataformat-yaml:3.0.1")
    implementation("tools.jackson.dataformat:jackson-dataformat-properties:3.0.1")

    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")

    // ClassGraph for runtime classpath scanning
    implementation("io.github.classgraph:classgraph:4.8.184")

    // SQLite and Hibernate for database access
    implementation("org.xerial:sqlite-jdbc:3.50.3.0")
    implementation("org.hibernate.orm:hibernate-core:7.1.3.Final")
    implementation("org.hibernate.orm:hibernate-c3p0:7.1.3.Final")
    implementation("org.hibernate.orm:hibernate-community-dialects:7.1.3.Final")

    // javax xml bind (for Ehcache xml configuration)
    implementation("javax.xml.bind:jaxb-api:2.3.1")
    implementation("org.glassfish.jaxb:jaxb-runtime:2.3.9")

    // EhCache for caching
    implementation("org.ehcache:ehcache:3.11.1")
    implementation("org.hibernate.orm:hibernate-jcache:7.1.7.Final")

    // Semver4j for semantic versioning
    implementation("org.semver4j:semver4j:6.0.0")

    // Netty for networking
    implementation("io.netty:netty-all:4.2.6.Final")

    // Command-line interface
    implementation("info.picocli:picocli-shell-jline3:4.7.7")
    implementation("org.fusesource.jansi:jansi:2.4.2")

    // SDK Dependency
    implementation(project(":sdk"))
}

tasks.test {
    useJUnitPlatform()
}

// Create build-inf.properties
tasks.register("createBuildInfo") {
    // Collect dependencies to include in build info (only implementation dependencies)
    // These info are needed by the Launcher to download correct dependencies
    val implDeps1 = configurations.findByName("implementation")
        ?.allDependencies?.joinToString(",") { dep ->
            when (dep) {
                is ProjectDependency ->
                    "project:${dep.path}"

                else -> {
                    val g = dep.group ?: ""
                    val n = dep.name
                    val v = dep.version ?: ""
                    listOf(g, n, v).filter { it.isNotEmpty() }.joinToString(":")
                }
            }
        } ?: ""

    val innerVersion = "$version+${calcSrcHash().substring(0, 8)}"

    doLast {
        val outputDir = file("src/main/resources/META-INF")
        val outputFile = file("$outputDir/build-inf.properties")

        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        outputFile.writeText(
            """
            version=$innerVersion
            artifactId=${this.project.group}:${this.project.name}:${this.project.version}
            buildTime=${Instant.now().epochSecond}
            implDeps=$implDeps1
        """.trimIndent()
        )
    }
}

tasks.register("jarAndMove") {
    dependsOn("jar")
    doLast {
        val jarTask = tasks.named("jar").get()
        val outputDir = file("../launcher/run/.maibot-launcher")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        jarTask.outputs.files.forEach { file ->
            file.copyTo(file("$outputDir/${file.name}"), overwrite = true)
        }
    }
}

tasks.named("processResources") {
    dependsOn("createBuildInfo")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.maibot.core.Main"
    }
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