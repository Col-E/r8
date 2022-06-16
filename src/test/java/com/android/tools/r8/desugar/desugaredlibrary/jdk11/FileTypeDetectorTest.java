// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdk11;

import static com.android.tools.r8.ToolHelper.DexVm.Version.V12_0_0;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileTypeDetector;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
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
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        // Skip Android 4.4.4 due to missing libjavacrypto.
        getTestParameters()
            .withDexRuntime(Version.V4_0_4)
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        ImmutableList.of(JDK11_PATH),
        DEFAULT_SPECIFICATIONS);
  }

  public FileTypeDetectorTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private boolean hasDefaultPNGTypeDetector() {
    return parameters.getDexRuntimeVersion().compareTo(V12_0_0) < 0;
  }

  private String getExpectedResult() {
    return hasDefaultPNGTypeDetector()
        ? EXPECTED_RESULT_DEFAULT_PNG_TYPE_DETECTOR
        : EXPECTED_RESULT_NO_DEFAULT_PNG_TYPE_DETECTOR;
  }

  @Test
  public void test() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addProgramClasses(GoogleIcon.class)
        .addKeepMainRule(TestClass.class)
        .compile()
        .withArt6Plus64BitsLib()
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
