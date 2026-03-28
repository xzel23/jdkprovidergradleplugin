plugins {
    application
}

dependencies {
    implementation(project(":"))
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("com.dua3.gradle.jdkprovider.swingtester.JdkProviderSwingTester")
}
