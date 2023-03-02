// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
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
  implementation(Deps.asm)
  implementation(Deps.asmUtil)
  implementation(Deps.asmCommons)
  implementation(Deps.fastUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.joptSimple)
  implementation(Deps.kotlinMetadata)
}

tasks.withType<JavaCompile> {
  println("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)
}
