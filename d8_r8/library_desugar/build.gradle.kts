// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// import java.nio.file.Paths
// import net.ltgt.gradle.errorprone.errorprone

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "library_desugar", "java"))
    output.resourcesDir = getRoot().resolveAll("build", "classes", "library_desugar_conversions")
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
}

tasks {
  withType<JavaCompile> {
    options.setFork(true)
    options.forkOptions.memoryMaximumSize = "3g"
    options.forkOptions.jvmArgs = listOf(
      "-Xss256m",
      // Set the bootclass path so compilation is consistent with 1.8 target compatibility.
      "-Xbootclasspath/a:third_party/openjdk/openjdk-rt-1.8/rt.jar")
  }
}