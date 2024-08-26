plugins {
    kotlin("jvm") version "2.0.0"
    id("com.google.devtools.ksp") version "2.0.10-1.0.24"
    id("eu.vendeli.telegram-bot") version "7.2.2"
}

group = "dev.hylas"
version = "0.0.1"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("org.twitter4j:twitter4j-core:4.1.2")
    implementation("com.github.scribejava:scribejava-apis:8.3.3")
    implementation("org.mongodb:mongodb-driver-kotlin-coroutine:5.1.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}
