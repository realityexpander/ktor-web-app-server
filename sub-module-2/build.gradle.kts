import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
//    application
    kotlin("jvm").version("1.8.0")
}

// config JVM target to 1.8 for kotlin compilation tasks
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "11"
}

// For tests
// 'compileJava' task (current target is 11) and 'compileKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("11"))
    }
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    api("net.iharder:base64:2.3.9")  // library will available to importing project
    //implementation("net.iharder:base64:2.3.9")  // library will NOT be exposed to importing project

    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}
