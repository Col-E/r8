// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "keepanno", "java"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  withSourcesJar()
}

dependencies {
  compileOnly(Deps.asm)
  compileOnly(Deps.guava)
}

tasks {
  val keepAnnoAnnotationsJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations.jar")
  }

  val keepAnnoJar by registering(Jar::class) {
    dependsOn(gradle.includedBuild("shared").task(":downloadDeps"))
    from(sourceSets.main.get().output)
  }
}
