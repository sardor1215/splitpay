import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.3.0"
    id("io.ktor.plugin") version "3.4.1"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    application
}

group = "com.example"
version = "0.0.1"

application {
    mainClass.set("com.splitpay.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    // Versions
    val ktor_version = "2.3.1"
    val logback_version = "1.4.11"
    val exposed_version = "0.44.1"
    val postgres_version = "42.6.0"

    // Logging
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Ktor
    implementation("io.ktor:ktor-server-core-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-netty-jvm:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor_version")

    // Ktor auth
    implementation("io.ktor:ktor-server-auth:${ktor_version}")
    implementation("io.ktor:ktor-server-auth-jwt:${ktor_version}")

    // Password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // Email (replace jakarta.mail with this)
    implementation("org.eclipse.angus:angus-mail:2.0.2")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.postgresql:postgresql:$postgres_version")

    // Exposed DateTime support (for timestampWithTimeZone)
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")


    // Tests
    testImplementation("io.ktor:ktor-server-tests-jvm:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.3.0")

    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
kotlin {
    jvmToolchain(17) // or whatever version you want — sets both Java & Kotlin
}