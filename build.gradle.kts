val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project


plugins {
    application
    kotlin("jvm").version("1.8.0")
    kotlin("plugin.serialization").version("1.8.0")
}

group = "com.realityexapander"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
    project.setProperty("mainClassName", mainClass.get())  // needed for shadow plugin to find the main function

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.4.6")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // ktor client
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-json:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")

    // kotlinx serialization
    implementation("io.ktor:ktor-serialization-kotlinx:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-client-logging:$ktor_version")

    // Compression for text
    implementation("io.ktor:ktor-server-compression:$ktor_version")

    // For creating slugs
    implementation("com.github.slugify:slugify:3.0.2")
}

//tasks.test {
//    useJUnitPlatform()
//}

//kotlin {
//    jvmToolchain(8)
//}
