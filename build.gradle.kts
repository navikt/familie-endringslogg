val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val logstash_encoder_version: String by project
val exposed_version: String by project

plugins {
    application
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    id("com.github.johnrengelman.shadow") version "6.1.0"
}

group = "no.nav.familie"
version = "0.0.1"
application {
    mainClass.set("no.nav.familie.ApplicationKt")
    mainClassName = "no.nav.familie.ApplicationKt" // Need this while shadow plugin < 7
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_encoder_version")
    implementation("io.ktor:ktor-client-core:1.6.7")
    implementation("io.ktor:ktor-client-cio:1.6.7")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("org.postgresql:postgresql:42.2.23")
    implementation("com.google.cloud.sql:postgres-socket-factory:1.3.3")
    implementation("org.flywaydb:flyway-core:8.0.0-beta2")
    implementation("com.github.ben-manes.caffeine:caffeine:3.0.4")
    implementation("com.launchdarkly:okhttp-eventsource:2.3.2")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
    testImplementation("com.h2database:h2:1.3.148")
}

tasks{
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "no.nav.familie.ApplicationKt"))
        }
    }
}
