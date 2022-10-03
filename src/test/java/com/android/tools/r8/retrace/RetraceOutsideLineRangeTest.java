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
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceOutsideLineRangeTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private final ClassReference someOtherClassOriginal =
      Reference.classFromTypeName("some.other.Class");
  private final ClassReference someOtherClassRenamed = Reference.classFromTypeName("a");
  private final ClassReference someClassOriginal = Reference.classFromTypeName("some.Class");
  private final ClassReference someClassRenamed = Reference.classFromTypeName("b");

  private final String mapping =
      someOtherClassOriginal.getTypeName()
          + " -> "
          + someOtherClassRenamed.getTypeName()
          + ":\n"
          + "  void method1():42:42 -> a\n"
          + someClassOriginal.getTypeName()
          + " -> "
          + someClassRenamed.getTypeName()
          + ":\n"
          + "  28:28:void foo.bar.inlinee():92:92 -> a\n"
          + "  1:3:void method2():11:13 -> a\n"
          + "  4:4:void method2():10:10 -> a\n"
          + "  5:5:void method2():14 -> a\n";

  @Test
  public void testNoLine() {
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {});
    retraceClassMethodAndPosition(retracer, someClassRenamed, someClassOriginal, 2, 1);
    retraceClassMethodAndPosition(retracer, someOtherClassRenamed, someOtherClassOriginal, 1, 1);
  }

  private void retraceClassMethodAndPosition(
      Retracer retracer,
      ClassReference renamedClass,
      ClassReference originalClass,
      int methodCount,
      int frameCount) {
    List<RetraceClassElement> classResult =
        retracer.retraceClass(renamedClass).stream().collect(Collectors.toList());
    assertEquals(classResult.size(), 1);
    RetraceClassElement retraceClassResult = classResult.get(0);
    assertEquals(originalClass, retraceClassResult.getRetracedClass().getClassReference());
    RetraceMethodResult retraceMethodResult = retraceClassResult.lookupMethod("a");
    List<RetraceMethodElement> classMethodResults =
        retraceMethodResult.stream().collect(Collectors.toList());
    assertEquals(methodCount, classMethodResults.size());
    List<RetraceFrameElement> classResultFrames =
        retraceMethodResult
            .narrowByPosition(RetraceStackTraceContext.empty(), OptionalInt.of(6))
            .stream()
            .collect(Collectors.toList());
    assertEquals(frameCount, classResultFrames.size());
  }
}
