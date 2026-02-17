import com.google.protobuf.gradle.*
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType

plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val protobufVersion = libsCatalog.findVersion("protobuf").get().requiredVersion

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kafka.clients)
    api(libs.protobuf.java)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

val sourceSets = extensions.getByType<SourceSetContainer>()
val mainSourceSet = sourceSets.named("main").get()
val integrationTestSourceSet = sourceSets.create("integrationTest")
integrationTestSourceSet.compileClasspath += mainSourceSet.output
integrationTestSourceSet.runtimeClasspath += mainSourceSet.output

configurations.named(integrationTestSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named(integrationTestSourceSet.runtimeOnlyConfigurationName) {
    extendsFrom(configurations.testRuntimeOnly.get())
}

dependencies {
    add(integrationTestSourceSet.implementationConfigurationName, platform(libs.spring.boot.bom))
    add(integrationTestSourceSet.implementationConfigurationName, platform(libs.testcontainers.bom))
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.boot.starter)
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.boot.starter.test)
    add(integrationTestSourceSet.implementationConfigurationName, libs.spring.kafka)
    add(integrationTestSourceSet.implementationConfigurationName, libs.testcontainers.junit.jupiter)
    add(integrationTestSourceSet.implementationConfigurationName, libs.testcontainers.kafka)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.register<Test>("integrationTest") {
    description = "Runs Spring Boot + Testcontainers integration tests."
    group = "verification"
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.test)
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
