# JDK Provider Gradle Plugin
![GitHub release (latest by date)](https://img.shields.io/github/v/release/xzel23/jdkprovidergradleplugin)

![Teaser Image](images/teaser.jpg)

The JDK Provider Gradle Plugin gives you fine‑grained control over the exact JDK used to build your project. It is an alternative to Gradle Toolchains and must not be used together with them.

### TL;DR: Why use this plugin?

- **Reliable JavaFX support:** Configures a JDK with JavaFX enabled (Gradle toolchains cannot do that reliably).
- **Windows ARM support:** Works on Windows ARM (unlike the JavaFX Gradle plugin).
- **Easy configuration:** Simple setup via the `jdk` extension.
- **FOSS:** Free and Open Source Software (GPL v3).

## Table of Contents

- [TL;DR: Why use this plugin?](#tldr-why-use-this-plugin)
- [Requirements](#requirements)
- [Applying the plugin](#applying-the-plugin)
- [Configuration](#configuration)
- [Rationale](#rationale)
- [Compatibility with Gradle features and plugins](#compatibility-with-gradle-features-and-plugins)
  - [Gradle Toolchains](#gradle-toolchains)
  - [Plugins that execute JDK executables directly](#plugins-that-execute-jdk-executables-directly)
  - [Beryx JLink Plugin - Create application installers](#beryx-jlink-plugin---create-application-installers)
  - [Gradle plugin for GraalVM Native Image – Create native applications](#gradle-plugin-for-graalvm-native-image--create-native-applications)
    - [Important Note on using GraalVM on Windows](#important-note-on-using-graalvm-on-windows)
  - [Multi-Release JARs](#multi-release-jars)
  - [Known issues](#known-issues)
- [Building the plugin](#building-the-plugin)
- [License](#license)
- [Contributing](#contributing)

Key points:

- A compatible JDK is resolved and if no matching ine is found, automatically resolved and downloaded using the Foojay DiscoAPI. Later builds will use the already downloaded JDK.
- To select a JDK that includes JavaFX, set `javaFxBundled = true` in the `jdk` extension.
- To select a JDK that supports native compilation, set `nativeImageCapable = true` in the `jdk` extension.
- The plugin supports Linux, Windows and macOS on both x64 and aarch64.
- Java compilation will run in forked mode to use the resolved JDK.
- This plugin is an alternative to Gradle toolchains, do not mix!
- Look at the samples to see how to build installers and native executables.

## Requirements

- Java 21 or newer
- Gradle 9+ (for Windows ARM: Gradle 9.2.1)

## Applying the plugin

Kotlin DSL (`build.gradle.kts`):

```kotlin
plugins {
    id("com.dua3.gradle.jdkprovider") version "<plugin_version>"
}
```

Groovy DSL (`build.gradle`):

```groovy
plugins {
    id 'com.dua3.gradle.jdkprovider' version '<plugin_version>'
}
```

## Configuration

Configure the plugin via the `jdk` extension. All properties are optional; unspecified values mean "no preference" and will be determined automatically where applicable.

Minimal example for a JavaFX build with the latest LTS JDK (Kotlin DSL):

```kotlin
jdk {
    version = 25
    javaFxBundled = true
}
```

Supported properties:

| Property                                                        | Type                 | Description                                                                              | Default                                           |
|-----------------------------------------------------------------|----------------------|------------------------------------------------------------------------------------------|---------------------------------------------------|
| `version`                                                       | `String`             | Java version requirement. Examples: `"21"`, `"21+"`, `"latest"`.                         | `"latest"` (latest available version).            |
| `vendor`                                                        | `JvmVendorSpec`      | Specific JDK vendor, e.g. `Azul`.                                                        | No preference (any vendor).                       |
| `os`                                                            | `OSFamily`           | Target operating system family, i.e., `OSFamily.LINUX`.                                  | Current OS.                                       |
| `arch`                                                          | `SystemArchitecture` | Target CPU architecture (e.g. `X64`, `AARCH64`).                                         | Current architecture.                             |
| `nativeImageCapable`                                            | `Boolean`            | Require a JDK that is capable of building native images (e.g., GraalVM).                 | `false`                                           |
| `javaFxBundled`                                                 | `Boolean`            | Require a JDK that bundles JavaFX (`true`) or excludes it (`false`).                     | `false`                                           |
| `automaticDownload`                                             | `Boolean`            | Allow automatic download of matching JDKs (ignored when Gradle is in offline mode).      | `true`                                            |

Example (Kotlin DSL):

```kotlin
jdk {
    // Choose any 21+ JDK (will return latest version >21)
    version.set("21+")
    
    // Vendor Adoptium
    vendor.set(org.gradle.jvm.toolchain.JvmVendorSpec.ADOPTIUM)

    // Prefer a JDK without JavaFX bundled
    javaFxBundled.set(false)

    // Require native-image capable JDKs (e.g., some Graal VM builds)
    nativeImageCapable.set(true)

    // Control whether the plugin may download missing JDKs (default: true)
    automaticDownload.set(true)
}
```

What the plugin does:

- Resolves or locates a suitable JDK for your project and exposes it to the Gradle build.
- Configures all tasks of type `JavaCompile`. `JavaDoc`, and `JavaExec` to use the resolved JDK
- Ensures Java compilation uses a forked compiler process using the selected JDK.

## Rationale

This plugin provides a more fine‑grained and explicit way to control the JDK used for building your project than Gradle Toolchains. If you prefer strict, reproducible selection of a particular JDK flavor (for example, with or without bundled JavaFX), this plugin aims to make that straightforward.

## Compatibility with Gradle features and plugins

### Gradle Toolchains

This plugin is not compatible with Gradle toolchains, it replaces the Gradle toolchain mechanism.

### Plugins that execute JDK executables directly

Plugins that execute JDK executables directly, i.e., do not use a JavaExec task, need to be configured manually.

Information about the selected JDK is available after the plugin has been applied to the project through the
extension properties `jdk.jdkHome` (type Directory) and `jdk.jdkSpec` (type JdkSpec).

Example how to configure the Badass JLink plugin (use version 3.2.0+):

```kotlin
    jlink.javaHome = jdk.jdkHome
```

### [Beryx JLink Plugin](https://github.com/beryx/badass-jlink-plugin) - Create application installers

- If on Windows, read how to install the WiX toolset.
- Configure both the `application`, `jdk` and `jlink` extensions in your build file.
- Run the `jpackage` task
- IMPORTANT: You need version 3.2.0 of the plugin that contains a fix for resolving the correct `jpackage` executable.

An example `build.gradle.kts`:

```kotlin
    plugins {
        application
        id("com.dua3.gradle.jdkprovider") version "0.1.0"
        id("org.beryx.jlink") version "3.2.0"
    }
    
    jdk {
        version = "25"
        javaFxBundled = true
    }

    application {
        mainClass.set("com.example.Main")
        mainModule.set("my_module")
    }

    jlink {
        // IMPORTANT: tell the jlink plugin where to find the correct JDK
        javaHome = jdk.jdkHome
        
        options = setOf("--strip-debug", "--no-header-files", "--no-man-pages")
        jpackage {
            imageName = "MyApplication"
            installerName = "MyInstaller"
        }
    } 
```

**Note:** On Windows ARM, you need at least Gradle 9.2.1 (fixed ARM compatibility on Windows) and JDK 25 (added 
jpackage compatibility with version 4+ of the WiX toolset; version 3.x does not work on Windows ARM).

**Installing the WiX toolset on Windows (both ARM and x64)**

- Install [dotnet](https://dotnet.microsoft.com/en-us/download).
- Install the WiX Toolset*: `dotnet tool install --global wix --version 5.0.2`
- Install WiX extensions: `wix extension add -g WixToolset.Util.wixext/5.0.2`

(*) Note that I explicitly use version 5.0.2 here as WiX have introduced a "maintenance fee" for users of version 6 or
above and binaries for newer versions are not freely available anymore.

### [Gradle plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration-toolchains-enabling) – Create native applications

Since the GraalVM Native Image plugin requires a JDK with native image support, we need to configure the JDK to support
native image generation:

```kotlin
    jdk {
        nativeImageCapable = true
    }
```

This ensures that a JDK with native image support is installed and used for compilation. However, the GraalVM plugin
requires manual configuration, as Gradle cannot automatically detect the correct JDK with native image support when
using toolchains.

To configure the GraalVM plugin to use the correct JDK, set the `javaLauncher` property. This works because
`jdk.getJavaLauncher(project)` returns a Provider that is evaluated lazily when needed, i.e., after the evaluation phase:

```kotlin
  graalvmNative {
      binaries {
          named("main") {
              this.javaLauncher = jdk.getJavaLauncher(project)
              imageName.set("hello_native")
              mainClass.set("com.example.HelloNative")
          }
      }
  }
```

Have a look at the helloNative sample project to see how to put everything together.

#### Important Note on using GraalVM on Windows

- **Windows ARM:** There currently is no GraalVM on Windows ARM.

- **Windows x64:** GraalVM on Windows x64 has an error that leads to native compilation failing with the message
  "'other' has different root when building."

  To this, add `-Djava.io.tmpdir=...` to the Gradle command line to point to a temp directory on the same drive 
  as your project.
  For details, read the corresponding [GraalVM issue](https://github.com/graalvm/native-build-tools/issues/754).**

### Multi-Release JARs

The plugin supports creating multi-release JARs by allowing you to override the JDK for specific source sets. When an override is defined for a source set, the plugin automatically configures the corresponding compilation, testing, and execution tasks to use that JDK. 

Additionally, for Java 9 and newer, the plugin automatically sets the `compilerOptions.release` flag to match the JDK version if it hasn't been manually configured.

Example (Kotlin DSL):

```kotlin
sourceSets {
    create("java17") {
        java {
            srcDirs("src/main/java17")
        }
    }
    create("java21") {
        java {
            srcDirs("src/main/java21")
        }
    }
}

jdk {
    version.set(11) // Global JDK used for the 'main' source set
    overrides {
        create("java17") { // Override for the 'java17' source set
            version.set(17)
        }
        create("java21") { // Override for the 'java21' source set
            version.set(21)
        }
    }
}

val java17Compile = tasks.named<JavaCompile>("compileJava17Java")
val java21Compile = tasks.named<JavaCompile>("compileJava21Java")

tasks.named<Jar>("jar") {
    // Include the classes compiled with Java 17 in the correct location
    into("META-INF/versions/17") {
        from(java17Compile.map { it.destinationDirectory })
    }
    // Include the classes compiled with Java 21 in the correct location
    into("META-INF/versions/21") {
        from(java21Compile.map { it.destinationDirectory })
    }
    manifest {
        attributes("Multi-Release" to true)
    }
}

java17Compile.configure {
    // Multi-release sources usually depend on the main classes
    classpath += sourceSets.main.get().output
}

java21Compile.configure {
    // Multi-release sources usually depend on the main classes
    classpath += sourceSets.main.get().output
}
```

In this example:
- The `main` source set is compiled using Java 11.
- The `java17` source set is compiled using Java 17.
- The `java21` source set is compiled using Java 21.
- The `jar` task is configured to package the Java 17 and Java 21 classes into the correct `META-INF/versions/` locations.
- The plugin automatically sets `--release 11` for `compileJava`, `--release 17` for `compileJava17Java`, and `--release 21` for `compileJava21Java`.

See the `samples/multi-release` project for a complete working example.

### Known issues

- Compatibility with the [beryx-runtime-plugin](https://github.com/beryx/badass-runtime-plugin) has not yet been tested. If you find any issues, please report them 
  here. If something in the runtime plugin needs to be fixed or changed, I will look into it.
- Please report any other issues to this project's [Issues Page](https://github.com/xzel23/jdkprovidergradleplugin/issues).

## Building the plugin

- Make Java 21 or later is installed.
- On Windows only, the javafx-jlink sample needs the WiX toolset installed to create an installer.
  Installation instructions are given above in the description of the JLink plugin.
- Clone the project.
- Run `./gradlew build` (macOS, Linux) or `.\gradlew.bat build` (Windows).

## License

This project is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License or (at your option) any later version. See the `LICENSE` file for details.

Copyright (c) Axel Howind.

## Contributing

Contributions are welcome! Please see `CONTRIBUTING.md` for guidelines.
