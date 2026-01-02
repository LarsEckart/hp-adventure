plugins {
    java
    application
    id("com.gradleup.shadow") version "9.0.0-beta4"
}

group = "com.example"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    implementation("io.javalin:javalin:6.1.6")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.slf4j:slf4j-simple:2.0.13")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("io.javalin:javalin-testtools:6.1.6")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.example.hpadventure.App")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("hp-adventure")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
