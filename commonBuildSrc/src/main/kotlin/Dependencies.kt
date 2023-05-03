// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.JavaVersion
import java.io.File

class DependenciesPlugin: Plugin<Project> {
  override fun apply(target: Project) {
    // Intentionally empty
  }
}

fun Project.getRoot() : File {
  var parent = this.projectDir
  while (!parent.getName().equals("d8_r8")) {
    parent = parent.getParentFile()
  }
  return parent.getParentFile()
}

fun File.resolveAll(vararg xs: String) : File {
  var that = this;
  for (x in xs) {
    that = that.resolve(x)
  }
  return that
}

object JvmCompatibility {
  val sourceCompatibility = JavaVersion.VERSION_11
  val targetCompatibility = JavaVersion.VERSION_11
}

object Versions {
  const val asmVersion = "9.5"
  const val fastUtilVersion = "7.2.0"
  const val gsonVersion = "2.7"
  const val guavaVersion = "31.1-jre"
  const val joptSimpleVersion = "4.6"
  const val junitVersion = "4.13-beta-2"
  const val kotlinVersion = "1.8.0"
  const val kotlinMetadataVersion = "0.6.0"
  const val smaliVersion = "3.0.3"
  const val errorproneVersion = "2.18.0"
}

object Deps {
  val asm by lazy { "org.ow2.asm:asm:${Versions.asmVersion}" }
  val asmUtil by lazy { "org.ow2.asm:asm-util:${Versions.asmVersion}" }
  val asmCommons by lazy { "org.ow2.asm:asm-commons:${Versions.asmVersion}" }
  val fastUtil by lazy { "it.unimi.dsi:fastutil:${Versions.fastUtilVersion}"}
  val gson by lazy { "com.google.code.gson:gson:${Versions.gsonVersion}"}
  val guava by lazy { "com.google.guava:guava:${Versions.guavaVersion}" }
  val joptSimple by lazy { "net.sf.jopt-simple:jopt-simple:${Versions.joptSimpleVersion}" }
  val junit by lazy { "junit:junit:${Versions.junitVersion}"}
  val kotlinMetadata by lazy {
    "org.jetbrains.kotlinx:kotlinx-metadata-jvm:${Versions.kotlinMetadataVersion}" }
  val kotlinStdLib by lazy { "org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlinVersion}" }
  val kotlinReflect by lazy { "org.jetbrains.kotlin:kotlin-reflect:${Versions.kotlinVersion}" }
  val smali by lazy { "com.android.tools.smali:smali:${Versions.smaliVersion}" }
  val errorprone by lazy { "com.google.errorprone:error_prone_core:${Versions.errorproneVersion}" }
}
