// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

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
  implementation(":resourceshrinker")
  compileOnly(Deps.asm)
  compileOnly(Deps.asmCommons)
  compileOnly(Deps.asmUtil)
  compileOnly(Deps.fastUtil)
  compileOnly(Deps.gson)
  compileOnly(Deps.guava)
  compileOnly(Deps.kotlinMetadata)
  errorprone(Deps.errorprone)
}

val keepAnnoJarTask = projectTask("keepanno", "jar")
val resourceShrinkerJarTask = projectTask("resourceshrinker", "jar")
val resourceShrinkerDepsTask = projectTask("resourceshrinker", "depsJar")

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
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  }

  val consolidatedLicense by registering {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    val root = getRoot()
    val r8License = root.resolve("LICENSE")
    val libraryLicense = root.resolve("LIBRARY-LICENSE")
    val libraryLicenseFiles = fileTree(root.resolve("library-licensing"))
    inputs.files(
      listOf(r8License, libraryLicense),
      libraryLicenseFiles,
      mainJarDependencies().map(::zipTree))

    val license = rootProject.layout.buildDirectory.file("generatedLicense/LICENSE").get().asFile
    outputs.files(license)

    doLast {
      val dependencies = mutableListOf<String>()
      configurations
        .findByName("runtimeClasspath")!!
        .resolvedConfiguration
        .resolvedArtifacts
        .forEach {
          val identifier = it.id.componentIdentifier
          if (identifier is ModuleComponentIdentifier) {
            dependencies.add("${identifier.group}:${identifier.module}")
          }
      }
      val libraryLicenses = libraryLicense.readText()
      dependencies.forEach {
        if (!libraryLicenses.contains("- artifact: $it")) {
          throw GradleException("No license for $it in LIBRARY_LICENSE")
        }
      }
      license.getParentFile().mkdirs()
      license.createNewFile()
      license.writeText(buildString {
        append("This file lists all licenses for code distributed.\n")
        .append("All non-library code has the following 3-Clause BSD license.\n")
        .append("\n")
        .append("\n")
        .append(r8License.readText())
        .append("\n")
        .append("\n")
        .append("Summary of distributed libraries:\n")
        .append("\n")
        .append(libraryLicenses)
        .append("\n")
        .append("\n")
        .append("Licenses details:\n")
        libraryLicenseFiles.sorted().forEach { file ->
          append("\n").append("\n").append(file.readText())
        }
      })
    }
  }

  val swissArmyKnife by registering(Jar::class) {
    dependsOn(keepAnnoJarTask)
    dependsOn(resourceShrinkerJarTask)
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
    from(keepAnnoJarTask.outputs.files.map(::zipTree))
    from(resourceShrinkerJarTask.outputs.files.map(::zipTree))
    from(consolidatedLicense)
    manifest {
      attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife"
    }
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    archiveFileName.set("r8-swissarmyknife.jar")
  }

  val depsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    dependsOn(resourceShrinkerDepsTask)
    from(mainJarDependencies().map(::zipTree))
    from(resourceShrinkerDepsTask.outputs.files.map(::zipTree))
    exclude("**/module-info.class")
    exclude("**/*.kotlin_metadata")
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/com.android.tools/**")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
    exclude("META-INF/services/kotlin.reflect.**")
    exclude("**/*.xml")
    exclude("com/android/version.properties")
    exclude("NOTICE")
    exclude("README.md")
    exclude("javax/annotation/**")
    exclude("wireless/**")
    manifest {}
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }

  val r8WithRelocatedDeps by registering(Exec::class) {
    dependsOn(swissArmyKnife)
    dependsOn(depsJar)
    val swissArmy = swissArmyKnife.get().outputs.getFiles().getSingleFile()
    val deps = depsJar.get().outputs.files.getSingleFile()
    inputs.files(listOf(swissArmy, deps))
    val output = getRoot().resolveAll("build", "libs", "r8.jar")
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
             // Add identity mapping to enforce no relocation of things already in package
             // com.android.tools.r8.
             "--map",
             "com.android.tools.r8.**->com.android.tools.r8",
             "--map",
             "com.android.**->com.android.tools.r8.com.android",
             "--map",
             "com.android.build.shrinker.**->com.android.tools.r8.resourceshrinker",
             "--map",
             "com.google.common.**->com.android.tools.r8.com.google.common",
             "--map",
             "com.google.gson.**->com.android.tools.r8.com.google.gson",
             "--map",
             "com.google.thirdparty.**->com.android.tools.r8.com.google.thirdparty",
             "--map",
             "org.objectweb.asm.**->com.android.tools.r8.org.objectweb.asm",
             "--map",
             "it.unimi.dsi.fastutil.**->com.android.tools.r8.it.unimi.dsi.fastutil",
             "--map",
             "kotlin.**->com.android.tools.r8.jetbrains.kotlin",
             "--map",
             "kotlinx.**->com.android.tools.r8.jetbrains.kotlinx",
             "--map",
             "org.jetbrains.**->com.android.tools.r8.org.jetbrains",
             "--map",
             "org.intellij.**->com.android.tools.r8.org.intellij",
             "--map",
             "org.checkerframework.**->com.android.tools.r8.org.checkerframework",
             "--map",
             "com.google.j2objc.**->com.android.tools.r8.com.google.j2objc",
             "--map",
             "com.google.protobuf.**->com.android.tools.r8.com.google.protobuf",
             "--map",
             "android.aapt.**->com.android.tools.r8.android.aaapt"
      ))
  }
}

tasks.withType<KotlinCompile> {
  enabled = false
}

tasks.withType<JavaCompile> {
  dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
  println("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)

  // Enable error prone for D8/R8 main sources.
  options.errorprone.isEnabled.set(true)

  // Make all warnings errors. Warnings that we have chosen not to fix (or suppress) are disabled
  // outright below.
  options.compilerArgs.add("-Werror")

  // Increase number of reported errors to 1000 (default is 100).
  options.compilerArgs.add("-Xmaxerrs")
  options.compilerArgs.add("1000")

  // Non-default / Experimental checks - explicitly enforced.
  options.errorprone.error("RemoveUnusedImports")
  options.errorprone.error("InconsistentOverloads")
  options.errorprone.error("MissingDefault")
  options.errorprone.error("MultipleTopLevelClasses")
  options.errorprone.error("NarrowingCompoundAssignment")

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
  options.errorprone.disable("IdentityHashMapUsage")
}
