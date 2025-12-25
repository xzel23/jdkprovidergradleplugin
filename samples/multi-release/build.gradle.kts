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
    create("java25") {
        java {
            srcDirs("src/main/java25")
        }
    }
}

jdk {
    version.set(11)
    overrides {
        create("java17") {
            version.set(17)
        }
        create("java25") {
            version.set(25)
        }
    }
}

val java17Compile = tasks.named<JavaCompile>("compileJava17Java")
val java25Compile = tasks.named<JavaCompile>("compileJava25Java")

tasks.named<Jar>("jar") {
    into("META-INF/versions/17") {
        from(java17Compile.map { it.destinationDirectory })
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

java17Compile.configure {
    classpath += sourceSets.main.get().output
}

java25Compile.configure {
    classpath += sourceSets.main.get().output
}
