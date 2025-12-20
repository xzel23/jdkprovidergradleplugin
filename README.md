# JDK Provider Gradle Plugin

The JDK Provider Gradle Plugin gives you fine‑grained control over the exact JDK used to build your project. It is an alternative to Gradle Toolchains and must not be used together with them.

Key points:

- Do not use together with Gradle Toolchains.
- Java compilation is automatically configured to run in forked mode.
- A JDK is resolved and linked into your project’s build directory (a symlink where supported; otherwise a copy).
- Select whether to use a JDK that includes JavaFX by setting `javaFxBundled = true/false` in the `jdk` extension.

## Requirements

- Java 21 or newer

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

Supported properties:

| Property                                                        | Type                 | Description                                                                              | Default                                           |
|-----------------------------------------------------------------|----------------------|------------------------------------------------------------------------------------------|---------------------------------------------------|
| `version`                                                       | `String`             | Java version requirement. Examples: `"21"`, `"21+"`, `"latest"`.                         | None. When empty, latest LTS version is selected. |
| `vendor`                                                        | `JvmVendorSpec`      | Specific JDK vendor, e.g. `JvmVendorSpec.ADOPTIUM`, `JvmVendorSpec.matching("GraalVM")`. | No preference (any vendor).                       |
| `os`                                                            | `OSFamily`           | Target operating system family, i.e., `OSFamily.LINUX`.                                  | Current OS.                                       |
| `arch`                                                          | `SystemArchitecture` | Target CPU architecture (e.g. `X64`, `AARCH64`).                                         | Current architecture.                             |
| `nativeImageCapable`                                            | `Boolean`            | Require a JDK that is capable of building native images (e.g., GraalVM)                  | `false`                                           |
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

Minimal example for a JavaFX build with latest LTS JDK (Kotlin DSL):

```kotlin
jdk {
     javaFxBundled.set(true)
}
```

What the plugin does:

- Resolves or locates a suitable JDK for your project and exposes it to the Gradle build.
- Configures all tasks of type `JavaCompile`. `JavaDoc`, and `JavaExec` to use the resolved JDK
- Ensures Java compilation uses a forked compiler process using the selected JDK.

## Rationale

This plugin provides a more fine‑grained and explicit way to control the JDK used for building your project than Gradle Toolchains. If you prefer strict, reproducible selection of a particular JDK flavor (for example, with or without bundled JavaFX), this plugin aims to make that straightforward.

## Compatibility and caveats

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

### [Gradle plugin for GraalVM Native Image](https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html#configuration-toolchains-enabling) - Create native applications

Since the GraalVM Native Image plugin requires a JDK with native image support, we need to configure the JDK to support
native image generation:

```kotlin
    jdk {
        nativeImageCapable = true
    }
```

This makes sure that a JDK with native image support is installed and used for compilation. But the GraalVM plugin needs
to be configured using a toolchain, and Gradle is not able to automatically detect the correct JDK with native image support.

Since the downloaded JDK can only be resolved after the evaluation phase, the configuration needs to be done in an afterEvaluate block:

```kotlin
project.afterEvaluate {
    graalvmNative {
        binaries {
            named("main") {
                imageName.set("hello_native")
                mainClass.set("com.example.HelloNative")
                this.javaLauncher.set(jdk.getJavaLauncher(project))
            }
        }
    }
}
```

Have a look at the helloNative sample project to see how to put everything together.

### Known issues

- Compatibility with the [beryx-runtime-plugin](https://github.com/beryx/badass-runtime-plugin) has not yet been tested. If you find any issues, please report them 
  here. If something in the runtime plugin needs to be fixed or changed, I will look into it.
- Please report any other issues to this project's [Issues Page](https://github.com/xzel23/jdkprovidergradleplugin/issues).

## Building the plugin

- Make sure Java 21+ is installed.
- On Windows only, the javafx-jlink sample needs the WiX toolset installed to create an installer, see above for 
  instructions. The installation is described above.
- Clone the project.
- Run `./gradlew build` or (`.\gradlew.bat build` on Windows).

## License

This project is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See the `LICENSE` file for details.

Copyright (c) Axel Howind.

## Contributing

Contributions are welcome! Please see `CONTRIBUTING.md` for guidelines.
