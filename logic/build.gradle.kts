plugins {
    kotlin("kapt")
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":data"))

    api(Deps.ANDROIDX_PAGING_COMMON)
    api(Deps.ANDROIDX_PALETTE)

    api(Deps.GLIDE) {
        exclude("androidx.fragment")
    }

    kapt(Deps.GLIDE_COMPILER)
}

