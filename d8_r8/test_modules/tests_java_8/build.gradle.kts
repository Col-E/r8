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

// If we depend on keepanno by referencing the project source outputs we get an error regarding
// incompatible java class file version. By depending on the jar we circumvent that.
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileTask = projectTask("keepanno", "compileJava")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(projectTask("main", "jar").outputs.files)
  implementation(projectTask("resourceshrinker", "jar").outputs.files)
  implementation(projectTask("resourceshrinker", "depsJar").outputs.files)
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
  implementation(resolve(ThirdPartyDeps.ddmLib,"ddmlib.jar"))
  implementation(resolve(ThirdPartyDeps.jasmin,"jasmin-2.4.jar"))
  implementation(resolve(ThirdPartyDeps.jdwpTests,"apache-harmony-jdwp-tests-host.jar"))
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
    ThirdPartyDeps.aapt2,
    ThirdPartyDeps.artTests,
    ThirdPartyDeps.artTestsLegacy,
    ThirdPartyDeps.compilerApi,
    ThirdPartyDeps.coreLambdaStubs,
    ThirdPartyDeps.dagger,
    ThirdPartyDeps.desugarJdkLibs,
    ThirdPartyDeps.desugarJdkLibsLegacy,
    ThirdPartyDeps.desugarJdkLibs11,
    ThirdPartyDeps.iosched2019,
    ThirdPartyDeps.jacoco,
    ThirdPartyDeps.java8Runtime,
    ThirdPartyDeps.jdk11Test,
    ThirdPartyDeps.jsr223,
    ThirdPartyDeps.multidex,
    ThirdPartyDeps.r8,
    ThirdPartyDeps.r8Mappings,
    ThirdPartyDeps.r8v2_0_74,
    ThirdPartyDeps.r8v3_2_54,
    ThirdPartyDeps.retraceBenchmark,
    ThirdPartyDeps.retraceBinaryCompatibility,
    ThirdPartyDeps.rhino,
    ThirdPartyDeps.rhinoAndroid,
    ThirdPartyDeps.smali,
    ThirdPartyDeps.tivi)
    + ThirdPartyDeps.androidJars
    + ThirdPartyDeps.androidVMs
    + ThirdPartyDeps.desugarLibraryReleases
    + ThirdPartyDeps.jdks
    + ThirdPartyDeps.kotlinCompilers
    + ThirdPartyDeps.proguards)

val thirdPartyRuntimeInternalDependenciesTask = ensureThirdPartyDependencies(
  "runtimeInternalDeps",
  listOf(
    ThirdPartyDeps.clank,
    ThirdPartyDeps.framework,
    ThirdPartyDeps.nest,
    ThirdPartyDeps.proto,
    ThirdPartyDeps.protobufLite,
    ThirdPartyDeps.retraceInternal)
    + ThirdPartyDeps.internalIssues
    + ThirdPartyDeps.gmscoreVersions
)

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
    .filter({"$it".contains("keepanno") ||
             "$it".contains("resoourceshrinker") ||
            ("$it".contains("third_party")
              && !"$it".contains("errorprone")
              && !"$it".contains("gradle"))})
}

tasks {
  "compileTestJava" {
    dependsOn(thirdPartyCompileDependenciesTask)
  }
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
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
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
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
    if (!project.hasProperty("no_internal")) {
      dependsOn(thirdPartyRuntimeInternalDependenciesTask)
    }
    dependsOn(*sourceSetDependenciesTasks)
    environment.put("KEEP_ANNO_JAVAC_BUILD_DIR", keepAnnoCompileTask.outputs.files.getAsPath())
    // This path is set when compiling examples jar task in DependenciesPlugin.
    environment.put("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR",
                    getRoot().resolveAll("build", "test", "examplesJava11", "classes"))
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
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
