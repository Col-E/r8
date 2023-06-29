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
    java.srcDir(root.resolveAll("src", "test", "java"))
  }
  // We are using a new JDK to compile to an older language version, which is not directly
  // compatible with java toolchains.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
  // If we depend on keepanno by referencing the project source outputs we get an error regarding
  // incompatible java class file version. By depending on the jar we circumvent that.
  implementation(projectTask("keepanno", "jar").outputs.files)
  implementation(projectTask("main", "jar").outputs.files)
  implementation(Deps.asm)
  implementation(Deps.asmCommons)
  implementation(Deps.asmUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.javassist)
  implementation(Deps.junit)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(resolve(ThirdPartyDeps.ddmLib))
  implementation(resolve(ThirdPartyDeps.jasmin))
  implementation(resolve(ThirdPartyDeps.jdwpTests))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
}

val thirdPartyCompileDependenciesTask = ensureThirdPartyDependencies(
  "compileDeps",
  listOf(
    ThirdPartyDeps.apiDatabase,
    ThirdPartyDeps.ddmLib,
    ThirdPartyDeps.jasmin,
    ThirdPartyDeps.jdwpTests))

val thirdPartyRuntimeDependenciesTask = ensureThirdPartyDependencies(
  "runtimeDeps",
  listOf(
    ThirdPartyDeps.compilerApi,
    ThirdPartyDeps.jacoco,
    ThirdPartyDeps.java8Runtime,
    ThirdPartyDeps.jdk11Test)
    + ThirdPartyDeps.androidJars
    + ThirdPartyDeps.androidVMs
    + ThirdPartyDeps.jdks
    + ThirdPartyDeps.kotlinCompilers
    + ThirdPartyDeps.proguards)

val sourceSetDependenciesTasks = arrayOf(
  projectTask("tests_java_examples", getExampleJarsTaskName("examples")),
  projectTask("tests_java_9", getExampleJarsTaskName("examplesJava9")),
  projectTask("tests_java_10", getExampleJarsTaskName("examplesJava10")),
  projectTask("tests_java_11", getExampleJarsTaskName("examplesJava11")),
  projectTask("tests_java_17", getExampleJarsTaskName("examplesJava17")),
  projectTask("tests_java_20", getExampleJarsTaskName("examplesJava20")),
  projectTask("tests_java_examplesAndroidN", getExampleJarsTaskName("examplesAndroidN")),
  projectTask("tests_java_examplesAndroidO", getExampleJarsTaskName("examplesAndroidO")),
  projectTask("tests_java_examplesAndroidP", getExampleJarsTaskName("examplesAndroidP")),
  projectTask("tests_java_kotlinR8TestResources", getExampleJarsTaskName("kotlinR8TestResources")),
)

fun testDependencies() : FileCollection {
  return sourceSets
    .test
    .get()
    .compileClasspath
    .filter({ "$it".contains("keepanno") ||
      ("$it".contains("third_party")
      && !"$it".contains("errorprone")
      && !"$it".contains("gradle"))
            })
}

tasks {
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("main").task(":jar"))
    dependsOn(thirdPartyCompileDependenciesTask)
    options.setFork(true)
    options.forkOptions.memoryMaximumSize = "3g"
    options.forkOptions.jvmArgs = listOf(
      "-Xss256m",
      // Set the bootclass path so compilation is consistent with 1.8 target compatibility.
      "-Xbootclasspath/a:third_party/openjdk/openjdk-rt-1.8/rt.jar")
  }

  withType<KotlinCompile> {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("main").task(":jar"))
    dependsOn(thirdPartyCompileDependenciesTask)
    kotlinOptions {
      // We are using a new JDK to compile to an older language version, which is not directly
      // compatible with java toolchains.
      jvmTarget = "1.8"
    }
  }

  withType<Test> {
    environment.put("USE_NEW_GRADLE_SETUP", "true")
    dependsOn(thirdPartyRuntimeDependenciesTask)
    dependsOn(*sourceSetDependenciesTasks)
    println("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    val userDefinedCoresPerFork = System.getenv("R8_GRADLE_CORES_PER_FORK")
    val processors = Runtime.getRuntime().availableProcessors()
    // See https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html.
    if (!userDefinedCoresPerFork.isNullOrEmpty()) {
      maxParallelForks = processors / userDefinedCoresPerFork.toInt()
    } else {
      // On normal work machines this seems to give the best test execution time (without freezing)
      maxParallelForks = processors / 3
      // On low cpu count machines (bots) we under subscribe, so increase the count.
      if (processors == 8) {
        maxParallelForks = 3
      }
    }
    println("NOTE: Max parallel forks " + maxParallelForks)
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(thirdPartyCompileDependenciesTask)
    doFirst {
      println(header("Test Java 8 dependencies"))
    }
    testDependencies().forEach({ println(it) })
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
