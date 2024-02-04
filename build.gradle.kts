val ktor_version = "2.3.8"
val kotlin_version="1.9.22"
val logback_version="1.4.14"
val logstash_encoder_version="7.4"
val exposed_version="0.47.0"
val hikaricp_version = "5.1.0"
val ktlint by configurations.creating

plugins {
    application
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "no.nav.familie"
version = "0.0.1"
application {
    mainClass.set("no.nav.familie.ApplicationKt")
}

repositories {
    mavenCentral()
}

dependencies {
    ktlint("com.pinterest:ktlint:0.51.0-FINAL") {
        attributes {
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        }
    }
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-call-id:$ktor_version")
    implementation("io.ktor:ktor-server-status-pages:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-cors:$ktor_version")
    implementation("io.ktor:ktor-client-core:$ktor_version")
    implementation("io.ktor:ktor-client-cio:$ktor_version")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_encoder_version")
    implementation("io.ktor:ktor-serialization:$ktor_version")
    implementation("io.ktor:ktor-client-serialization:$ktor_version")
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-dao:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version")
    implementation("com.zaxxer:HikariCP:$hikaricp_version")
    implementation("org.postgresql:postgresql:42.7.1")
    implementation("com.google.cloud.sql:postgres-socket-factory:1.15.2")
    implementation("org.flywaydb:flyway-core:10.7.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.7.1")
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")
    implementation("com.launchdarkly:okhttp-eventsource:4.1.1")
    testImplementation("io.ktor:ktor-server-tests:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlin_version")
    testImplementation("com.h2database:h2:2.2.224")
}


val inputFiles = project.fileTree(mapOf("dir" to "src", "include" to "**/*.kt"))

val ktlintCheck by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    // outputs.dir(outputDir)

    description = "Check Kotlin code style."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("src/**/*.kt")
}

val ktlintFormat by tasks.creating(JavaExec::class) {
    inputs.files(inputFiles)
    // outputs.dir(outputDir)

    description = "Fix Kotlin code style deviations."
    classpath = ktlint
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F", "src/**/*.kt")
    jvmArgs = listOf("--add-opens", "java.base/java.lang=ALL-UNNAMED")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn("ktlintFormat")
    dependsOn("ktlintCheck")
    tasks.findByName("ktlintCheck")?.mustRunAfter("ktlintFormat")
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "21"
        freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

tasks{
    shadowJar {
        manifest {
            attributes(Pair("Main-Class", "no.nav.familie.ApplicationKt"))
        }
        mergeServiceFiles()
    }
}
