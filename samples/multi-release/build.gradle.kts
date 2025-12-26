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
    create("java21") {
        java {
            srcDirs("src/main/java21")
        }
    }
    create("java25") {
        java {
            srcDirs("src/main/java25")
        }
    }
}

jdk {
    version = 17
    nativeImageCapable = false
    overrides {
        create("java21") {
            version = 21
            nativeImageCapable = false
        }
        create("java25") {
            version = 25
            nativeImageCapable = false
        }
    }
}

val java21Compile = tasks.named<JavaCompile>("compileJava21Java")
val java25Compile = tasks.named<JavaCompile>("compileJava25Java")

tasks.named<Jar>("jar") {
    into("META-INF/versions/21") {
        from(java21Compile.map { it.destinationDirectory })
    }
    into("META-INF/versions/25") {
        from(java25Compile.map { it.destinationDirectory })
    }
    manifest {
        attributes("Multi-Release" to true)
    }
}

tasks.named<JavaCompile>("compileJava") {
}

java21Compile.configure {
    classpath += sourceSets.main.get().output
}

java25Compile.configure {
    classpath += sourceSets.main.get().output
}
