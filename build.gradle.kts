val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project

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

// Remove this warning:
// 'compileJava' task (current target is 11) and 'compileKotlin' task (current target is 1.8) jvm target compatibility should be set to the same Java version.
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of("11"))
    }
}

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
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")

    // Gson's serialization - For UUID's and other custom types
    implementation("com.google.code.gson:gson:2.7")

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

}

//tasks.test {
//    useJUnitPlatform()
//}

//kotlin {
//    jvmToolchain(8)
//}


