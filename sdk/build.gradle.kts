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
    
    implementation("org.slf4j:slf4j-api:2.0.17")

    implementation("io.netty:netty-all:4.2.6.Final")
}

tasks.test {
    useJUnitPlatform()
}