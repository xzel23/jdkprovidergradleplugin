plugins {
    `java-library`
    `java-gradle-plugin`
    `maven-publish`
}

group = "com.dua3.gradle"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // Compile against Gradle public API (needed for resolver SPI types)
    implementation(gradleApi())

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Lightweight archive extraction for tar/gzip support
    implementation("org.apache.commons:commons-compress:1.28.0")
    // Minimal JSON parsing for DiscoAPI responses
    implementation("org.json:json:20240303")
    // Gradle TestKit and project builder for functional/unit tests
    testImplementation(gradleTestKit())
    // Mock HTTP server for DiscoAPI tests
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        register("jdkProvider") {
            id = "com.dua3.gradle.jdkprovider"
            implementationClass = "com.dua3.gradle.jdkprovider.plugin.JdkProviderPlugin"
            displayName = "JDK Provider Plugin"
            description = "Resolves Java toolchains using Foojay/DiscoAPI and provision it to Gradle build."
        }
    }
}
