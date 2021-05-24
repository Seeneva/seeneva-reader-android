/*
 * This file is part of Seeneva Android Reader
 * Copyright (C) 2021 Sergei Solodovnikov
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

object Version {
    const val ANDROID_JAVA8_DESUGAR = "1.1.5"

    const val KOTLIN = "1.5.0"
    const val KOTLINX_COROUTINES = "1.5.0"
    const val KOTLINX_SERIALIZATION_JSON = "1.2.1"

    const val ANDROIDX_APPCOMPAT = "1.2.0"
    const val ANDROIDX_ANNOTATIONS = "1.2.0"
    const val ANDROIDX_VIEW_PAGER = "1.0.0"
    const val ANDROIDX_RECYCLER_VIEW = "1.2.0"
    const val ANDROIDX_RECYCLER_VIEW_SELECTION = "1.1.0"
    const val ANDROIDX_FRAGMENT_KTX = "1.2.5"
    const val ANDROIDX_CONSTRAINT_LAYOUT = "2.0.4"
    const val ANDROIDX_CORE_KTX = "1.3.2"
    const val ANDROIDX_LIFECYCLE = "2.3.1"
    const val ANDROIDX_PAGING = "3.0.0"
    const val ANDROIDX_PALETTE = "1.0.0"
    const val ANDROIDX_TEST_CORE_KTX = "1.3.0"
    const val ANDROIDX_TEST_JUNIT_KTX = "1.1.2"
    const val ANDROIDX_WORK_RUNTIME = "2.4.0"
    const val ANDROIDX_ROOM = "2.3.0"
    const val ANDROIDX_SWIPE_REFRESH_LAYOUT = "1.1.0"
    const val ANDROIDX_TEST_RUNNER = "1.3.0"

    const val MATERIAL = "1.2.1"

    const val COIL = "1.1.1"

    const val KOIN = "2.2.2"

    const val MOCKK = "1.10.6"

    const val KOTLIN_FAKER = "1.6.0"

    const val KLUENT = "1.65"

    const val TINYLOG = "2.3.1"

    const val SCALE_IMAGE_VIEW = "3.10.0"

    const val R_TREE = "0.9-RC1"
}

object Deps {
    const val ANDROID_JAVA8_DESUGAR =
        "com.android.tools:desugar_jdk_libs:${Version.ANDROID_JAVA8_DESUGAR}"

    const val ANDROIDX_APPCOMPAT =
        "androidx.appcompat:appcompat:${Version.ANDROIDX_APPCOMPAT}"
    const val ANDROIDX_CORE_KTX =
        "androidx.core:core-ktx:${Version.ANDROIDX_CORE_KTX}"
    const val ANDROIDX_FRAGMENT_KTX =
        "androidx.fragment:fragment-ktx:${Version.ANDROIDX_FRAGMENT_KTX}"
    const val ANDROIDX_ANNOTATIONS =
        "androidx.annotation:annotation:${Version.ANDROIDX_ANNOTATIONS}"
    const val ANDROIDX_VIEW_PAGER =
        "androidx.viewpager2:viewpager2:${Version.ANDROIDX_VIEW_PAGER}"
    const val ANDROIDX_RECYCLER_VIEW =
        "androidx.recyclerview:recyclerview:${Version.ANDROIDX_RECYCLER_VIEW}"
    const val ANDROIDX_RECYCLER_VIEW_SELECTION =
        "androidx.recyclerview:recyclerview-selection:${Version.ANDROIDX_RECYCLER_VIEW_SELECTION}"
    const val ANDROIDX_CONSTRAINT_LAYOUT =
        "androidx.constraintlayout:constraintlayout:${Version.ANDROIDX_CONSTRAINT_LAYOUT}"

    const val ANDROIDX_LIFECYCLE_RUNTIME =
        "androidx.lifecycle:lifecycle-runtime-ktx:${Version.ANDROIDX_LIFECYCLE}"
    const val ANDROIDX_LIFECYCLE_SERVICE =
        "androidx.lifecycle:lifecycle-service:${Version.ANDROIDX_LIFECYCLE}"
    const val ANDROIDX_LIFECYCLE_VIEWMODEL =
        "androidx.lifecycle:lifecycle-viewmodel-ktx:${Version.ANDROIDX_LIFECYCLE}"
    const val ANDROIDX_LIFECYCLE_LIVEDATA =
        "androidx.lifecycle:lifecycle-livedata-ktx:${Version.ANDROIDX_LIFECYCLE}"
    const val ANDROIDX_LIFECYCLE_JAVA8 =
        "androidx.lifecycle:lifecycle-common-java8:${Version.ANDROIDX_LIFECYCLE}"

    const val ANDROIDX_PAGING_COMMON =
        "androidx.paging:paging-common-ktx:${Version.ANDROIDX_PAGING}"
    const val ANDROIDX_PAGING_RUNTIME =
        "androidx.paging:paging-runtime-ktx:${Version.ANDROIDX_PAGING}"

    const val ANDROIDX_ROOM_KTX =
        "androidx.room:room-ktx:${Version.ANDROIDX_ROOM}"
    const val ANDROIDX_ROOM_COMPILER =
        "androidx.room:room-compiler:${Version.ANDROIDX_ROOM}"

    const val ANDROIDX_SWIPE_REFRESH_LAYOUT =
        "androidx.swiperefreshlayout:swiperefreshlayout:${Version.ANDROIDX_SWIPE_REFRESH_LAYOUT}"

    const val ANDROIDX_PALETTE = "androidx.palette:palette-ktx:${Version.ANDROIDX_PALETTE}"

    const val ANDROIDX_WORK_RUNTIME =
        "androidx.work:work-runtime-ktx:${Version.ANDROIDX_WORK_RUNTIME}"

    const val MATERIAL =
        "com.google.android.material:material:${Version.MATERIAL}"

    const val KOTLINX_COROUTINES_ANDROID =
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:${Version.KOTLINX_COROUTINES}"
    const val KOTLINX_COROUTINES_CORE =
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Version.KOTLINX_COROUTINES}"

    const val KOTLINX_SERIALIZATION_JSON =
        "org.jetbrains.kotlinx:kotlinx-serialization-json:${Version.KOTLINX_SERIALIZATION_JSON}"

    //const val KOIN_ANDROID = "io.insert-koin:koin-android:${Version.KOIN}"
    const val KOIN_ANDROIDX_SCOPE =
        "io.insert-koin:koin-androidx-scope:${Version.KOIN}"
    const val KOIN_ANDROIDX_VIEWMODEL =
        "io.insert-koin:koin-androidx-viewmodel:${Version.KOIN}"
    const val KOIN_ANDROIDX_WORKMANAGER =
        "io.insert-koin:koin-androidx-workmanager:${Version.KOIN}"


    const val COIL =
        "io.coil-kt:coil-base:${Version.COIL}"

    const val TINYLOG_API =
        "org.tinylog:tinylog-api-kotlin:${Version.TINYLOG}"
    const val TINYLOG_IMPL =
        "org.tinylog:tinylog-impl:${Version.TINYLOG}"

    const val SCALE_IMAGE_VIEW =
        "com.davemorrissey.labs:subsampling-scale-image-view-androidx:${Version.SCALE_IMAGE_VIEW}"

    const val R_TREE =
        "com.github.davidmoten:rtree2:${Version.R_TREE}"
}

object TestDeps {
    const val ANDROIDX_TEST_RUNNER =
        "androidx.test:runner:${Version.ANDROIDX_TEST_RUNNER}"
    const val ANDROIDX_TEST_CORE_KTX =
        "androidx.test:core-ktx:${Version.ANDROIDX_TEST_CORE_KTX}"
    const val ANDROIDX_TEST_JUNIT_KTX =
        "androidx.test.ext:junit-ktx:${Version.ANDROIDX_TEST_JUNIT_KTX}"

    const val KOTLINX_COROUTINES_TEST =
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:${Version.KOTLINX_COROUTINES}"

    const val KOIN_TEST = "io.insert-koin:koin-test:${Version.KOIN}"

    const val MOCKK = "io.mockk:mockk:${Version.MOCKK}"

    const val KOTLIN_FAKER = "io.github.serpro69:kotlin-faker:${Version.KOTLIN_FAKER}"

    const val KLUENT = "org.amshove.kluent:kluent-android:${Version.KLUENT}"
}