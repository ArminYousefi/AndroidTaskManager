plugins {
    kotlin("jvm") version "1.9.10"
    id("io.ktor.plugin") version "2.3.4"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.example"
version = "0.0.1"

repositories {
    mavenCentral()
}
application {
    mainClass.set("com.example.ApplicationKt")
}
dependencies {
    implementation("org.jetbrains.exposed:exposed-core:0.44.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.44.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.44.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.44.1")

    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("io.ktor:ktor-server-auth-jvm:2.3.4")
    implementation("io.ktor:ktor-server-auth-jwt-jvm:2.3.4")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:2.3.4")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:2.3.4")
    implementation("io.ktor:ktor-server-websockets-jvm:2.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.exposed:exposed-core:0.41.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.41.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.41.1")
    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("at.favre.lib:bcrypt:0.9.0")
    implementation("com.auth0:java-jwt:4.4.0")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    testImplementation("io.ktor:ktor-server-tests-jvm:2.3.4")
}
