plugins {
    application
    id("com.dua3.gradle.jdkprovider")
}


jdkProvider {
    version = "25"
    vendor = JvmVendorSpec.AZUL
    javaFxBundled = true
}

application {
    mainClass.set("com.example.HelloFx")
}

// Compile and run with the JavaFX controls module enabled (assuming a JavaFX-bundled JDK)
tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("--add-modules=javafx.controls"))
}

tasks.named<JavaExec>("run") {
    jvmArgs = listOf("--add-modules=javafx.controls")
}
