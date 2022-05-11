// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

public class JavaExampleClassProxy {

  private final String examplesFolder;
  private final String binaryName;

  public JavaExampleClassProxy(String examples, String binaryName) {
    this.examplesFolder = examples;
    this.binaryName = binaryName;
  }

  public static Path examplesJar(String examplesFolder) {
    return Paths.get(ToolHelper.BUILD_DIR, "test", examplesFolder + ".jar");
  }

  public byte[] bytes() {
    Path examplePath = examplesJar(examplesFolder);
    if (!Files.exists(examplePath)) {
      throw new RuntimeException(
          "Could not find path "
              + examplePath
              + ". Build "
              + examplesFolder
              + " by running tools/gradle.py build"
              + StringUtils.capitalize(examplesFolder));
    }
    try (ZipFile zipFile = new ZipFile(examplePath.toFile())) {
      return ByteStreams.toByteArray(
          zipFile.getInputStream(zipFile.getEntry(binaryName + ".class")));
    } catch (IOException e) {
      throw new RuntimeException("Could not read zip-entry from " + examplePath.toString(), e);
    }
  }

  public String typeName() {
    return DescriptorUtils.getJavaTypeFromBinaryName(binaryName);
  }

  public ClassReference getClassReference() {
    return Reference.classFromBinaryName(binaryName);
  }
}
