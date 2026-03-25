plugins {
    java
    id("com.dua3.gradle.jdkprovider")
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

jdk {
    version = "25.0.1+"
    nativeImageCapable = false
}
