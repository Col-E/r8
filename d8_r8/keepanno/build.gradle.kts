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
}

dependencies {
  compileOnly(Deps.asm)
  compileOnly(Deps.guava)
}
