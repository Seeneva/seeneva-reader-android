/*
 *  This file is part of Seeneva Android Reader
 *  Copyright (C) 2021-2023 Sergei Solodovnikov
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    id("seeneva.android-library-conventions")
    alias(libs.plugins.kotlin.serialization)
}

android {
    defaultConfig {
        namespace = "app.seeneva.reader.logic"

        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

//    aaptOptions{
//        //Add the following lines to this code block to prevent Android from compressing TensorFlow Lite model files
//        noCompress("tflite")
//    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":common"))
    implementation(project(":data"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.core)
    implementation(libs.androidx.annotations)
    api(libs.androidx.paging.common)
    api(libs.androidx.palette)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    implementation(libs.tinylog.api)
    implementation(libs.tinylog.impl)

    implementation(libs.coil)

    implementation(libs.rTree)

    testImplementation(kotlin("test-junit"))
    testImplementation(libs.mockk)

    androidTestImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.koin.test.junit4)
    androidTestImplementation(libs.kluent) {
        exclude("com.nhaarman.mockitokotlin2")
    }
}

