// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// TODO(X): Move this file out the repository root when old gradle is removed.

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
  repositories {
    mavenCentral()
    gradlePluginPortal()
  }
}

rootProject.name = "d8-r8"

// This project is temporarily located in d8_r8. When moved to root, the parent
// folder should just be removed.
includeBuild(rootProject.projectDir.parentFile.resolve("commonBuildSrc"))
includeBuild("keepanno")

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild("main")
includeBuild("test")
