import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.getting
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

plugins {
    java
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

@Suppress("UnstableApiUsage")
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()
        }

        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(platform(libs.findLibrary("spring-boot-bom").get()))
                implementation(platform(libs.findLibrary("aws-sdk-bom").get()))
                implementation(libs.findLibrary("spring-boot-starter").get())
                implementation(libs.findLibrary("spring-boot-starter-kafka").get())
                implementation(libs.findLibrary("spring-boot-starter-test").get())
                implementation(libs.findLibrary("testcontainers").get())
                implementation(libs.findLibrary("testcontainers-junit-jupiter").get())
                implementation(libs.findLibrary("testcontainers-kafka").get())
                implementation(libs.findLibrary("testcontainers-localstack").get())
                implementation(libs.findLibrary("aws-sdk-s3").get())
            }

            targets {
                all {
                    testTask.configure {
                        description = "Runs Spring Boot + Testcontainers integration tests."
                        group = "verification"
                        shouldRunAfter(tasks.named<Test>("test"))
                    }
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(testing.suites.named("integrationTest"))
}

