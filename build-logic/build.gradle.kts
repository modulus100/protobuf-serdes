plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

dependencies {
    implementation("me.champeau.jmh:jmh-gradle-plugin:0.7.3")
}
