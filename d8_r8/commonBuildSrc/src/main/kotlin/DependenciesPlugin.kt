// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.net.URI
import java.nio.file.Paths
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.register
import org.gradle.nativeplatform.platform.OperatingSystem
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

class DependenciesPlugin: Plugin<Project> {

  override fun apply(target: Project) {
    val dependenciesPath = "file:" +
      target.getRoot().resolve("third_party").resolve("dependencies").getAbsolutePath()
    val dependenciesNewPath = "file:" +
      target.getRoot().resolve("third_party").resolve("dependencies_new").getAbsolutePath()
    val repositories = target.getRepositories()
    repositories.maven { name = "LOCAL_MAVEN_REPO";  url = URI(dependenciesPath) }
    repositories.maven { name = "LOCAL_MAVEN_REPO_NEW";  url = URI(dependenciesNewPath) }
  }
}

enum class Jdk(val folder : String) {
  JDK_8("jdk8"),
  JDK_9("openjdk-9.0.4"),
  JDK_11("jdk-11"),
  JDK_17("jdk-17"),
  JDK_20("jdk-20");

  fun isJdk8() : Boolean {
    return this == JDK_8
  }

  fun getThirdPartyDependency() : ThirdPartyDependency {
    val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
    val subFolder : String
    val fileName : String
    if (os.isLinux) {
      subFolder = if(isJdk8()) "linux-x86" else "linux"
      fileName = "java"
    } else if (os.isMacOsX) {
      subFolder = if(isJdk8()) "darwin-x86" else "osx"
      fileName = "java"
    } else {
      assert(os.isWindows())
      if (isJdk8()) {
        throw RuntimeException("No Jdk8 on Windows")
      }
      subFolder = "windows"
      fileName = "java.bat"
    }
    return ThirdPartyDependency(
      name,
      Paths.get("third_party", "openjdk", folder, subFolder, "bin", fileName).toFile(),
      Paths.get("third_party", "openjdk", folder, "$subFolder.tar.gz.sha1").toFile())
  }
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

fun Project.ensureThirdPartyDependencies(name : String, deps : List<ThirdPartyDependency>) : Task {
  val outputFiles : MutableList<File> = mutableListOf()
  val depsTasks = deps.map({
      tasks.register<DownloadDependencyTask>("download-third-party-${it.packageName}") {
        setDependency(
          it.packageName,
          getRoot().resolve(it.sha1File),
          getRoot().resolve(it.path).parentFile,
          it.type)
        outputFiles.add(it.path)
      }})
  return tasks.register("ensure-third-party-$name") {
    dependsOn(depsTasks)
    outputs.files(outputFiles)
  }.get()
}

/**
 * Builds a jar for each subfolder in an examples test source set.
 *
 * <p> As an example, src/test/examplesJava9 contains subfolders: backport, collectionof, ..., .
 * These are compiled to individual jars and placed in <repo-root>/build/test/examplesJava9/ as:
 * backport.jar, collectionof.jar, ..., .
 *
 * Calling this from a project will amend the task graph with the task named
 * getExamplesJarsTaskName(examplesName) such that it can be referenced from the test runners.
 */
fun Project.buildJavaExamplesJars(examplesName : String) : Task {
  val outputFiles : MutableList<File> = mutableListOf()
  val jarTasks : MutableList<Task> = mutableListOf()
  var testSourceSet = extensions
    .getByType(JavaPluginExtension::class.java)
    .sourceSets
    // The TEST_SOURCE_SET_NAME is the source set defined by writing java { sourcesets.test { ... }}
    .getByName(SourceSet.TEST_SOURCE_SET_NAME)
  testSourceSet
    .java
    .sourceDirectories
    .files
    .forEach { srcDir ->
      srcDir.listFiles(File::isDirectory)?.forEach { exampleDir ->
        jarTasks.add(tasks.register<Jar>("jar-examples$examplesName-${exampleDir.name}") {
          dependsOn("compileTestJava")
          archiveFileName.set("${exampleDir.name}.jar")
          destinationDirectory.set(getRoot().resolveAll("build", "test", "examples$examplesName"))
          from(testSourceSet.output.classesDirs.files.map{ it.resolve(exampleDir.name) }) {
            include("**/*.class")
          }
        }.get())
      }
    }
  return tasks.register(getExamplesJarsTaskName(examplesName)) {
    dependsOn(jarTasks)
    outputs.files(outputFiles)
  }.get()
}

fun Project.getExamplesJarsTaskName(name: String) : String {
  return "build-example-jars-$name"
}

fun Project.resolve(thirdPartyDependency: ThirdPartyDependency) : ConfigurableFileCollection {
  return files(project.getRoot().resolve(thirdPartyDependency.path))
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
  jar: File, deps: File, compiler: String, args: List<String> = listOf(),
) : List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar:$deps",
    "com.android.tools.r8.SwissArmyKnife",
    compiler) + args
}

fun Project.baseCompilerCommandLine(
  jar: File, compiler: String, args: List<String> = listOf(),
) : List<String> {
  // Execute r8 commands against a stable r8 with dependencies.
  // TODO(b/139725780): See if we can remove or lower the heap size (-Xmx8g).
  return listOf(
    getJavaPath(Jdk.JDK_17),
    "-Xmx8g",
    "-ea",
    "-cp",
    "$jar",
    "com.android.tools.r8.SwissArmyKnife",
    compiler) + args
}

