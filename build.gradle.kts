plugins {
    kotlin("jvm") version "2.0.0"
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "killua.dev"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    mavenLocal()
}

application {
    mainClass.set("killua.dev.MainKt")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.github.ajalt.clikt:clikt:4.4.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.twitter4j:twitter4j-core:4.1.2")
    implementation("com.github.scribejava:scribejava-apis:8.3.3")
    testImplementation(kotlin("test"))

}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "killua.dev.MainKt"
    }
    archiveFileName.set("app.jar")
}

tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
    archiveClassifier.set("all")
    mergeServiceFiles()
    manifest {
        attributes["Main-Class"] = "killua.dev.MainKt"
    }
}

tasks.named("build") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distZip") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("distTar") {
    dependsOn(tasks.named("shadowJar"))
}

tasks.named("startScripts") {
    dependsOn(tasks.named("shadowJar"))
    val shadowJar = tasks.named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar").get()
    val startScripts = tasks.named<CreateStartScripts>("startScripts").get()
    startScripts.classpath = files(shadowJar.archiveFile.get())
    startScripts.dependsOn(tasks.named("jar"))
}