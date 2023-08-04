// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
}

dependencies { }

val r8WithRelocatedDepsTask = projectTask("main", "r8WithRelocatedDeps")
val r8Jar = projectTask("main", "jar")
val depsJarTask = projectTask("main", "depsJar")
val allTestsJarRelocatedTask = projectTask("test", "allTestsJarRelocated")
val allDepsJarTask = projectTask("test", "allDepsJar")

tasks {
  withType<Exec> {
    doFirst {
      println("Executing command: ${commandLine.joinToString(" ")}")
    }
  }

  val generateKeepRules by registering(Exec::class) {
    dependsOn(r8WithRelocatedDepsTask)
    dependsOn(depsJarTask)
    dependsOn(allTestsJarRelocatedTask)
    dependsOn(allDepsJarTask)
    val r8 = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val deps = depsJarTask.outputs.files.getSingleFile()
    val tests = allTestsJarRelocatedTask.outputs.files.getSingleFile()
    val testDeps = allDepsJarTask.outputs.files.getSingleFile()
    inputs.files(listOf(r8, deps, tests, testDeps))
    val output = file(Paths.get("build", "libs", "generated-keep-rules.txt"))
    outputs.file(output)
    commandLine = baseCompilerCommandLine(
      r8,
      "tracereferences",
      listOf(
        "--keep-rules",
        "--allowobfuscation",
        "--lib",
        "${getRoot().resolveAll("third_party", "openjdk", "openjdk-rt-1.8", "rt.jar")}",
        "--lib",
        "${deps}",
        "--lib",
        "$testDeps",
        "--target",
        "$r8",
        "--source",
        "$tests",
        "--output",
        "$output"))
  }

  val r8LibWithRelocatedDeps by registering(Exec::class) {
    dependsOn(generateKeepRules)
    dependsOn(r8WithRelocatedDepsTask)
    val r8 = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val generatedKeepRules = generateKeepRules.get().outputs.files.getSingleFile()
    val keepTxt = getRoot().resolveAll("src", "main", "keep.txt")
    // TODO(b/294351878): Remove once enum issue is fixed
    val keepResourceShrinkerTxt = getRoot().resolveAll("src", "main", "keep_r8resourceshrinker.txt")
    inputs.files(listOf(r8, generatedKeepRules, keepTxt, keepResourceShrinkerTxt))
    val output = file(Paths.get("build", "libs", "r8lib-deps-relocated.jar"))
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8,
      r8,
      output,
      listOf(keepTxt, generatedKeepRules, keepResourceShrinkerTxt),
      false)
  }

  val r8LibNoDeps by registering(Exec::class) {
    dependsOn(depsJarTask)
    dependsOn(r8WithRelocatedDepsTask)
    val r8Compiler = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val r8Jar = r8Jar.outputs.files.getSingleFile()
    val deps = depsJarTask.outputs.files.getSingleFile()
    inputs.files(listOf(r8Compiler, r8Jar, deps))
    val output = file(Paths.get("build", "libs", "r8lib-no-deps.jar"))
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8Compiler,
      r8Jar,
      output,
      listOf(getRoot().resolveAll("src", "main", "keep.txt")),
      true,
      listOf(deps))
  }
}
