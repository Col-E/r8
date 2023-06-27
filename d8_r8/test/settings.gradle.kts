// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

rootProject.name = "r8-tests"

val root = rootProject.projectDir.parentFile
includeBuild(root.resolve("main"))
includeBuild(root.resolve("test_modules").resolve("tests_java_8"))
includeBuild(root.resolve("test_modules").resolve("tests_java_9"))
includeBuild(root.resolve("test_modules").resolve("tests_java_10"))
includeBuild(root.resolve("test_modules").resolve("tests_java_11"))
includeBuild(root.resolve("test_modules").resolve("tests_java_17"))
includeBuild(root.resolve("test_modules").resolve("tests_java_20"))
includeBuild(root.resolve("test_modules").resolve("tests_java_examples"))
includeBuild(root.resolve("test_modules").resolve("tests_java_examplesAndroidN"))
includeBuild(root.resolve("test_modules").resolve("tests_java_examplesAndroidP"))
includeBuild(root.resolve("test_modules").resolve("tests_java_examplesAndroidO"))
includeBuild(root.resolve("test_modules").resolve("tests_java_kotlinR8TestResources"))
