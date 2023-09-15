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

val sourceSetDependenciesTasks = arrayOf(
  projectTask("tests_java_9", getExampleJarsTaskName("examplesJava9")),
  projectTask("tests_java_10", getExampleJarsTaskName("examplesJava10")),
  projectTask("tests_java_11", getExampleJarsTaskName("examplesJava11")),
  projectTask("tests_java_17", getExampleJarsTaskName("examplesJava17")),
  projectTask("tests_java_20", getExampleJarsTaskName("examplesJava20")),
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
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  }
  withType<JavaCompile> {
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    dependsOn(gradle.includedBuild("main").task(":compileJava"))
    dependsOn(gradle.includedBuild("main").task(":processResources"))
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  }

  withType<KotlinCompile> {
    enabled = false
  }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(mainDepsJarTask)
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
    }
    dependsOn(*sourceSetDependenciesTasks)
    systemProperty("TEST_DATA_LOCATION",
                   layout.buildDirectory.dir("classes/java/test").get().toString())
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
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets. Renaming
    //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_tests_java_8.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    dependsOn(gradle.includedBuild("resourceshrinker").task(":jar"))
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