fun Project.createR8LibCommandLine(
  r8Compiler: File,
  input: File,
  output: File,
  pgConf: List<File>,
  excludingDepsVariant: Boolean,
  lib: List<File> = listOf(),
  classpath: List<File> = listOf()
) : List<String> {
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
  const val errorproneVersion = "2.18.0"
  const val fastUtilVersion = "7.2.1"
  const val gsonVersion = "2.7"
  const val guavaVersion = "31.1-jre"
  const val javassist = "3.29.2-GA"
  const val junitVersion = "4.13-beta-2"
  const val kotlinVersion = "1.8.10"
  const val kotlinMetadataVersion = "0.6.2"
  const val mockito = "2.10.0"
  const val smaliVersion = "3.0.3"
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
  val mockito by lazy { "org.mockito:mockito-core:${Versions.mockito}" }
  val smali by lazy { "com.android.tools.smali:smali:${Versions.smaliVersion}" }
  val errorprone by lazy { "com.google.errorprone:error_prone_core:${Versions.errorproneVersion}" }
}

object ThirdPartyDeps {
  val apiDatabase = ThirdPartyDependency(
    "apiDatabase",
    Paths.get(
      "third_party",
      "api_database",
      "api_database",
      "resources",
      "new_api_database.ser").toFile(),
    Paths.get("third_party", "api_database", "api_database.tar.gz.sha1").toFile())
  val ddmLib = ThirdPartyDependency(
    "ddmlib",
    Paths.get("third_party", "ddmlib", "ddmlib.jar").toFile(),
    Paths.get("third_party", "ddmlib.tar.gz.sha1").toFile())
  val jasmin = ThirdPartyDependency(
    "jasmin",
    Paths.get("third_party", "jasmin", "jasmin-2.4.jar").toFile(),
    Paths.get("third_party", "jasmin.tar.gz.sha1").toFile())
  val jdwpTests = ThirdPartyDependency(
    "jdwp-tests",
    Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar").toFile(),
    Paths.get("third_party", "jdwp-tests.tar.gz.sha1").toFile())
  val androidJars : List<ThirdPartyDependency> = getThirdPartyAndroidJars()
  val java8Runtime = ThirdPartyDependency(
    "openjdk-rt-1.8",
    Paths.get("third_party", "openjdk", "openjdk-rt-1.8", "rt.jar").toFile(),
    Paths.get("third_party", "openjdk", "openjdk-rt-1.8.tar.gz.sha1").toFile()
  )
  val androidVMs : List<ThirdPartyDependency> = getThirdPartyAndroidVms()
  val jdks : List<ThirdPartyDependency> = getJdks()
}

fun getThirdPartyAndroidJars() : List<ThirdPartyDependency> {
  return listOf(
    "libcore_latest",
    "lib-master",
    "lib-v14",
    "lib-v15",
    "lib-v19",
    "lib-v21",
    "lib-v22",
    "lib-v23",
    "lib-v24",
    "lib-v25",
    "lib-v26",
    "lib-v27",
    "lib-v28",
    "lib-v29",
    "lib-v30",
    "lib-v31",
    "lib-v32",
    "lib-v33",
    "lib-v34"
  ).map(::getThirdPartyAndroidJar)
}

fun getThirdPartyAndroidJar(version : String) : ThirdPartyDependency {
  return ThirdPartyDependency(
    version,
    Paths.get("third_party", "android_jar", version, "android.jar").toFile(),
    Paths.get("third_party", "android_jar", "$version.tar.gz.sha1").toFile())
}

fun getThirdPartyAndroidVms() : List<ThirdPartyDependency> {
  return listOf(
    listOf("host", "art-master"),
    listOf("host", "art-14.0.0-dp1"),
    listOf("host", "art-13.0.0"),
    listOf("host", "art-12.0.0-beta4"),
    listOf("art-10.0.0"),
    listOf("art-5.1.1"),
    listOf("art-6.0.1"),
    listOf("art-7.0.0"),
    listOf("art-8.1.0"),
    listOf("art-9.0.0"),
    listOf("art"),
    listOf("dalvik-4.0.4"),
    listOf("dalvik")).map(::getThirdPartyAndroidVm)
}

fun getThirdPartyAndroidVm(version : List<String>) : ThirdPartyDependency {
  val output = Paths.get("tools", "linux", *version.toTypedArray(), "bin", "art").toFile()
  return ThirdPartyDependency(
    version.last(),
    output,
    Paths.get(
      "tools",
      "linux",
      *version.slice(0..version.size - 2).toTypedArray(),
      "${version.last()}.tar.gz.sha1").toFile())
}

fun getJdks() : List<ThirdPartyDependency> {
  val os: OperatingSystem = DefaultNativePlatform.getCurrentOperatingSystem()
  if (os.isLinux || os.isMacOsX) {
    return Jdk.values().map{ it.getThirdPartyDependency()}
  } else {
    return Jdk.values().filter{ !it.isJdk8() }.map{ it.getThirdPartyDependency()}
  }
}