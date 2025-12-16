# JDK Provider Gradle Plugin

The JDK Provider Gradle Plugin gives you fine‑grained control over the exact JDK used to build your project. It is an alternative to Gradle Toolchains and must not be used together with them.

Key points:

- Do not use together with Gradle Toolchains.
- Java compilation is automatically configured to run in forked mode.
- A JDK is resolved and linked into your project’s build directory (a symlink where supported; otherwise a copy).
- Select whether to use a JDK that includes JavaFX by setting `javaFxBundled = true/false` in the `jdk` extension.

## Requirements

- Gradle 9.x
- Java 21 or newer on the machine running Gradle (for the build itself)

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

Supported properties (with defaults):

- `version: String` — Java version requirement. Examples: `"21"`, `">=21"`, `"17..21"`, or semantic ranges supported by the plugin. Default: `"any"`.
- `vendor: org.gradle.jvm.toolchain.JvmVendorSpec` — Specific JDK vendor, e.g. `JvmVendorSpec.ADOPTIUM`, `JvmVendorSpec.ORACLE`, or `JvmVendorSpec.matching("GraalVM")`. Default: no preference (any vendor).
- `os: com.dua3.gradle.jdkprovider.types.OSFamily` — Target operating system family (`LINUX`, `WINDOWS`, `MACOS`). Default: current OS.
- `arch: com.dua3.gradle.jdkprovider.types.SystemArchitecture` — Target CPU architecture (e.g. `X64`, `AARCH64`). Default: current architecture.
- `nativeImageCapable: Boolean` — Require a JDK that is capable of building native images (e.g., GraalVM distributions). Default: no preference.
- `javaFxBundled: Boolean` — Require a JDK that bundles JavaFX (`true`) or excludes it (`false`). Default: no preference.
- `automaticDownload: Boolean` — Allow automatic download of matching JDKs when not found locally (ignored when Gradle is in offline mode). Default: `true`.

Example (Kotlin DSL):

```kotlin
jdk {
    // Choose any 21+ JDK 
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
- Creates a symlink (or a copy if symlinks are unsupported) of the selected JDK inside `build/jdk` of the project.
- Ensures Java compilation uses a forked compiler process using the selected JDK.

## Rationale

This plugin provides a more fine‑grained and explicit way to control the JDK used for building your project than Gradle Toolchains. If you prefer strict, reproducible selection of a particular JDK flavor (for example, with or without bundled JavaFX), this plugin aims to make that straightforward.

## Compatibility and caveats

- Not compatible with Gradle Toolchains. Do not enable Gradle Toolchains in a project that applies this plugin.
- The plugin requires Gradle 9.x and a host JDK of version 21 or newer.

## License

This project is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. See the `LICENSE` file for details.

Copyright (c) Axel Howind.

## Contributing

Contributions are welcome! Please see `CONTRIBUTING.md` for guidelines.
