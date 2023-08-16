// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
}

java {
  sourceSets.main.configure {
    kotlin.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
    java.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
}

fun jarDependencies() : FileCollection {
  return sourceSets
    .main
    .get()
    .compileClasspath
    .filter({ "$it".contains("third_party")
              && "$it".contains("dependencies")
    })
}

val thirdPartyCompileDependenciesTask = ensureThirdPartyDependencies(
  "compileDeps",
  listOf(ThirdPartyDeps.r8))

dependencies {
  compileOnly(Deps.asm)
  compileOnly(Deps.guava)
  compileOnly(files(getRoot().resolve(ThirdPartyDeps.r8.path).resolve("r8lib_8.2.20-dev.jar")))
  implementation("com.android.tools.build:aapt2-proto:8.2.0-alpha10-10154469")
  implementation("com.google.protobuf:protobuf-java:3.19.3")
  implementation("com.android.tools.layoutlib:layoutlib-api:31.2.0-alpha10")
  implementation("com.android.tools:common:31.2.0-alpha10")
  implementation("com.android.tools:sdk-common:31.2.0-alpha10")
}

tasks {
  withType<KotlinCompile> {
    dependsOn(thirdPartyCompileDependenciesTask)
    kotlinOptions {
      // We cannot use languageVersion.set(JavaLanguageVersion.of(8)) because gradle cannot figure
      // out that the jdk is 1_8 and will try to download it.
      jvmTarget = "11"
    }
  }

  val depsJar by registering(Jar::class) {
    println(header("Resource shrinker dependencies"))
    jarDependencies().forEach({ println(it) })
    from(jarDependencies().map(::zipTree))
    exclude("**/*.proto")
    exclude("versions-offline/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("resourceshrinker_deps.jar")
  }
}
