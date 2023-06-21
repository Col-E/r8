// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URI

class DependenciesPlugin: Plugin<Project> {

  override fun apply(target: Project) {
    val dependenciesPath = "file:" +
      "${target.getRoot().resolve("third_party").resolve("dependencies").getAbsolutePath()}"
    val dependenciesNewPath = "file:" +
      "${target.getRoot().resolve("third_party").resolve("dependencies_new").getAbsolutePath()}"
    val repositories = target.getRepositories()
    repositories.maven { name = "LOCAL_MAVEN_REPO";  url = URI(dependenciesPath) }
    repositories.maven { name = "LOCAL_MAVEN_REPO_NEW";  url = URI(dependenciesNewPath) }
  }
}

enum class Jdk(val folder : String) {
  JDK_11("jdk-11"),
  JDK_17("jdk-17"),
  JDK_20("jdk-20");
}

fun Project.getRoot() : File {
  var parent = this.projectDir
  while (!parent.getName().equals("d8_r8")) {
    parent = parent.getParentFile()
  }
  return parent.getParentFile()
}

fun Project.header(title : String) : String {
  return "****** ${title} ******"
}

/**
 * When using composite builds, referecing tasks in other projects do not give a Task but a
 * TaskReference. To get outputs from other tasks we need to have a proper task and gradle do not
 * provide a way of getting a Task from a TaskReference. We use a trick where create a synthetic
 * task that depends on the task of interest, allowing us to look at the graph and obtain the
 * actual reference. Remove this code if gradle starts supporting this natively.
 */
fun Project.projectTask(project : String, taskName : String) : Task {
  val name = "$project-reference-$taskName";
  val task = tasks.register(name) {
    dependsOn(gradle.includedBuild(project).task(":$taskName"))
  }.get();
  return task.taskDependencies
    .getDependencies(tasks.getByName(name)).iterator().next();
}

fun File.resolveAll(vararg xs: String) : File {
  var that = this;
  for (x in xs) {
    that = that.resolve(x)
  }
  return that
}

fun Project.getJavaHome(jdk : Jdk) : File {
  // TODO(b/270105162): Make sure this works on other platforms.
  return getRoot().resolveAll("third_party", "openjdk", jdk.folder, "linux")
}

fun Project.getCompilerPath(jdk : Jdk) : String {
  // TODO(b/270105162): Make sure this works on other platforms.
  return getJavaHome(jdk).resolveAll("bin", "javac").toString()
}

fun Project.getJavaPath(jdk : Jdk) : String {
  // TODO(b/270105162): Make sure this works on other platforms.
  return getJavaHome(jdk).resolveAll("bin", "java").toString()
}

fun Project.baseCompilerCommandLine(
  jar : File, deps : File, compiler : String, args : List<String> = listOf()) : List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    "${getJavaPath(Jdk.JDK_17)}",
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar:$deps",
    "com.android.tools.r8.SwissArmyKnife",
    compiler) + args
}

fun Project.baseCompilerCommandLine(
  jar : File, compiler : String, args : List<String> = listOf()) : List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    "${getJavaPath(Jdk.JDK_17)}",
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar",
    "com.android.tools.r8.SwissArmyKnife",
    compiler) + args
}

fun Project.createR8LibCommandLine(
  r8Compiler : File,
  input : File,
  output: File,
  pgConf : List<File>,
  excludingDepsVariant : Boolean,
  lib : List<File> = listOf(),
  classpath : List<File> = listOf(),
  args : List<String> = listOf()) : List<String> {
  val pgList = pgConf.flatMap({ listOf("--pg-conf", "$it") })
  val libList = lib.flatMap({ listOf("--lib", "$it") })
  val cpList = classpath.flatMap({ listOf("--classpath", "$it") })
  val exclList = if (excludingDepsVariant) listOf("--excldeps-variant") else listOf()
  return listOf(
    "python3",
    "${getRoot().resolve("tools").resolve("create_r8lib.py")}",
    "--r8compiler",
    "${r8Compiler}",
    "--r8jar",
    "${input}",
    "--output",
    "${output}",
  ) + exclList + pgList + libList + cpList
}

object JvmCompatibility {
  val sourceCompatibility = JavaVersion.VERSION_11
  val targetCompatibility = JavaVersion.VERSION_11
}

object Versions {
  const val asmVersion = "9.5"
  const val fastUtilVersion = "7.2.1"
  const val gsonVersion = "2.7"
  const val guavaVersion = "31.1-jre"
  const val junitVersion = "4.13-beta-2"
  const val kotlinVersion = "1.8.10"
  const val kotlinMetadataVersion = "0.6.2"
  const val smaliVersion = "3.0.3"
  const val errorproneVersion = "2.18.0"
  const val javassist = "3.29.2-GA"
}

object Deps {
  val asm by lazy { "org.ow2.asm:asm:${Versions.asmVersion}" }
  val asmUtil by lazy { "org.ow2.asm:asm-util:${Versions.asmVersion}" }
  val asmCommons by lazy { "org.ow2.asm:asm-commons:${Versions.asmVersion}" }
  val fastUtil by lazy { "it.unimi.dsi:fastutil:${Versions.fastUtilVersion}"}
  val gson by lazy { "com.google.code.gson:gson:${Versions.gsonVersion}"}
  val guava by lazy { "com.google.guava:guava:${Versions.guavaVersion}" }
  val javassist by lazy { "org.javassist:javassist:${Versions.javassist}"}
  val junit by lazy { "junit:junit:${Versions.junitVersion}"}
  val kotlinMetadata by lazy {
    "org.jetbrains.kotlinx:kotlinx-metadata-jvm:${Versions.kotlinMetadataVersion}" }
  val kotlinStdLib by lazy { "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}" }
  val kotlinReflect by lazy { "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}" }
  val smali by lazy { "com.android.tools.smali:smali:${Versions.smaliVersion}" }
  val errorprone by lazy { "com.google.errorprone:error_prone_core:${Versions.errorproneVersion}" }
}
