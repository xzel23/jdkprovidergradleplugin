rootProject.name = "javafx-minimal-sample"

// Resolve the plugin from the local source build via composite build.
// Adjust the relative path if you move the sample.
pluginManagement {
    includeBuild("../../")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
