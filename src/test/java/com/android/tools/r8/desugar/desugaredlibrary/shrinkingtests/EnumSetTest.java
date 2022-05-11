// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.shrinkingtests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.StringUtils;
import java.time.DayOfWeek;
import java.time.chrono.IsoEra;
import java.time.chrono.MinguoEra;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumSetTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public EnumSetTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testEnum() throws Exception {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(EnumSetUser.class)
        .addKeepMainRule(EnumSetUser.class)
        .run(parameters.getRuntime(), EnumSetUser.class)
        .assertSuccessWithOutput(StringUtils.lines("2", "0", "1"));
  }

  static class EnumSetUser {

    public static void main(String[] args) {
      EnumSet<IsoEra> isoEras = EnumSet.allOf(IsoEra.class);
      System.out.println(isoEras.size());
      EnumSet<DayOfWeek> dayOfWeeks = EnumSet.noneOf(DayOfWeek.class);
      System.out.println(dayOfWeeks.size());
      EnumSet<MinguoEra> minguoEras = EnumSet.of(MinguoEra.BEFORE_ROC);
      System.out.println(minguoEras.size());
    }
  }
}
