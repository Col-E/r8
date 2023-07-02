// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths
import net.ltgt.gradle.errorprone.errorprone

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("net.ltgt.errorprone") version "3.0.1"
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "main", "java"))
    resources.srcDirs(getRoot().resolveAll("third_party", "api_database", "api_database"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
}

dependencies {
  implementation(":keepanno")
  compileOnly(Deps.asm)
  compileOnly(Deps.asmCommons)
  compileOnly(Deps.asmUtil)
  compileOnly(Deps.fastUtil)
  compileOnly(Deps.gson)
  compileOnly(Deps.guava)
  compileOnly(Deps.kotlinMetadata)
  errorprone(Deps.errorprone)
}

val thirdPartyResourceDependenciesTask = ensureThirdPartyDependencies(
  "resourceDeps",
  listOf(ThirdPartyDeps.apiDatabase))

val keepAnnoJarTask = projectTask("keepanno", "jar")

fun mainJarDependencies() : FileCollection {
  return sourceSets
    .main
    .get()
    .compileClasspath
    .filter({ "$it".contains("third_party")
              && "$it".contains("dependencies")
              && !"$it".contains("errorprone")
    })
}

tasks {
  withType<Exec> {
    doFirst {
      println("Executing command: ${commandLine.joinToString(" ")}")
    }
  }

  withType<ProcessResources> {
    dependsOn(thirdPartyResourceDependenciesTask)
  }

  val swissArmyKnife by registering(Jar::class) {
    from(sourceSets.main.get().output)
    manifest {
      attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife"
    }
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    archiveFileName.set("r8-swissarmyknife.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(keepAnnoJarTask)
    doFirst {
      println(header("R8 full dependencies"))
    }
    mainJarDependencies().forEach({ println(it) })
    from(mainJarDependencies().map(::zipTree))
    from(keepAnnoJarTask.outputs.files.map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }

  val r8WithRelocatedDeps by registering(Exec::class) {
    dependsOn(swissArmyKnife)
    dependsOn(depsJar)
    val swissArmy = swissArmyKnife.get().outputs.getFiles().getSingleFile()
    val deps = depsJar.get().outputs.files.getSingleFile()
    inputs.files(listOf(swissArmy, deps))
    val output = file(Paths.get("build", "libs", "r8-deps-relocated.jar"))
    outputs.file(output)
    commandLine = baseCompilerCommandLine(
      swissArmy,
      deps,
      "relocator",
      listOf("--input",
             "$swissArmy",
             "--input",
             "$deps",
             "--output",
             "$output",
             "--map",
             "com.google.common->com.android.tools.r8.com.google.common",
             "--map",
             "com.google.gson->com.android.tools.r8.com.google.gson",
             "--map",
             "com.google.thirdparty->com.android.tools.r8.com.google.thirdparty",
             "--map",
             "org.objectweb.asm->com.android.tools.r8.org.objectweb.asm",
             "--map",
             "it.unimi.dsi.fastutil->com.android.tools.r8.it.unimi.dsi.fastutil",
             "--map",
             "kotlin->com.android.tools.r8.jetbrains.kotlin",
             "--map",
             "kotlinx->com.android.tools.r8.jetbrains.kotlinx",
             "--map",
             "org.jetbrains->com.android.tools.r8.org.jetbrains",
             "--map",
             "org.intellij->com.android.tools.r8.org.intellij",
             "--map",
             "org.checkerframework->com.android.tools.r8.org.checkerframework",
             "--map",
             "com.google.j2objc->com.android.tools.r8.com.google.j2objc"
      ))
  }
}

tasks.withType<JavaCompile> {
  println("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)

  // Enable error prone for D8/R8 main sources and make all warnings errors.
  // Warnings that we have chosen not to fix (or suppress) are disabled outright below.
  options.compilerArgs.add("-Werror")
  options.errorprone.isEnabled.set(true)

  // Non-default / Experimental checks - explicitly enforced.
  options.errorprone.error("RemoveUnusedImports")
  options.errorprone.error("InconsistentOverloads")
  options.errorprone.error("MissingDefault")
  options.errorprone.error("MultipleTopLevelClasses")
  options.errorprone.error("NarrowingCompoundAssignment")

  // TODO(b/270510095): These should likely be fixed/suppressed and become hard failures.
  options.errorprone.disable("UnusedVariable")
  options.errorprone.disable("EqualsUnsafeCast")
  options.errorprone.disable("TypeParameterUnusedInFormals")
  options.errorprone.disable("ImmutableEnumChecker")
  options.errorprone.disable("BadImport")
  options.errorprone.disable("ComplexBooleanConstant")
  options.errorprone.disable("StreamToIterable")
  options.errorprone.disable("HidingField")
  options.errorprone.disable("StreamResourceLeak")
  options.errorprone.disable("CatchAndPrintStackTrace")
  options.errorprone.disable("NonCanonicalType")
  options.errorprone.disable("UnusedNestedClass")
  options.errorprone.disable("AmbiguousMethodReference")
  options.errorprone.disable("InvalidParam")
  options.errorprone.disable("CharacterGetNumericValue")
  options.errorprone.disable("ModifyCollectionInEnhancedForLoop")
  options.errorprone.disable("EmptyCatch")
  options.errorprone.disable("ArgumentSelectionDefectChecker")
  options.errorprone.disable("ImmutableAnnotationChecker")
  options.errorprone.disable("ObjectToString")
  options.errorprone.disable("DoNotClaimAnnotations")
  options.errorprone.disable("AnnotateFormatMethod")

  // TODO(b/270537614): Remove finalize uses.
  options.errorprone.disable("Finalize")

  // The following warnings could/should be active but are hit by R8 now so silence them.
  options.errorprone.disable("EqualsGetClass")
  options.errorprone.disable("MixedMutabilityReturnType")
  options.errorprone.disable("UnnecessaryParentheses")
  options.errorprone.disable("DoNotCallSuggester")
  options.errorprone.disable("InlineMeSuggester")
  options.errorprone.disable("MutablePublicArray")
  options.errorprone.disable("DefaultCharset")
  options.errorprone.disable("InconsistentCapitalization")
  options.errorprone.disable("InlineFormatString")
  options.errorprone.disable("MissingImplementsComparable")

  // Warnings that cause unwanted edits (e.g., inability to write informative asserts).
  options.errorprone.disable("AlreadyChecked")

  // JavaDoc related warnings. Would be nice to resolve but of no real consequence.
  options.errorprone.disable("InvalidLink")
  options.errorprone.disable("InvalidBlockTag")
  options.errorprone.disable("InvalidInlineTag")
  options.errorprone.disable("EmptyBlockTag")
  options.errorprone.disable("MissingSummary")
  options.errorprone.disable("UnrecognisedJavadocTag")
  options.errorprone.disable("AlmostJavadoc")

  // Moving away from identity and canonical items is not planned.
  options.errorprone.disable("ReferenceEquality")
  options.errorprone.disable("IdentityHashMapUsage")
}
