// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.relocator.foo.bar.Baz;
import com.android.tools.r8.relocator.foo.bar.BazImpl;
import com.android.tools.r8.relocator.foo.baz.OtherImpl;
import com.android.tools.r8.utils.StringUtils;
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
public class RelocatorServiceLoaderTest extends TestBase {

  private static final String SERVICE_FILE = "META-INF/services/foo.bar.Baz";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RelocatorServiceLoaderTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testRewritingOfServicesForNotFoundClasses()
      throws IOException, CompilationFailedException {
    File testJar = temp.newFile("test.jar");
    Path testJarPath = testJar.toPath();
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(testJarPath, options))) {
      ZipUtils.writeToZipStream(
          out,
          SERVICE_FILE,
          StringUtils.lines("foo.bar.BazImpl", "foo.baz.OtherImpl").getBytes(),
          ZipEntry.STORED);
    }
    ZipFile zip = new ZipFile(testJar);
    assertNotNull(zip.getEntry(SERVICE_FILE));
    Path relocatedJar = temp.newFile("out.jar").toPath();
    Relocator.run(
        RelocatorCommand.builder()
            .addProgramFile(testJarPath)
            .setOutputPath(relocatedJar)
            .addPackageMapping(
                Reference.packageFromString("foo.bar"), Reference.packageFromString("baz.qux"))
            .build());
    zip = new ZipFile(relocatedJar.toFile());
    ZipEntry serviceEntry = zip.getEntry("META-INF/services/baz.qux.Baz");
    assertNotNull(serviceEntry);
    InputStream inputStream = zip.getInputStream(serviceEntry);
    Scanner scanner = new Scanner(inputStream);
    assertEquals("baz.qux.BazImpl", scanner.next());
    assertEquals("foo.baz.OtherImpl", scanner.next());
    assertFalse(scanner.hasNext());
  }

  @Test
  public void testRewritingService() throws IOException, CompilationFailedException {
    File testJar = temp.newFile("test.jar");
    Path testJarPath = testJar.toPath();
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(testJarPath, options))) {
      ZipUtils.writeToZipStream(
          out,
          SERVICE_FILE,
          StringUtils.lines("foo.bar.BazImpl", "foo.baz.OtherImpl").getBytes(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/bar/Baz.class", Baz.dump(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/bar/BazImpl.class", BazImpl.dump(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/baz/OtherImpl.class", OtherImpl.dump(), ZipEntry.STORED);
    }
    ZipFile zip = new ZipFile(testJar);
    assertNotNull(zip.getEntry(SERVICE_FILE));
    Path relocatedJar = temp.newFile("out.jar").toPath();
    Relocator.run(
        RelocatorCommand.builder()
            .addProgramFile(testJarPath)
            .setOutputPath(relocatedJar)
            .addPackageMapping(
                Reference.packageFromString("foo.bar"), Reference.packageFromString("baz.qux"))
            .build());
    zip = new ZipFile(relocatedJar.toFile());
    ZipEntry serviceEntry = zip.getEntry("META-INF/services/baz.qux.Baz");
    assertNotNull(serviceEntry);
    InputStream inputStream = zip.getInputStream(serviceEntry);
    Scanner scanner = new Scanner(inputStream);
    assertEquals("baz.qux.BazImpl", scanner.next());
    assertEquals("foo.baz.OtherImpl", scanner.next());
    assertFalse(scanner.hasNext());
  }

  @Test
  public void testRewritingServiceImpl() throws IOException, CompilationFailedException {
    File testJar = temp.newFile("test.jar");
    Path testJarPath = testJar.toPath();
    OpenOption[] options =
        new OpenOption[] {StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING};
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(testJarPath, options))) {
      ZipUtils.writeToZipStream(
          out,
          SERVICE_FILE,
          StringUtils.lines("foo.bar.BazImpl", "foo.baz.OtherImpl").getBytes(),
          ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/bar/Baz.class", Baz.dump(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/bar/BazImpl.class", BazImpl.dump(), ZipEntry.STORED);
      ZipUtils.writeToZipStream(out, "foo/baz/OtherImpl.class", OtherImpl.dump(), ZipEntry.STORED);
    }
    ZipFile zip = new ZipFile(testJar);
    assertNotNull(zip.getEntry(SERVICE_FILE));
    Path relocatedJar = temp.newFile("out.jar").toPath();
    Relocator.run(
        RelocatorCommand.builder()
            .addProgramFile(testJarPath)
            .setOutputPath(relocatedJar)
            .addPackageMapping(
                Reference.packageFromString("foo.baz"), Reference.packageFromString("baz.qux"))
            .build());
    zip = new ZipFile(relocatedJar.toFile());
    ZipEntry serviceEntry = zip.getEntry("META-INF/services/foo.bar.Baz");
    assertNotNull(serviceEntry);
    InputStream inputStream = zip.getInputStream(serviceEntry);
    Scanner scanner = new Scanner(inputStream);
    assertEquals("foo.bar.BazImpl", scanner.next());
    assertEquals("baz.qux.OtherImpl", scanner.next());
    assertFalse(scanner.hasNext());
  }
}
