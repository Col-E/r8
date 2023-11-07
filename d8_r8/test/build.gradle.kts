// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile


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
val keepAnnoSourcesTask = projectTask("keepanno", "sourcesJar")
val mainDepsJarTask = projectTask("main", "depsJar")
val swissArmyKnifeTask = projectTask("main", "swissArmyKnife")
val r8WithRelocatedDepsTask = projectTask("main", "r8WithRelocatedDeps")
val mainSourcesTask = projectTask("main", "sourcesJar")
val resourceShrinkerSourcesTask = projectTask("resourceshrinker", "sourcesJar")
val java8TestJarTask = projectTask("tests_java_8", "testJar")
val java8TestsDepsJarTask = projectTask("tests_java_8", "depsJar")
val bootstrapTestsDepsJarTask = projectTask("tests_bootstrap", "depsJar")
val testsJava8SourceSetDependenciesTask = projectTask("tests_java_8", "sourceSetDependencyTask")

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

  val packageTests by registering(Jar::class) {
    dependsOn(java8TestJarTask)
    from(java8TestJarTask.outputs.files.map(::zipTree))
    exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("r8tests.jar")
  }

  val packageTestDeps by registering(Jar::class) {
    dependsOn(bootstrapTestsDepsJarTask, java8TestsDepsJarTask)
    from(bootstrapTestsDepsJarTask.outputs.getFiles().map(::zipTree))
    from(java8TestsDepsJarTask.outputs.getFiles().map(::zipTree))
    exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("test_deps_all.jar")
  }

  // When testing R8 lib with relocated deps we must relocate kotlinx.metadata in the tests, since
  // types from kotlinx.metadata are used on the R8 main/R8 test boundary.
  //
  // This is not needed when testing R8 lib excluding deps since we simply include the deps on the
  // classpath at runtime.
  val relocateTestsForR8LibWithRelocatedDeps by registering(Exec::class) {
    dependsOn(packageTests, r8WithRelocatedDepsTask)
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testJar = packageTests.getSingleOutputFile()
    inputs.files(r8WithRelocatedDepsJar, testJar)
    val outputJar = file(Paths.get("build", "libs", "r8tests-relocated.jar"))
    outputs.file(outputJar)
    commandLine = baseCompilerCommandLine(
      r8WithRelocatedDepsJar,
      "relocator",
      listOf("--input",
             "$testJar",
             "--output",
             "$outputJar",
             "--map",
             "kotlinx.metadata.**->com.android.tools.r8.jetbrains.kotlinx.metadata"))
  }

  fun Exec.generateKeepRulesForR8Lib(
          targetJarProvider: Task, testJarProvider: TaskProvider<*>, artifactName: String) {
    dependsOn(
            mainDepsJarTask,
            packageTestDeps,
            r8WithRelocatedDepsTask,
            targetJarProvider,
            testJarProvider)
    val mainDepsJar = mainDepsJarTask.getSingleOutputFile()
    val rtJar = resolve(ThirdPartyDeps.java8Runtime, "rt.jar").getSingleFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val targetJar = targetJarProvider.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    val testJar = testJarProvider.getSingleOutputFile()
    inputs.files(mainDepsJar, rtJar, r8WithRelocatedDepsJar, targetJar, testDepsJar, testJar)
    val output = file(Paths.get("build", "libs", artifactName))
    outputs.file(output)
    commandLine = baseCompilerCommandLine(
            r8WithRelocatedDepsJar,
            "tracereferences",
            listOf(
                    "--keep-rules",
                    "--allowobfuscation",
                    "--lib",
                    "$rtJar",
                    "--lib",
                    "$mainDepsJar",
                    "--lib",
                    "$testDepsJar",
                    "--target",
                    "$targetJar",
                    "--source",
                    "$testJar",
                    "--output",
                    "$output"))
  }

  val generateKeepRulesForR8LibWithRelocatedDeps by registering(Exec::class) {
    generateKeepRulesForR8Lib(
            r8WithRelocatedDepsTask,
            relocateTestsForR8LibWithRelocatedDeps,
            "generated-keep-rules-r8lib.txt")
  }

  val generateKeepRulesForR8LibNoDeps by registering(Exec::class) {
    generateKeepRulesForR8Lib(
            swissArmyKnifeTask,
            packageTests,
            "generated-keep-rules-r8lib-exclude-deps.txt")
  }

  fun Exec.assembleR8Lib(
    inputJarProvider: Task,
    generatedKeepRulesProvider: TaskProvider<Exec>,
    classpath: List<File>,
    artifactName: String) {
    dependsOn(generatedKeepRulesProvider, inputJarProvider, r8WithRelocatedDepsTask)
    val inputJar = inputJarProvider.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val keepRuleFiles = listOf(
            getRoot().resolveAll("src", "main", "keep.txt"),
            generatedKeepRulesProvider.getSingleOutputFile(),
            // TODO(b/294351878): Remove once enum issue is fixed
            getRoot().resolveAll("src", "main", "keep_r8resourceshrinker.txt"))
    inputs.files(listOf(r8WithRelocatedDepsJar, inputJar).union(keepRuleFiles).union(classpath))
    val outputJar = getRoot().resolveAll("build", "libs", artifactName)
    outputs.file(outputJar)
    commandLine = createR8LibCommandLine(
      r8WithRelocatedDepsJar,
      inputJar,
      outputJar,
      keepRuleFiles,
      excludingDepsVariant = classpath.isNotEmpty(),
      debugVariant = false,
      classpath = classpath)
  }

  val assembleR8LibNoDeps by registering(Exec::class) {
    dependsOn(mainDepsJarTask)
    val mainDepsJar = mainDepsJarTask.getSingleOutputFile()
    assembleR8Lib(
            swissArmyKnifeTask,
            generateKeepRulesForR8LibNoDeps,
            listOf(mainDepsJar),
            "r8lib-exclude-deps.jar")
  }

  val assembleR8LibWithRelocatedDeps by registering(Exec::class) {
    assembleR8Lib(
            r8WithRelocatedDepsTask,
            generateKeepRulesForR8LibWithRelocatedDeps,
            listOf(),
            "r8lib.jar")
  }

  val resourceshrinkercli by registering(Exec::class) {
    dependsOn(r8WithRelocatedDepsTask)
    val r8 = r8WithRelocatedDepsTask.getSingleOutputFile()
    val keepTxt = getRoot().resolveAll("src", "main", "resourceshrinker_cli.txt")
    val cliKeep = getRoot().resolveAll("src", "main", "keep_r8resourceshrinker.txt")
    inputs.files(keepTxt, cliKeep)
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

  fun Task.generateTestKeepRulesForR8Lib(
          r8LibJarProvider: TaskProvider<Exec>, artifactName: String) {
    dependsOn(r8LibJarProvider)
    val r8LibJar = r8LibJarProvider.getSingleOutputFile()
    inputs.files(r8LibJar)
    val output = rootProject.buildDir.resolveAll("libs", artifactName)
    outputs.files(output)
    doLast {
      // TODO(b/299065371): We should be able to take in the partition map output.
      output.writeText(
              """-keep class ** { *; }
-dontshrink
-dontoptimize
-keepattributes *
-applymapping $r8LibJar.map
""")
    }
  }

  val generateTestKeepRulesR8LibWithRelocatedDeps by registering {
    generateTestKeepRulesForR8Lib(assembleR8LibWithRelocatedDeps, "r8lib-tests-keep.txt")
  }

  val generateTestKeepRulesR8LibNoDeps by registering {
    generateTestKeepRulesForR8Lib(assembleR8LibNoDeps, "r8lib-exclude-deps-tests-keep.txt")
  }

  fun Exec.rewriteTestsForR8Lib(
          keepRulesFileProvider: TaskProvider<Task>,
          r8JarProvider: Task,
          testJarProvider: TaskProvider<*>,
          artifactName: String) {
    dependsOn(
            keepRulesFileProvider,
            packageTestDeps,
            relocateTestsForR8LibWithRelocatedDeps,
            r8JarProvider,
            r8WithRelocatedDepsTask,
            testJarProvider)
    val keepRulesFile = keepRulesFileProvider.getSingleOutputFile()
    val rtJar = resolve(ThirdPartyDeps.java8Runtime, "rt.jar").getSingleFile()
    val r8Jar = r8JarProvider.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    val testJar = testJarProvider.getSingleOutputFile()
    inputs.files(keepRulesFile, rtJar, r8Jar, r8WithRelocatedDepsJar, testDepsJar, testJar)
    val outputJar = getRoot().resolveAll("build", "libs", artifactName)
    outputs.file(outputJar)
    commandLine = baseCompilerCommandLine(
            r8WithRelocatedDepsJar,
            "r8",
            listOf(
                    "--classfile",
                    "--debug",
                    "--lib",
                    "$rtJar",
                    "--classpath",
                    "$r8Jar",
                    "--classpath",
                    "$testDepsJar",
                    "--output",
                    "$outputJar",
                    "--pg-conf",
                    "$keepRulesFile",
                    "$testJar"))
  }

  val rewriteTestsForR8LibWithRelocatedDeps by registering(Exec::class) {
    rewriteTestsForR8Lib(
            generateTestKeepRulesR8LibWithRelocatedDeps,
            r8WithRelocatedDepsTask,
            relocateTestsForR8LibWithRelocatedDeps,
            "r8libtestdeps-cf.jar")
  }

  val rewriteTestsForR8LibNoDeps by registering(Exec::class) {
    rewriteTestsForR8Lib(
            generateTestKeepRulesR8LibNoDeps,
            swissArmyKnifeTask,
            packageTests,
            "r8lib-exclude-deps-testdeps-cf.jar")
  }

  val cleanUnzipTests by registering(Delete::class) {
    dependsOn(packageTests)
    val outputDir = file("${buildDir}/unpacked/test")
    setDelete(outputDir)
  }

  val unzipTests by registering(Copy::class) {
    dependsOn(cleanUnzipTests, packageTests)
    val outputDir = file("${buildDir}/unpacked/test")
    from(zipTree(packageTests.getSingleOutputFile()))
    into(outputDir)
  }

  fun Copy.unzipRewrittenTestsForR8Lib(
          rewrittenTestJarProvider: TaskProvider<Exec>, outDirName: String) {
    dependsOn(rewrittenTestJarProvider)
    val outputDir = file("$buildDir/unpacked/$outDirName")
    val rewrittenTestJar = rewrittenTestJarProvider.getSingleOutputFile()
    from(zipTree(rewrittenTestJar))
    into(outputDir)
  }

  val cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps by registering(Delete::class) {
    val outputDir = file("${buildDir}/unpacked/rewrittentests-r8lib")
    setDelete(outputDir)
  }

  val unzipRewrittenTestsForR8LibWithRelocatedDeps by registering(Copy::class) {
    dependsOn(cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps)
    unzipRewrittenTestsForR8Lib(rewriteTestsForR8LibWithRelocatedDeps, "rewrittentests-r8lib")
  }

  val cleanUnzipRewrittenTestsForR8LibNoDeps by registering(Delete::class) {
    val outputDir = file("${buildDir}/unpacked/rewrittentests-r8lib-exclude-deps")
    setDelete(outputDir)
  }

  val unzipRewrittenTestsForR8LibNoDeps by registering(Copy::class) {
    dependsOn(cleanUnzipRewrittenTestsForR8LibNoDeps)
    unzipRewrittenTestsForR8Lib(
            rewriteTestsForR8LibNoDeps, "rewrittentests-r8lib-exclude-deps")
  }

  fun Test.testR8Lib(r8Lib: TaskProvider<Exec>, unzipRewrittenTests: TaskProvider<Copy>) {
    println("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    println("NOTE: Max parallel forks " + maxParallelForks)
    dependsOn(
            packageTestDeps,
            r8Lib,
            r8WithRelocatedDepsTask,
            testsJava8SourceSetDependenciesTask,
            unzipRewrittenTests,
            unzipTests,
            gradle.includedBuild("shared").task(":downloadDeps"))
    if (!project.hasProperty("no_internal")) {
      dependsOn(gradle.includedBuild("shared").task(":downloadDepsInternal"))
    }
    val r8LibJar = r8Lib.getSingleOutputFile()
    val r8LibMappingFile = file(r8LibJar.toString() + ".map")
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    configure(isR8Lib = true, r8Jar = r8WithRelocatedDepsJar, r8LibMappingFile = r8LibMappingFile)

    // R8lib should be used instead of the main output and all the tests in r8 should be mapped and
    // exists in r8LibTestPath.
    classpath = files(
            packageTestDeps.get().getOutputs().getFiles(),
            r8LibJar,
            unzipRewrittenTests.get().getOutputs().getFiles())
    testClassesDirs = unzipRewrittenTests.get().getOutputs().getFiles()
    systemProperty("TEST_DATA_LOCATION", unzipTests.getSingleOutputFile())
    systemProperty(
            "KEEP_ANNO_JAVAC_BUILD_DIR", keepAnnoCompileTask.getOutputs().getFiles().getAsPath())
    systemProperty("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR",
            getRoot().resolveAll("build", "test", "examplesJava11", "classes"))
    systemProperty("R8_RUNTIME_PATH", r8LibJar)
    systemProperty("RETRACE_RUNTIME_PATH", r8LibJar)
    systemProperty("R8_DEPS", mainDepsJarTask.getSingleOutputFile())
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")

    reports.junitXml.outputLocation.set(getRoot().resolveAll("build", "test-results", "test"))
    reports.html.outputLocation.set(getRoot().resolveAll("build", "reports", "tests", "test"))
  }

  val testR8LibWithRelocatedDeps by registering(Test::class) {
    testR8Lib(assembleR8LibWithRelocatedDeps, unzipRewrittenTestsForR8LibWithRelocatedDeps)
  }

  val testR8LibNoDeps by registering(Test::class) {
    testR8Lib(assembleR8LibNoDeps, unzipRewrittenTestsForR8LibNoDeps)
  }

  val packageSources by registering(Jar::class) {
    dependsOn(mainSourcesTask)
    dependsOn(resourceShrinkerSourcesTask)
    dependsOn(keepAnnoSourcesTask)
    from(mainSourcesTask.outputs.files.map(::zipTree))
    from(resourceShrinkerSourcesTask.outputs.files.map(::zipTree))
    from(keepAnnoSourcesTask.outputs.files.map(::zipTree))
    archiveClassifier.set("sources")
    archiveFileName.set("r8-src.jar")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
  }

  test {
    if (project.hasProperty("r8lib")) {
      dependsOn(testR8LibWithRelocatedDeps)
    } else if (project.hasProperty("r8lib_no_deps")) {
      dependsOn(testR8LibNoDeps)
    } else {
      dependsOn(gradle.includedBuild("tests_java_8").task(":test"))
      dependsOn(gradle.includedBuild("tests_bootstrap").task(":test"))
    }
  }
}

fun Task.getSingleOutputFile(): File = getOutputs().getSingleOutputFile()

fun TaskOutputs.getSingleOutputFile(): File = getFiles().getSingleFile()

fun TaskProvider<*>.getSingleOutputFile(): File = get().getSingleOutputFile()