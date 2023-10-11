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

    implementation(project(":data"))

    implementation(Deps.COIL)

    api(Deps.ANDROIDX_PAGING_COMMON)
    api(Deps.ANDROIDX_PALETTE)

    implementation(Deps.R_TREE)
}

