// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.records;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JavaCompilerTool;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
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
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

/**
 * Records are compiled using: third_party/openjdk/jdk-15/linux/bin/javac --release 15
 * --enable-preview path/to/file.java
 */
public class RecordTestUtils {

  private static final String EXAMPLE_FOLDER = "examplesJava16";
  private static final String RECORD_FOLDER = "records";

  public static Path jar() {
    return Paths.get(ToolHelper.TESTS_BUILD_DIR, EXAMPLE_FOLDER, RECORD_FOLDER + ".jar");
  }

  // TODO(b/169645628): Consider if that keep rule should be required or not.
  public static final String RECORD_KEEP_RULE =
      "-keepattributes *\n" + "-keep class * extends java.lang.Record { private final <fields>; }";

  public static Path[] getJdk15LibraryFiles(TemporaryFolder temp) throws IOException {
    Assume.assumeFalse(ToolHelper.isWindows());
    // TODO(b/169645628): Add JDK-15 runtime jar instead. As a temporary solution we use the jdk 8
    // runtime with additional stubs.
    // We use jdk-8 for compilation because in jdk-9 and higher we would need to deal with the
    // module patching logic.
    Path recordStubs =
        JavaCompilerTool.create(getCheckedInJdk8(), temp)
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "Record.java"))
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "ObjectMethods.java"))
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "StringConcatFactory.java"))
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "TypeDescriptor.java"))
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "RecordComponent.java"))
            .compile();
    return new Path[] {recordStubs, ToolHelper.getJava8RuntimeJar()};
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

  public static void assertRecordsAreRecords(CodeInspector inspector) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (clazz.getDexProgramClass().superType.toString().equals("java.lang.Record")) {
        assertTrue(clazz.getDexProgramClass().isRecord());
      }
    }
  }

  public static void assertNoJavaLangRecord(CodeInspector inspector) {
    assertThat(inspector.clazz("java.lang.Record"), isAbsent());
  }
}
