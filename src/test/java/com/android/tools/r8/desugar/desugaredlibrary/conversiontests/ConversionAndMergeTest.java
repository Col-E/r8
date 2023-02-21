// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConversionAndMergeTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;
  private static final String EXPECTED_RESULT = StringUtils.lines("GMT");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public ConversionAndMergeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testMerge() throws Exception {
    Path extra = buildClass(ExtraClass.class);
    Path convClass = buildClass(APIConversionClass.class);
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramFiles(extra, convClass)
        .run(parameters.getRuntime(), APIConversionClass.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private Path buildClass(Class<?> cls) throws Exception {
    return testForD8()
        .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
        .setMinApi(parameters)
        .addProgramClasses(cls)
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forSpecification(
                libraryDesugaringSpecification.getSpecification()))
        .compile()
        .writeToZip();
  }

  static class ExtraClass {
    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }

  static class APIConversionClass {
    public static void main(String[] args) {
      // Following is a call where java.time.ZoneId is a parameter type (getTimeZone()).
      TimeZone timeZone = TimeZone.getTimeZone(ZoneId.systemDefault());
      // Following is a call where java.time.ZoneId is a return type (toZoneId()).
      System.out.println(timeZone.toZoneId().getId());
    }
  }
}
