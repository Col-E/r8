// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Records are compiled using: third_party/openjdk/jdk-15/linux/bin/javac --release 15
 * --enable-preview path/to/file.java
 */
public class RecordTestUtils {

  private static final String EXAMPLE_FOLDER = "examplesJava15";
  private static final String RECORD_FOLDER = "records";

  public static Path jar() {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, EXAMPLE_FOLDER, RECORD_FOLDER + ".jar");
  }

  public static byte[][] getProgramData(String mainClassSimpleName) {
    byte[][] bytes = classDataFromPrefix(RECORD_FOLDER + "/" + mainClassSimpleName);
    assert bytes.length > 0 : "Did not find any program data for " + mainClassSimpleName;
    return bytes;
  }

  public static String getMainType(String mainClassSimpleName) {
    return RECORD_FOLDER + "." + mainClassSimpleName;
  }

  private static byte[][] classDataFromPrefix(String prefix) {
    Path examplePath = jar();
    if (!Files.exists(examplePath)) {
      throw new RuntimeException(
          "Could not find path "
              + examplePath
              + ". Build "
              + EXAMPLE_FOLDER
              + " by running tools/gradle.py build"
              + StringUtils.capitalize(EXAMPLE_FOLDER));
    }
    List<byte[]> result = new ArrayList<>();
    try (ZipFile zipFile = new ZipFile(examplePath.toFile())) {
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry zipEntry = entries.nextElement();
        if (zipEntry.getName().startsWith(prefix)) {
          result.add(ByteStreams.toByteArray(zipFile.getInputStream(zipEntry)));
        }
      }
    } catch (IOException e) {
      throw new RuntimeException("Could not read zip-entry from " + examplePath.toString(), e);
    }
    if (result.isEmpty()) {
      throw new RuntimeException("Did not find any class with prefix " + prefix);
    }
    return result.toArray(new byte[0][0]);
  }
}
