import org.gradle.kotlin.dsl.dependencies

plugins {
    kotlin("jvm") version "1.9.22"
    id("io.ktor.plugin") version "2.3.7"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.burgerking.duoc"
version = "0.1.0"

application {
    mainClass.set("com.burgerking.duoc.ApplicationKt")
}

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
}


dependencies {


    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-gson-jvm")
    implementation("io.ktor:ktor-server-cors-jvm")


    implementation("org.litote.kmongo:kmongo:4.11.0")
    implementation("org.litote.kmongo:kmongo-serialization:4.11.0")
    implementation("org.litote.kmongo:kmongo-id:4.11.0")





    implementation("org.mongodb:mongodb-driver-sync:4.11.0")


    implementation("io.insert-koin:koin-ktor:3.5.3")
    implementation("io.insert-koin:koin-logger-slf4j:3.5.3")


    implementation("ch.qos.logback:logback-classic:1.4.14")


    testImplementation("io.ktor:ktor-server-tests-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}
