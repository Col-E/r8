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

val testsJava8Jar = projectTask("tests_java_8", "testJar")
val keepAnnoJarTask = projectTask("keepanno", "jar")
val keepAnnoCompileTask = projectTask("keepanno", "compileJava")
val mainR8RelocatedTask = projectTask("main", "r8WithRelocatedDeps")

dependencies {
  implementation(keepAnnoJarTask.outputs.files)
  implementation(files(testsJava8Jar.outputs.files.getSingleFile()))
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
  withType<JavaCompile> {
    dependsOn(testsJava8Jar)
    dependsOn(gradle.includedBuild("main").task(":jar"))
  }

  withType<KotlinCompile> {
    kotlinOptions {
      enabled = false
    }
  }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(mainR8RelocatedTask)
    systemProperty("TEST_DATA_LOCATION",
                   layout.buildDirectory.dir("classes/java/test").get().toString())
    systemProperty("KEEP_ANNO_JAVAC_BUILD_DIR", keepAnnoCompileTask.outputs.files.getAsPath())
    systemProperty("R8_WITH_RELOCATED_DEPS", mainR8RelocatedTask.outputs.files.singleFile)
    systemProperty("R8_RUNTIME_PATH", mainR8RelocatedTask.outputs.files.singleFile)
  }

  val testJar by registering(Jar::class) {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
    archiveFileName.set("not_named_tests_bootstrap.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(gradle.includedBuild("keepanno").task(":jar"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
    }
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}
