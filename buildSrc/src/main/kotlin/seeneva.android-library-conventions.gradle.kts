/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2024 Sergei Solodovnikov
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

//https://stackoverflow.com/a/70878181
val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    buildToolsVersion = "34.0.0"

    compileSdk = 34

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JvmTarget.JVM_17.target
    }

    // needed to build tests
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        // Translations can miss some strings especially after Weblate pull request
        // Lets consider this as a warning to finish 'lintRelease' Gradle task
        warning += "MissingTranslation"
    }
}

dependencies {
    // some Java 8 features
    // https://developer.android.com/studio/write/java8-support-table
    coreLibraryDesugaring(libs.android.javaDesugar)
}