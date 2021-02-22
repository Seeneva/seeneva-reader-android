plugins {
    kotlin("kapt")
}

android {
    defaultConfig{
        missingDimensionStrategy(RustBuildTypeFlavor.NAME, RustBuildTypeFlavor.RUST_RELEASE)
    }

    aaptOptions{
        //Add the following lines to this code block to prevent Android from compressing TensorFlow Lite model files
        noCompress("tflite")
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation(project(":data"))

    implementation(Deps.COIL)

    api(Deps.ANDROIDX_PAGING_COMMON)
    api(Deps.ANDROIDX_PALETTE)

    implementation(Deps.R_TREE)
}

