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
      srcDir(root.resolveAll("src", "test", "java"))
    }
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
val mainCompileTask = projectTask("main", "compileJava")
val mainDepsJarTask = projectTask("main", "depsJar")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(mainCompileTask.outputs.files)
  implementation(projectTask("main", "processResources").outputs.files)
  implementation(projectTask("resourceshrinker", "compileJava").outputs.files)
  implementation(projectTask("resourceshrinker", "compileKotlin").outputs.files)
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
  implementation(Deps.smaliUtil)
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
    ThirdPartyDeps.examplesAndroidOLegacy,
    ThirdPartyDeps.gson,
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
    .filter {
        "$it".contains("third_party")
          && !"$it".contains("errorprone")
          && !"$it".contains("third_party/gradle")
    }
}

tasks {
  "compileTestJava" {
    dependsOn(thirdPartyCompileDependenciesTask)
  }
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    dependsOn(gradle.includedBuild("main").task(":compileJava"))
    dependsOn(gradle.includedBuild("main").task(":processResources"))
    dependsOn(thirdPartyCompileDependenciesTask)
  }

  withType<KotlinCompile> {
    enabled = false
  }

  withType<Test> {
    TestingState.setUpTestingState(this)
    environment.put("USE_NEW_GRADLE_SETUP", "true")
    environment.put("TEST_CLASSES_LOCATIONS", "$buildDir/classes/java/test")
    dependsOn(mainDepsJarTask)
    dependsOn(thirdPartyRuntimeDependenciesTask)
    if (!project.hasProperty("no_internal")) {
      dependsOn(thirdPartyRuntimeInternalDependenciesTask)
    }
    dependsOn(*sourceSetDependenciesTasks)
    systemProperty("KEEP_ANNO_JAVAC_BUILD_DIR", keepAnnoCompileTask.outputs.files.getAsPath())
    // This path is set when compiling examples jar task in DependenciesPlugin.
    systemProperty("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR",
                    getRoot().resolveAll("build", "test", "examplesJava11", "classes"))
    systemProperty(
      "R8_RUNTIME_PATH",
      mainCompileTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator + mainDepsJarTask.outputs.files.singleFile)
    systemProperty(
      "RETRACE_RUNTIME_PATH",
      mainCompileTask.outputs.files.getAsPath().split(File.pathSeparator)[0] +
        File.pathSeparator + mainDepsJarTask.outputs.files.singleFile)
    systemProperty("R8_DEPS", mainDepsJarTask.outputs.files.singleFile)

    // TODO(b/291198792): Remove this exclusion when desugared library runs correctly.
    exclude("com/android/tools/r8/desugar/desugaredlibrary/**")
    exclude("com/android/tools/r8/desugar/InvokeSuperToRewrittenDefaultMethodTest**")
    exclude("com/android/tools/r8/desugar/InvokeSuperToEmulatedDefaultMethodTest**")
    exclude("com/android/tools/r8/desugar/backports/ThreadLocalBackportWithDesugaredLibraryTest**")
    exclude("com/android/tools/r8/L8CommandTest**")
    exclude("com/android/tools/r8/MarkersTest**")
    exclude("com/android/tools/r8/apimodel/ApiModelDesugaredLibraryReferenceTest**")
    exclude("com/android/tools/r8/apimodel/ApiModelNoDesugaredLibraryReferenceTest**")
    exclude("com/android/tools/r8/benchmarks/desugaredlib/**")
    exclude("com/android/tools/r8/classmerging/vertical/ForceInlineConstructorWithRetargetedLibMemberTest**")
    exclude("com/android/tools/r8/classmerging/vertical/ForceInlineConstructorWithRetargetedLibMemberTest**")
    exclude("com/android/tools/r8/ir/optimize/inliner/InlineMethodWithRetargetedLibMemberTest**")
    exclude("com/android/tools/r8/profile/art/DesugaredLibraryArtProfileRewritingTest**")
    exclude("com/android/tools/r8/profile/art/dump/DumpArtProfileProvidersTest**")
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets. Renaming
    //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_tests_java_8.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    dependsOn(thirdPartyCompileDependenciesTask)
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
