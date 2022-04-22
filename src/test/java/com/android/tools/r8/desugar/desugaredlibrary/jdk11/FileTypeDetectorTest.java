// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.ToolHelper.DexVm.Version.V12_0_0;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FileTypeDetectorTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT_DEFAULT_PNG_TYPE_DETECTOR =
      StringUtils.lines("false", "text/plain", "image/png", "null", "image/png");
  private static final String EXPECTED_RESULT_NO_DEFAULT_PNG_TYPE_DETECTOR =
      StringUtils.lines("false", "text/plain", "null", "null", "image/png");
  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    // Skip Android 4.4.4 due to missing libjavacrypto.
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build());
  }

  public FileTypeDetectorTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  private boolean hasDefaultPNGTypeDetector() {
    return parameters.getDexRuntimeVersion().compareTo(V12_0_0) < 0;
  }

  private String getExpectedResult() {
    return hasDefaultPNGTypeDetector()
        ? EXPECTED_RESULT_DEFAULT_PNG_TYPE_DETECTOR
        : EXPECTED_RESULT_NO_DEFAULT_PNG_TYPE_DETECTOR;
  }

  private LibraryDesugaringTestConfiguration pathConfiguration() {
    return LibraryDesugaringTestConfiguration.builder()
        .setMinApi(parameters.getApiLevel())
        .addDesugaredLibraryConfiguration(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonForTestingWithPath()))
        .setMode(shrinkDesugaredLibrary ? CompilationMode.RELEASE : CompilationMode.DEBUG)
        .withKeepRuleConsumer()
        .build();
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    testForD8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .addInnerClasses(getClass())
        .addProgramClasses(GoogleIcon.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(isJDK11DesugaredLibrary());
    testForR8(Backend.DEX)
        .addLibraryFiles(getLibraryFile())
        .addInnerClasses(getClass())
        .addProgramClasses(GoogleIcon.class)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .enableCoreLibraryDesugaring(pathConfiguration())
        .compile()
        .withArt6Plus64BitsLib()
        .withArtFrameworks()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  public static class TestClass {

    public static void main(String[] args) throws Throwable {
      // FileTypeDetector usage through ServiceLoader.
      ServiceLoader<FileTypeDetector> serviceLoader = ServiceLoader.load(FileTypeDetector.class);
      Iterator<FileTypeDetector> iterator = serviceLoader.iterator();
      System.out.println(iterator.hasNext());
      while (iterator.hasNext()) {
        FileTypeDetector fileTypeDetector = iterator.next();
        System.out.println((fileTypeDetector == null));
      }

      Path emptyText = Files.createTempFile("example", ".txt");
      Path png = getGoogleIconPng();

      // FileTypeDetector usage through Files.
      System.out.println(Files.probeContentType(emptyText));
      System.out.println(Files.probeContentType(png));

      // Custom file type detector usage.
      FileTypeDetector fileTypeDetector = new PngFileTypeDetector();
      System.out.println(fileTypeDetector.probeContentType(emptyText));
      System.out.println(fileTypeDetector.probeContentType(png));
    }

    private static Path getGoogleIconPng() throws IOException {
      Path picture = Files.createTempFile("art", ".png");
      Files.write(picture, GoogleIcon.GOOGLE_ICON_PNG);
      return picture;
    }
  }

  public static class PngFileTypeDetector extends FileTypeDetector {

    private static final byte[] PNG_HEADER = {
      (byte) 0x89,
      (byte) 0x50,
      (byte) 0x4E,
      (byte) 0x47,
      (byte) 0x0D,
      (byte) 0x0A,
      (byte) 0x1A,
      (byte) 0x0A
    };

    private static final int PNG_HEADER_SIZE = PNG_HEADER.length;

    @Override
    public String probeContentType(final Path path) throws IOException {
      final byte[] buf = new byte[PNG_HEADER_SIZE];

      try (final InputStream in = Files.newInputStream(path); ) {
        if (in.read(buf) != PNG_HEADER_SIZE) {
          return null;
        }
      }

      return Arrays.equals(buf, PNG_HEADER) ? "image/png" : null;
    }
  }
}
