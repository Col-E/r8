// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure {
    java.srcDir(root.resolveAll("src", "test", "examplesJava10"))
  }
  sourceCompatibility = JavaVersion.VERSION_1_10
  targetCompatibility = JavaVersion.VERSION_1_10
}

dependencies { }

// We just need to register the examples jars for it to be referenced by other modules.
val buildExampleJars = buildExampleJars("examplesJava10")

tasks {
  withType<JavaCompile> {
    options.setFork(true)
    options.forkOptions.memoryMaximumSize = "3g"
    options.forkOptions.executable = getCompilerPath(Jdk.JDK_11)
  }
}

