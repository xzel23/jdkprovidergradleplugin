plugins {
    java
    id("com.dua3.gradle.jdkprovider")
}

group = "com.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

sourceSets {
    create("java17") {
        java {
            srcDirs("src/main/java17")
        }
    }
}

jdk {
    version.set(11)
    overrides {
        create("java17") {
            version.set(17)
        }
    }
}

val java17Compile = tasks.named<JavaCompile>("compileJava17Java")

tasks.named<Jar>("jar") {
    into("META-INF/versions/17") {
        from(java17Compile.map { it.destinationDirectory })
    }
    manifest {
        attributes("Multi-Release" to true)
    }
}

tasks.named<JavaCompile>("compileJava") {
}

java17Compile.configure {
    classpath += sourceSets.main.get().output
}
