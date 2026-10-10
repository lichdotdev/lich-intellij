// Everything that decides what is sent where lives here, with no IntelliJ
// dependency, so it builds and tests on a plain JDK in seconds. The IDE side
// (the root project) only gathers a selection and shows a result.
plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // The IDE ships both the Kotlin runtime and Gson, and a plugin that bundles
    // its own copy of either fights the platform's for the classes.
    compileOnly(kotlin("stdlib"))
    compileOnly("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test"))
    testImplementation("com.google.code.gson:gson:2.11.0")
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
}
