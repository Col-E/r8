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
  sourceSets.main.configure {
    java.srcDir(root.resolveAll("src", "test", "java"))
  }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}


// We cannot use languageVersion.set(JavaLanguageVersion.of("8")) because gradle cannot figure
// out that the jdk is 1_8 and will try to download it.
tasks.withType<KotlinCompile> {
  kotlinOptions {
    jvmTarget = "1.8"
  }
}

// The test module compilation depends on main and keep anno output, but we cannot directly
// reference the task we only obtain a task reference. To obtain the actual reference by creating
// a dummy.
tasks.register("dummy-keepanno-reference") {
  dependsOn(gradle.includedBuild("keepanno").task(":jar"))
}
val keepAnnoTask = tasks.getByName("dummy-keepanno-reference")
  .taskDependencies
  .getDependencies(tasks.getByName("dummy-keepanno-reference"))
  .iterator()
  .next()

tasks.register("dummy-r8-reference") {
  dependsOn(gradle.includedBuild("main").task(":jar"))
}
val r8Task = tasks.getByName("dummy-r8-reference")
  .taskDependencies
  .getDependencies(tasks.getByName("dummy-r8-reference"))
  .iterator()
  .next()

dependencies {
  implementation(keepAnnoTask.outputs.files)
  implementation(r8Task.outputs.files)
  implementation(Deps.asm)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.junit)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(files(root.resolveAll("third_party", "ddmlib", "ddmlib.jar")))
  implementation(
    files(
      root.resolveAll("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar")))
  implementation(files(root.resolveAll("third_party", "jasmin", "jasmin-2.4.jar")))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
  implementation(Deps.asmUtil)
}

tasks.withType<JavaCompile> {
  dependsOn(keepAnnoTask)
  dependsOn(r8Task)
  options.setFork(true)
  options.forkOptions.memoryMaximumSize = "3g"
  options.forkOptions.jvmArgs = listOf(
    "-Xss256m",
    // Set the bootclass path so compilation is consistent with 1.8 target compatibility.
    "-Xbootclasspath/a:third_party/openjdk/openjdk-rt-1.8/rt.jar")
}

tasks.withType<KotlinCompile> {
    dependsOn(keepAnnoTask)
    dependsOn(r8Task)
}
