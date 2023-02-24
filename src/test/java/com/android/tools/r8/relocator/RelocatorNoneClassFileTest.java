// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ZipUtils;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RelocatorNoneClassFileTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RelocatorNoneClassFileTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testRewritingFiles() throws IOException, CompilationFailedException {
    File testJar = temp.newFile("test.jar");
    Path testJarPath = testJar.toPath();
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(testJarPath, options))) {
      ZipUtils.writeToZipStream(
          out, "foo/bar/kotlin.kotlin_builtins", "foo.bar.BazImpl".getBytes(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out, "foo.bar.kotlin.kotlin_builtins", "foo.bar.BazImpl".getBytes(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(
          out,
          "somepackage/foo/bar/kotlin.kotlin_builtins",
          "foo.bar.BazImpl".getBytes(),
          ZipEntry.STORED);
    }
    Path relocatedJar = temp.newFile("out.jar").toPath();
    Relocator.run(
        RelocatorCommand.builder()
            .addProgramFile(testJarPath)
            .setOutputPath(relocatedJar)
            .addPackageMapping(
                Reference.packageFromString("foo.bar"), Reference.packageFromString("baz.qux"))
            .build());
    ZipFile zip = new ZipFile(relocatedJar.toFile());
    // We should have relocated foo/bar/kotlin.kotlin_builtins to baz/qux/kotlin.kotlin_builtins
    assertNull(zip.getEntry("foo/bar/kotlin.kotlin_builtins"));
    ZipEntry relocatedEntry = zip.getEntry("baz/qux/kotlin.kotlin_builtins");
    assertNotNull(relocatedEntry);
    // We should not change the contents of the files, even if it matches the package.
    InputStream inputStream = zip.getInputStream(relocatedEntry);
    Scanner scanner = new Scanner(inputStream);
    assertEquals("foo.bar.BazImpl", scanner.next());
    // We should (for now at least) not rewrite files if not in a package.
    assertNotNull(zip.getEntry("foo.bar.kotlin.kotlin_builtins"));
    assertNotNull(zip.getEntry("somepackage/foo/bar/kotlin.kotlin_builtins"));
  }
}
