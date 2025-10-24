plugins {
    id("java")
}

group = "org.maibot.sdk"
description = "MaiBot SDK"
version = "0.1.0-Alpha"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // SLF4J for logging
    implementation("org.slf4j:slf4j-api:2.0.17")

    // Netty for networking
    implementation("io.netty:netty-all:4.2.6.Final")

    // Lombok for reducing boilerplate code
    compileOnly("org.projectlombok:lombok:1.18.42")
    annotationProcessor("org.projectlombok:lombok:1.18.42")
}

tasks.test {
    useJUnitPlatform()
}