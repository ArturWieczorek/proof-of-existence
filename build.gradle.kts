plugins {
    `java-library`
    application // gives `./gradlew run --args="..."`
    id("com.diffplug.spotless") version "6.25.0"
    id("com.gradleup.shadow") version "8.3.5" // `./gradlew shadowJar` -> one runnable fat jar
}

repositories {
    mavenCentral()
}

java {
    // Pin the Java version so every machine builds identically.
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}

dependencies {
    // Cardano: build/submit transactions, read metadata, talk to a Blockfrost-compatible backend.
    // (Not needed for Chapter 01's pure-Java hashing, but used from Chapter 02 onward.)
    implementation("com.bloxbean.cardano:cardano-client-lib:0.7.2")
    implementation("com.bloxbean.cardano:cardano-client-backend-blockfrost:0.7.2")
    // Koios backend: a keyless provider so the CLI can submit on-chain with no API signup
    // (Ch08 submit; Blockfrost is still supported and is the default for the browser verify page).
    implementation("com.bloxbean.cardano:cardano-client-backend-koios:0.7.2")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.assertj:assertj-core:3.26.3")
}

// Keep parameter names in the bytecode (frameworks and readability).
tasks.withType<JavaCompile>().configureEach { options.compilerArgs.add("-parameters") }

tasks.test {
    useJUnitPlatform {
        // Fast unit tests only; tests tagged "integration" need a live devnet/testnet.
        excludeTags("integration")
    }
    testLogging { events("passed", "skipped", "failed") }
}

tasks.register<Test>("integrationTest") {
    description = "Runs tests tagged 'integration' (need a live devnet/testnet)."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("integration") }
}

spotless {
    java {
        googleJavaFormat("1.22.0")
        target("src/**/*.java")
    }
}

application {
    mainClass.set("org.poe.NotaryCli")
}

// One self-contained, runnable jar for the release: `java -jar notary.jar <command> ...`
tasks.named<Jar>("shadowJar") {
    archiveFileName.set("notary.jar")
}
