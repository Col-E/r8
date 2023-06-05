// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

pluginManagement {
    repositories {
        maven {
            url = uri("file:../../../third_party/dependencies")
        }
        maven {
            url = uri("file:../../../third_party/dependencies_new")
        }
    }
}

dependencyResolutionManagement {
  repositories {
    maven {
        url= uri("file:../third_party/dependencies")
    }
    maven {
        url= uri("file:../third_party/dependencies_new")
    }
  }
}

rootProject.name = "r8-java8-tests"

val d8Root = rootProject.projectDir.parentFile.parentFile
val root = d8Root.parentFile

includeBuild(root.resolve("commonBuildSrc"))
includeBuild(d8Root.resolve("keepanno"))

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild(d8Root.resolve("main"))
