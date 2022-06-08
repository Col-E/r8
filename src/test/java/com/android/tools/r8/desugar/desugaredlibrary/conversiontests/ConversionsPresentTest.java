// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConversionsPresentTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;

  @Parameters(name = "{0}, spec: {1}")
  public static List<Object[]> data() {
    return buildParameters(getConversionParametersUpToExcluding(MIN_SUPPORTED), getJdk8Jdk11());
  }

  public ConversionsPresentTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testConversionsDex() throws Exception {
    testForL8(parameters.getApiLevel())
        .apply(libraryDesugaringSpecification::configureL8TestBuilder)
        .compile()
        .apply(c -> checkConversionGeneratedDex(c.inspector()));
  }

  private void checkConversionGeneratedDex(CodeInspector inspector) {
    List<FoundClassSubject> conversionsClasses =
        inspector.allClasses().stream()
            .filter(c -> c.getOriginalName().contains("Conversions"))
            .filter(
                c ->
                    c.getOriginalName().contains(".util.")
                        || c.getOriginalName().contains(".time."))
            .collect(Collectors.toList());
    if (requiresEmulatedInterfaceCoreLibDesugaring(parameters)) {
      assertEquals(5, conversionsClasses.size());
      assertTrue(inspector.clazz("j$.util.OptionalConversions").isPresent());
      assertTrue(inspector.clazz("j$.time.TimeConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.LongSummaryStatisticsConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.IntSummaryStatisticsConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.DoubleSummaryStatisticsConversions").isPresent());
    } else if (requiresTimeDesugaring(parameters, libraryDesugaringSpecification != JDK8)) {
      assertEquals(1, conversionsClasses.size());
      assertTrue(inspector.clazz("j$.time.TimeConversions").isPresent());
    } else {
      assertEquals(0, inspector.allClasses().size());
    }
  }
}
