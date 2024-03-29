import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val kotlinx_coroutines_version: String by project

// note: to run "watch" build (in a separate terminal):
// ./gradlew -t build
tasks.register("watch") {
    // run the build with -t (continuous build) option and dont exit
    doLast {
        exec {
            commandLine = listOf("./gradlew", "-t", "build")
        }
    }
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
//// For tests to run, the JVM target must be set to 1.8
//tasks.withType<JavaCompile>().configureEach {
//    options.release.set(8)
//}

plugins {
    application
    kotlin("jvm").version("1.8.0")
    kotlin("plugin.serialization").version("1.8.0")
}

group = "com.realityexpander"
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
    implementation(project(mapOf("path" to ":sub-module-2")))

    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version")
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:1.4.6")

    // ktor client
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("io.ktor:ktor-client-json:$ktor_version")
    implementation("io.ktor:ktor-client-okhttp:$ktor_version")

    // kotlinx serialization
    implementation("io.ktor:ktor-serialization-kotlinx:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")

    // Gson's serialization - For UUID's and other custom types
    implementation("com.google.code.gson:gson:2.8.9")

    // Logging
    implementation("io.ktor:ktor-client-logging:$ktor_version")

    // Compression for text
    implementation("io.ktor:ktor-server-compression:$ktor_version")

    // Creating file slugs
    implementation("com.github.slugify:slugify:3.0.2")

    // Authentication
    implementation("io.ktor:ktor-server-auth:$ktor_version")

    // HTML Building
    implementation("io.ktor:ktor-server-html-builder:$ktor_version")

    // Argon2 - password hashing & salting
    implementation("de.mkammerer:argon2-jvm:2.11")

    // Forwarding headers - getting clientIP Address
    implementation("io.ktor:ktor-server-forwarded-header:$ktor_version")

    // For JWT's
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")

    // HOCON - configuration files
    implementation("io.github.config4k:config4k:0.5.0")

    // Simple email sender
    implementation("org.apache.commons:commons-email:1.5")

    // Rate Limiting
    implementation("io.ktor:ktor-server-rate-limit:$ktor_version")

    // Status Pages
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")

    // Fluid Mongo (coroutines for mongo-db)
    implementation("io.fluidsonic.mongo:fluid-mongo:1.6.0")

    // Reflections for importing JSON Object using type safety
    implementation("org.reflections:reflections:0.10.2")
    implementation(kotlin("reflect"))

    // Lettuce for Redis
    implementation("io.lettuce:lettuce-core:6.2.6.RELEASE")
    implementation("com.redis:lettucemod:3.6.3") // Extension library

    // For coroutines & lettuce coroutine support
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinx_coroutines_version")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:$kotlinx_coroutines_version")

    // Websockets
    implementation("io.ktor:ktor-server-websockets:$ktor_version")


    // Testing
    //    testImplementation("io.ktor:ktor-server-test-host:$ktor_version") // needed? for junit4
    //    testImplementation("org.jetbrains.kotlin:kotlin-test")

    // For JUnit5 Parameterized tests
    testImplementation(platform("org.junit:junit-bom:5.9.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.1.0")

    // For Coroutines Testing
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.2")

}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

//kotlin {
//    jvmToolchain(8)
//}


