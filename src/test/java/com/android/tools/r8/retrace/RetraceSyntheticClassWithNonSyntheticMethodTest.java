// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
public class RetraceSyntheticClassWithNonSyntheticMethodTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceSyntheticClassWithNonSyntheticMethodTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final ClassReference originalClass = Reference.classFromTypeName("some.Class");
  private final ClassReference obfuscatedClass = Reference.classFromTypeName("a");

  private final String mapping =
      StringUtils.lines(
          "# { id: 'com.android.tools.r8.mapping', version: '1.0' }",
          originalClass.getTypeName() + " -> " + obfuscatedClass.getTypeName() + ":",
          "  # { id: 'com.android.tools.r8.synthesized' }",
          "  void <clinit>() -> <clinit>");

  @Test
  public void retraceSyntheticTest() {
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
    RetraceClassResult retraceClassResult = retracer.retraceClass(obfuscatedClass);
    List<RetraceClassElement> retracedClasses =
        retraceClassResult.stream().collect(Collectors.toList());
    assertEquals(1, retracedClasses.size());
    RetraceClassElement retraceClassElement = retracedClasses.get(0);
    assertEquals(originalClass, retraceClassElement.getRetracedClass().getClassReference());
    assertTrue(retraceClassElement.isCompilerSynthesized());
    RetraceMethodResult retraceMethodResult = retraceClassResult.lookupMethod("<clinit>");
    List<RetraceMethodElement> retracedMethods =
        retraceMethodResult.stream().collect(Collectors.toList());
    assertEquals(1, retracedMethods.size());
    RetraceMethodElement retraceMethodElement = retracedMethods.get(0);
    assertFalse(retraceMethodElement.isCompilerSynthesized());
    assertTrue(retraceMethodElement.getRetracedMethod().isKnown());
    assertEquals("<clinit>", retraceMethodElement.getRetracedMethod().getMethodName());
  }
}
