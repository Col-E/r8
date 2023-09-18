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

val keepAnnoCompileTask = projectTask("keepanno", "compileJava")
val mainDepsJarTask = projectTask("main", "depsJar")
val swissArmyKnifeTask = projectTask("main", "swissArmyKnife")
val r8WithRelocatedDepsTask = projectTask("main", "r8WithRelocatedDeps")
val java8TestJarTask = projectTask("tests_java_8", "testJar")
val java8TestsDepsJarTask = projectTask("tests_java_8", "depsJar")
val bootstrapTestsDepsJarTask = projectTask("tests_bootstrap", "depsJar")

tasks {
  withType<Exec> {
    doFirst {
      println("Executing command: ${commandLine.joinToString(" ")}")
    }
  }

  withType<KotlinCompile> {
    kotlinOptions {
      jvmTarget = "17"
    }
  }

  val allTestsJar by registering(Jar::class) {
    dependsOn(java8TestJarTask)
    from(java8TestJarTask.outputs.files.map(::zipTree))
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    archiveFileName.set("all-tests.jar")
  }

  val allDepsJar by registering(Jar::class) {
    dependsOn(java8TestsDepsJarTask)
    dependsOn(bootstrapTestsDepsJarTask)
    from(java8TestsDepsJarTask.outputs.getFiles().map(::zipTree))
    from(bootstrapTestsDepsJarTask.outputs.getFiles().map(::zipTree))
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
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
             "kotlinx.metadata.**->com.android.tools.r8.jetbrains.kotlinx.metadata"))
  }

  val r8LibNoDeps by registering(Exec::class) {
    dependsOn(mainDepsJarTask)
    dependsOn(r8WithRelocatedDepsTask)
    val r8Compiler = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val r8Jar = swissArmyKnifeTask.outputs.files.getSingleFile()
    val deps = mainDepsJarTask.outputs.files.getSingleFile()
    inputs.files(listOf(r8Compiler, r8Jar, deps))
    val output = getRoot().resolveAll("build", "libs", "r8lib-exclude-deps.jar")
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8Compiler,
      r8Jar,
      output,
      listOf(getRoot().resolveAll("src", "main", "keep.txt")),
      true,
      false,
      listOf(deps))
  }

  val retraceNoDeps by registering(Exec::class) {
    dependsOn(r8LibNoDeps)
    val r8Compiler = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val r8Jar = r8LibNoDeps.get().outputs.files.getSingleFile()
    val deps = mainDepsJarTask.outputs.files.getSingleFile()
    inputs.files(listOf(r8Compiler, r8Jar, deps))
    val inputMap = file(r8Jar.toString() + ".map")
    val output = getRoot().resolveAll("build", "libs", "r8retrace-exclude-deps.jar")
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8Compiler,
      r8Jar,
      output,
      listOf(getRoot().resolveAll("src", "main", "keep_retrace.txt")),
      true,
      true,
      listOf(deps),
      listOf(),
      inputMap)
  }

  val generateKeepRules by registering(Exec::class) {
    dependsOn(r8WithRelocatedDepsTask)
    dependsOn(mainDepsJarTask)
    dependsOn(allTestsJarRelocated)
    dependsOn(allDepsJar)
    val r8 = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val deps = mainDepsJarTask.outputs.files.getSingleFile()
    val tests = allTestsJarRelocated.get().outputs.files.getSingleFile()
    val testDeps = allDepsJar.get().outputs.files.getSingleFile()
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
    val output = getRoot().resolveAll("build", "libs", "r8lib.jar")
    outputs.files(output)
    commandLine = createR8LibCommandLine(
      r8,
      r8,
      output,
      listOf(keepTxt, generatedKeepRules, keepResourceShrinkerTxt),
      false,
      false)
  }

  val retraceWithRelocatedDeps by registering(Exec::class) {
    dependsOn(r8LibWithRelocatedDeps)
    val r8Compiler = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val r8Jar = r8LibWithRelocatedDeps.get().outputs.files.getSingleFile()
    val deps = mainDepsJarTask.outputs.files.getSingleFile()
    inputs.files(listOf(r8Compiler, r8Jar, deps))
    val inputMap = file(r8Jar.toString() + ".map")
    val output = getRoot().resolveAll("build", "libs", "r8retrace.jar")
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8Compiler,
      r8Jar,
      output,
      listOf(getRoot().resolveAll("src", "main", "keep_retrace.txt")),
      false,
      true,
      listOf(),
      listOf(),
      inputMap)
  }

  val resourceshrinkercli by registering(Exec::class) {
    dependsOn(r8WithRelocatedDepsTask)
    val r8 = r8WithRelocatedDepsTask.outputs.files.getSingleFile()
    val keepTxt = getRoot().resolveAll("src", "main", "resourceshrinker_cli.txt")
    val cliKeep = getRoot().resolveAll("src", "main", "keep_r8resourceshrinker.txt")
    inputs.files(listOf(keepTxt, cliKeep))
    val output = file(Paths.get("build", "libs", "resourceshrinkercli.jar"))
    outputs.file(output)
    commandLine = createR8LibCommandLine(
      r8,
      r8,
      output,
      listOf(keepTxt, cliKeep),
      false,
      false)
  }

  val allTestWithApplyMappingProguardConfiguration by registering {
    dependsOn(r8LibWithRelocatedDeps)
    val license = rootProject.buildDir.resolveAll("libs", "r8tests-keep.txt")
    outputs.files(license)
    doLast {
      // TODO(b/299065371): We should be able to take in the partition map output.
      license.writeText(
        """-keep class ** { *; }
-dontshrink
-dontoptimize
-keepattributes *
-applymapping ${r8LibWithRelocatedDeps.get().outputs.files.singleFile}.map
""")
    }
  }

  val allTestsWithApplyMapping by registering(Exec::class) {
    dependsOn(allDepsJar)
    dependsOn(allTestsJarRelocated)
    dependsOn(r8WithRelocatedDepsTask)
    dependsOn(allTestWithApplyMappingProguardConfiguration)
    // dependsOn(thirdPartyRuntimeDependenciesTask)
    val r8 = r8WithRelocatedDepsTask.outputs.files.singleFile
    val allTests = allTestsJarRelocated.get().outputs.files.singleFile
    val pgConf = allTestWithApplyMappingProguardConfiguration.get().outputs.files.singleFile
    val lib = resolve(ThirdPartyDeps.java8Runtime, "rt.jar").getSingleFile()
    val main = r8WithRelocatedDepsTask.outputs.files.singleFile
    val testDeps = allDepsJar.get().outputs.files.singleFile
    inputs.files(listOf(r8, allTests, pgConf, lib, main, testDeps))
    val output = file(Paths.get("build", "libs", "all-tests-relocated-applymapping.jar"))
    outputs.file(output)
    commandLine = baseCompilerCommandLine(
      r8,
      "r8",
      listOf(
        "--classfile",
        "--debug",
        "--lib",
        "$lib",
        "--classpath",
        "$main",
        "--classpath",
        "$testDeps",
        "--output",
        "$output",
        "--pg-conf",
        "$pgConf",
        "$allTests"))
  }

  val unzipTests by registering(Copy::class) {
    dependsOn(allTestsJar)
    val outputDir = file("${buildDir}/unpacked/test")
    from(zipTree(allTestsJar.get().outputs.files.singleFile))
    into(outputDir)
  }

  val unzipRewrittenTests by registering(Copy::class) {
    dependsOn(allTestsWithApplyMapping)
    val outputDir = file("${buildDir}/unpacked/rewrittentest")
    from(zipTree(allTestsWithApplyMapping.get().outputs.files.singleFile))
    into(outputDir)
  }

  val r8LibTest by registering(Test::class) {
    println("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    println("NOTE: Max parallel forks " + maxParallelForks)
    dependsOn(allDepsJar)
    dependsOn(r8LibWithRelocatedDeps)
    dependsOn(unzipTests)
    dependsOn(unzipRewrittenTests)
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
    }
    val r8LibJar = r8LibWithRelocatedDeps.get().outputs.files.singleFile
    this.configure(isR8Lib = true, r8Jar = r8LibJar)

    // R8lib should be used instead of the main output and all the tests in r8 should be mapped and
    // exists in r8LibTestPath.
    classpath = files(
      allDepsJar.get().outputs.files,
      r8LibJar,
      unzipRewrittenTests.get().outputs.files)
    testClassesDirs = unzipRewrittenTests.get().outputs.files
    systemProperty("TEST_DATA_LOCATION", unzipTests.get().outputs.files.singleFile)
    systemProperty("KEEP_ANNO_JAVAC_BUILD_DIR", keepAnnoCompileTask.outputs.files.getAsPath())
    systemProperty("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR",
                   getRoot().resolveAll("build", "test", "examplesJava11", "classes"))
    systemProperty("R8_RUNTIME_PATH", r8LibJar)
    // TODO(b/270105162): This should change if running with retrace lib/r8lib.
    systemProperty("RETRACE_RUNTIME_PATH", r8LibJar)
    systemProperty("R8_DEPS", mainDepsJarTask.outputs.files.singleFile)
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
  }

  test {
    if (project.hasProperty("r8lib")) {
      dependsOn(r8LibTest)
    } else {
      dependsOn(gradle.includedBuild("tests_java_8").task(":test"))
      dependsOn(gradle.includedBuild("tests_bootstrap").task(":test"))
    }
  }
}
