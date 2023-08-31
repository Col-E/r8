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
    java {
      srcDir(root.resolveAll("src", "test", "bootstrap"))
    }
  }
  // We are using a new JDK to compile to an older language version, which is not directly
  // compatible with java toolchains.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

val testJar = projectTask("tests_java_8", "testJar")

dependencies {
  implementation(files(testJar.outputs.files.getSingleFile()))
  implementation(projectTask("main", "jar").outputs.files)
  implementation(Deps.asm)
  implementation(Deps.asmCommons)
  implementation(Deps.asmUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.junit)
  implementation(Deps.kotlinMetadata)
  implementation(Deps.fastUtil)
}

val mainR8RelocatedTask = projectTask("main", "r8WithRelocatedDeps")

tasks {
  withType<JavaCompile> {
    dependsOn(testJar)
    dependsOn(gradle.includedBuild("main").task(":jar"))
  }

  withType<KotlinCompile> {
    kotlinOptions {
      // We are using a new JDK to compile to an older language version, which is not directly
      // compatible with java toolchains.
      jvmTarget = "1.8"
    }
  }

  withType<Test> {
    environment.put("USE_NEW_GRADLE_SETUP", "true")
    dependsOn(mainR8RelocatedTask)
    environment.put("R8_WITH_RELOCATED_DEPS", mainR8RelocatedTask.outputs.files.getSingleFile())
    environment.put("R8_RUNTIME_PATH", mainR8RelocatedTask.outputs.files.getSingleFile())

    // TODO(b/291198792): Remove this exclusion when desugared library runs correctly.
    exclude("com/android/tools/r8/bootstrap/HelloWorldCompiledOnArtTest**")
  }
}
