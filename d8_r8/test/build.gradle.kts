// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

val root = getRoot();

java {
  sourceSets.test.configure {
    java.srcDir(root.resolveAll("src", "test", "java"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
}

// We cannot use languageVersion.set(JavaLanguageVersion.of("8")) because gradle cannot figure
// out that the jdk is 1_8 and will try to download it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
  kotlinOptions {
    jvmTarget = "11"
  }
}


dependencies {
  implementation(":r8")
  implementation(":keepanno")
  implementation(Deps.asm)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.junit)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(files(root.resolveAll("third_party", "ddmlib", "ddmlib.jar")))
  implementation(
    files(
      root.resolveAll("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar")))
  implementation(files(root.resolveAll("third_party", "jasmin", "jasmin-2.4.jar")))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
  implementation(Deps.asmUtil)
}

tasks.named("test") {
  dependsOn(gradle.includedBuild("tests_java_8").task(":compileJava"))
}

tasks.withType<Test> {
  environment("USE_NEW_GRADLE_SETUP", "true")
}
