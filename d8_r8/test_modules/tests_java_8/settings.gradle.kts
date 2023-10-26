// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

pluginManagement {
  repositories {
    maven {
      url = uri("file:../../../third_party/dependencies_plugin")
    }
    maven {
      url = uri("file:../../../third_party/dependencies")
    }
  }
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("file:../../../third_party/dependencies")
    }
  }
}

rootProject.name = "tests_java_8"

val root = rootProject.projectDir.parentFile.parentFile
includeBuild(root.resolve("shared"))
includeBuild(root.resolve("keepanno"))
includeBuild(root.resolve("resourceshrinker"))

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild(root.resolve("main"))
includeBuild(root.resolve("test_modules").resolve("tests_java_9"))
includeBuild(root.resolve("test_modules").resolve("tests_java_10"))
includeBuild(root.resolve("test_modules").resolve("tests_java_11"))
includeBuild(root.resolve("test_modules").resolve("tests_java_17"))
includeBuild(root.resolve("test_modules").resolve("tests_java_20"))
