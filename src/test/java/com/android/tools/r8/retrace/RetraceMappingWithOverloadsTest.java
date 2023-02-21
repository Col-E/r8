// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceMappingWithOverloadsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceMappingWithOverloadsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final ClassReference someClassOriginal = Reference.classFromTypeName("some.Class");
  private final ClassReference someClassRenamed = Reference.classFromTypeName("A");

  private final String mapping =
      StringUtils.lines(
          someClassOriginal.getTypeName() + " -> " + someClassRenamed.getTypeName() + ":",
          "  java.util.List select(java.util.List) -> a",
          "  3:3:void sync():425:425 -> a",
          "  4:5:void sync():427:428 -> a",
          "  void cancel(java.lang.String[]) -> a");

  @Test
  public void test() {
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
    List<RetraceMethodElement> a =
        retracer.retraceClass(someClassRenamed).lookupMethod("a").stream()
            .collect(Collectors.toList());
    assertEquals(3, a.size());
  }
}
