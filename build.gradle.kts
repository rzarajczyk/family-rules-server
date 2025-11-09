plugins {
    id("org.springframework.boot") version "3.3.1"
    id("io.spring.dependency-management") version "1.1.5"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
    application
}

group = "pl.zarajczyk"
version = "1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}


repositories {
    mavenCentral()
}


dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("com.google.cloud:google-cloud-firestore:3.15.0")
    implementation("com.google.auth:google-auth-library-oauth2-http:1.19.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

//    If you experience strange network problems on MacOS, uncomment this
//    implementation(
//        group = "io.netty",
//        name = "netty-resolver-dns-native-macos",
//        version = "4.1.86.Final",
//        classifier = "osx-aarch_64"
//    )

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.kotest:kotest-assertions-core:5.6.0")
    testImplementation("io.kotest:kotest-runner-junit5:5.6.0")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation(kotlin("test"))
    testImplementation("org.testcontainers:testcontainers:2.0.1")
    testImplementation("org.testcontainers:testcontainers-gcloud:2.0.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.0")
    testImplementation("org.testcontainers:postgresql:1.20.0")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("pl.zarajczyk.familyrules.ApplicationKt")
}

tasks {
    bootJar {
        archiveFileName.set("app.jar")
    }
}
