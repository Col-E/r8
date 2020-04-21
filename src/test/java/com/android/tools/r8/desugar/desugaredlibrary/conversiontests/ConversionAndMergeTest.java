// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConversionAndMergeTest extends DesugaredLibraryTestBase {

  private static final String GMT = StringUtils.lines("GMT");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getConversionParametersUpToExcluding(AndroidApiLevel.O);
  }

  public ConversionAndMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMerge() throws Exception {
    Path extra = buildClass(ExtraClass.class);
    Path convClass = buildClass(APIConversionClass.class);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(extra, convClass)
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
        .compile()
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, parameters.getApiLevel())
        .run(parameters.getRuntime(), APIConversionClass.class)
        .assertSuccessWithOutput(GMT);
  }

  private Path buildClass(Class<?> cls) throws Exception {
    return testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(cls)
        .enableCoreLibraryDesugaring(parameters.getApiLevel())
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
