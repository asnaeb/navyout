@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeMultiplatform)
    id("maven-publish")
    kotlin("plugin.serialization") version "2.2.0"
}

group = "io.github.asnaeb"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    iosX64()
    iosArm64()
    iosSimulatorArm64()
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.navigation)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(compose.runtime)
            implementation(compose.foundation)
        }

        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
        }

    }
}

android {
    namespace = "io.github.asnaeb.navyout"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral()

    //signAllPublications()

    coordinates(group.toString(), rootProject.name, version.toString())

    pom {
        name = "Navzion"
        description = "A KMP navigation library based on the official navigation lib"
        inceptionYear = "2025"
        url = "https://github.com/asnaeb/nayout/"
        licenses {
            license {
                name = "Roberto De Lucia"
                url = "github.io/asnaeb"
                distribution = "2025"
            }
        }
        developers {
            developer {
                id = "asnaeb"
                name = "Roberto De Lucia"
                url = "github.io/asnaeb"
            }
        }
        scm {
            url = "XXX"
            connection = "YYY"
            developerConnection = "ZZZ"
        }
    }
}