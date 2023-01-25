// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.kotlin

import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;

fun main() {
    val tempDirectory = Files.createTempDirectory("tempFile")
    val tempFile = tempDirectory.resolve("tempFile")
    Files.write(tempFile, "first ".toByteArray(StandardCharsets.UTF_8), StandardOpenOption.CREATE)
    Files.write(tempFile, "second".toByteArray(StandardCharsets.UTF_8), StandardOpenOption.APPEND)
    println("Content: " + Files.readAllLines(tempFile).get(0))
    println("Size: " + Files.getAttribute(tempFile, "basic:size"))
    println("Exists (before deletion): " + Files.exists(tempFile));
    Files.deleteIfExists(tempFile);
    println("Exists (after deletion): " + Files.exists(tempFile));
}
