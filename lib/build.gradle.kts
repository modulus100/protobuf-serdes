plugins {
    `java-library`
    alias(libs.plugins.protobuf)
    id("protobuf-serdes.integration-test-conventions")
    id("protobuf-serdes.jmh-conventions")
}

val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
val protobufVersion = libsCatalog.findVersion("protobuf").get().requiredVersion

repositories {
    mavenCentral()
}

dependencies {
    api(libs.kafka.clients)
    api(libs.protobuf.java)
    implementation(libs.guava)
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.sdk.s3)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
