// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// TODO(b/270105162): Move this file out the repository root when old gradle is removed.

rootProject.name = "d8-r8"

// Bootstrap building by downloading dependencies.
val dependencies_bucket = "r8-deps"
val root = rootProject.projectDir

fun getRepoRoot() : File {
  var current = root
  while (!current.getName().equals("d8_r8")) {
    current = current.getParentFile()
  }
  return current.getParentFile()
}

fun downloadFromGoogleStorage(sha1File : File) {
  val cmd = listOf(
    "download_from_google_storage.py",
    "--extract",
    "--bucket",
    dependencies_bucket,
    "--sha1_file",
    "${sha1File}"
  )
  println("Executing command: ${cmd.joinToString(" ")}")
  val process = ProcessBuilder().command(cmd).start()
  process.waitFor()
  if (process.exitValue() != 0) {
    throw GradleException(
      "Bootstrapping dependencies_new download failed:\n"
        + "${String(process.getErrorStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)}\n"
        + String(process.getInputStream().readAllBytes(),
                 java.nio.charset.StandardCharsets.UTF_8))
  }
}

val thirdParty = getRepoRoot().resolve("third_party")
downloadFromGoogleStorage(thirdParty.resolve("dependencies.tar.gz.sha1"))
downloadFromGoogleStorage(thirdParty.resolve("dependencies_new.tar.gz.sha1"))

pluginManagement {
  repositories {
    maven {
      url = uri("file:../third_party/dependencies")
    }
    maven {
      url = uri("file:../third_party/dependencies_new")
    }
  }
  includeBuild(rootProject.projectDir.resolve("commonBuildSrc"))
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("file:../third_party/dependencies")
    }
    maven {
      url = uri("file:../third_party/dependencies_new")
    }
  }
}

// This project is temporarily located in d8_r8. When moved to root, the parent
// folder should just be removed.
includeBuild(root.resolve("keepanno"))
includeBuild(root.resolve("resourceshrinker"))

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild(root.resolve("main"))
includeBuild(root.resolve("library_desugar"))
includeBuild(root.resolve("test"))

// Include r8lib as standalone to have a nice separation between source artifacts and r8 compiled
// artifacts
includeBuild(root.resolve("r8lib"))
