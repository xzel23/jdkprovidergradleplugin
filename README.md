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

Example how to configure the BadAss JLink plugin:

```kotlin
    jlink.javaHome.set( jdk.jdkHome.map { it.asFile.absolutePath })
```

## Building

- Make sure Java 21+ is installed.
- Clone the project.
- Run `./gradlew build` or (`.\gradlew.bat build` on Windows).

## License

This project is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See the `LICENSE` file for details.

Copyright (c) Axel Howind.

## Contributing

Contributions are welcome! Please see `CONTRIBUTING.md` for guidelines.
