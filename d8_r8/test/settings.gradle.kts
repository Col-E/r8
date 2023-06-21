// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

rootProject.name = "r8-tests"

val root = rootProject.projectDir.parentFile
includeBuild(root.resolve("main"))
includeBuild(root.resolve("test_modules").resolve("tests_java_8"))
