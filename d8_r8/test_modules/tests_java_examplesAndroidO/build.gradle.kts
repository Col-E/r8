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
    java.srcDirs.clear()
    java.srcDir(root.resolveAll("src", "test", "examplesAndroidO"))
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  testCompileOnly(Deps.asm)
  testCompileOnly(resolve(getThirdPartyAndroidJar("lib-v26"),"android.jar"))
}

// We just need to register the examples jars for it to be referenced by other modules.
val buildExampleJars = buildExampleJars("examplesAndroidO")

val thirdPartyCompileDependenciesTask = ensureThirdPartyDependencies(
  "compileDeps",
  listOf(
    Jdk.JDK_11.getThirdPartyDependency(),
    getThirdPartyAndroidJar("lib-v26")))

tasks {
  withType<JavaCompile> {
    dependsOn(thirdPartyCompileDependenciesTask)
    options.setFork(true)
    options.compilerArgs.add("-Xlint:-options")
    options.compilerArgs.add("-parameters")
    options.forkOptions.memoryMaximumSize = "3g"
    options.forkOptions.jvmArgs = listOf(
      "-Xss256m",
      // Set the bootclass path so compilation is consistent with 1.8 target compatibility.
      "-Xbootclasspath/a:third_party/openjdk/openjdk-rt-1.8/rt.jar")
  }
}
