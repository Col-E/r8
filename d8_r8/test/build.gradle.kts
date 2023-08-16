// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths
import org.gradle.api.logging.LogLevel.ERROR
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform


plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

dependencies { }

val r8WithRelocatedDepsTask = projectTask("main", "r8WithRelocatedDeps")
val java8TestJarTask = projectTask("tests_java_8", "testJar")
val java8DepsJarTask = projectTask("tests_java_8", "depsJar")

tasks {
  withType<JavaCompile> {
    options.setFork(true)
    options.forkOptions.executable = getCompilerPath(Jdk.JDK_17)
    options.forkOptions.javaHome = getJavaHome(Jdk.JDK_17)
  }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }

  val allTestsJar by registering(Jar::class) {
    dependsOn(java8TestJarTask)
    from(java8TestJarTask.outputs.getFiles().map(::zipTree))
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    archiveFileName.set("all-tests.jar")
  }

  val allDepsJar by registering(Jar::class) {
    dependsOn(java8DepsJarTask)
    from(java8DepsJarTask.outputs.getFiles().map(::zipTree))
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    archiveFileName.set("all-deps.jar")
  }

  val allTestsJarRelocated by registering(Exec::class) {
    dependsOn(r8WithRelocatedDepsTask)
    dependsOn(allTestsJar)
    val r8 = r8WithRelocatedDepsTask.outputs.getFiles().getSingleFile()
    val allTests = allTestsJar.get().outputs.files.getSingleFile()
    inputs.files(listOf(r8, allTests))
    val output = file(Paths.get("build", "libs", "all-tests-relocated.jar"))
    outputs.file(output)
    commandLine = baseCompilerCommandLine(
      r8,
      "relocator",
      listOf("--input",
             "$allTests",
             "--output",
             "$output",
             "--map",
             "kotlinx.metadata->com.android.tools.r8.jetbrains.kotlinx.metadata"))
  }

  withType<Test> {
    println("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    println("NOTE: Max parallel forks " + maxParallelForks)
    val os = DefaultNativePlatform.getCurrentOperatingSystem()
    if (os.isMacOsX) {
      logger.lifecycle(
        "WARNING: Testing in only partially supported on Mac OS. \n" +
          "Art only runs on Linux and tests requiring Art runs in a Docker container, which must " +
          "be present. See tools/docker/README.md for details.")
    } else if (os.isWindows) {
      logger.lifecycle(
        "WARNING: Testing in only partially supported on Windows. Art only runs on Linux and " +
          "tests requiring Art will be skipped")
    } else if (!os.isLinux) {
      logger.log(
        LogLevel.ERROR,
        "Testing in not supported on your platform. Testing is only fully supported on " +
          "Linux and partially supported on Mac OS and Windows. Art does not run on other " +
          "platforms.")
    }
    dependsOn(gradle.includedBuild("tests_java_8").task(":test"))
  }
}