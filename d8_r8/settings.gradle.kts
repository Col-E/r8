// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// TODO(b/270105162): Move this file out the repository root when old gradle is removed.

pluginManagement {
  repositories {
    maven {
      url = uri("file:../third_party/dependencies")
    }
    maven {
      url = uri("file:../third_party/dependencies_new")
    }
  }
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

rootProject.name = "d8-r8"

// Bootstrap building by downloading dependencies.

fun String.execute() =
    org.codehaus.groovy.runtime.ProcessGroovyMethods.execute(this)

fun Process.out() =
    String(
        this.getInputStream().readAllBytes(),
        java.nio.charset.StandardCharsets.UTF_8)
fun Process.err() =
    String(
        this.getErrorStream().readAllBytes(),
        java.nio.charset.StandardCharsets.UTF_8)

val dependencies_bucket = "r8-deps"
val dependencies_sha1_file = "third_party/dependencies.tar.gz.sha1"
var cmd =
        ("download_from_google_storage.py --extract"
                + " --bucket ${dependencies_bucket}"
                + " --sha1_file ${dependencies_sha1_file}")
var process = cmd.execute()
process.waitFor()
if (process.exitValue() != 0) {
    throw GradleException(
            "Bootstrapping dependencies download failed:"
            + "\n${process.err()}\n${process.out()}")
}
val dependencies_new_sha1_file = "third_party/dependencies_new.tar.gz.sha1"
cmd =
        ("download_from_google_storage.py --extract"
                + " --bucket ${dependencies_bucket}"
                + " --sha1_file ${dependencies_new_sha1_file}")
process = cmd.execute()
process.waitFor()
if (process.exitValue() != 0) {
    throw GradleException(
            "Bootstrapping dependencies_new download failed:"
            + "\n${process.err()}\n${process.out()}")
}

val root = rootProject.projectDir

// This project is temporarily located in d8_r8. When moved to root, the parent
// folder should just be removed.
includeBuild(root.parentFile.resolve("commonBuildSrc"))
includeBuild(root.resolve("keepanno"))

// We need to include src/main as a composite-build otherwise our test-modules
// will compete with the test to compile the source files.
includeBuild(root.resolve("main"))
includeBuild(root.resolve("test"))

// Include r8lib as standalone to have a nice separation between source artifacts and r8 compiled
// artifacts
includeBuild(root.resolve("r8lib"))