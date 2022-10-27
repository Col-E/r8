// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceSynthesizedInnerFrameTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public RetraceSynthesizedInnerFrameTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final String mapping =
      "# { id: 'com.android.tools.r8.mapping', version: '1.0' }\n"
          + "some.Class -> a:\n"
          + "  3:3:int other.strawberry(int):101:101 -> a\n"
          + "  # { id: 'com.android.tools.r8.synthesized' }\n"
          + "  3:3:int mango(float):28 -> a";

  @Test
  public void testSyntheticClass() {
    List<RetraceFrameElement> frameResults =
        Retracer.createDefault(ProguardMapProducer.fromString(mapping), new DiagnosticsHandler() {})
            .retraceClass(Reference.classFromTypeName("a"))
            .stream()
            .flatMap(
                element ->
                    element
                        .lookupFrame(RetraceStackTraceContext.empty(), OptionalInt.of(3), "a")
                        .stream())
            .collect(Collectors.toList());
    assertEquals(1, frameResults.size());
    RetraceFrameElement retraceFrameElement = frameResults.get(0);
    List<RetracedMethodReference> allFrames =
        retraceFrameElement.stream()
            .map(RetracedSingleFrame::getMethodReference)
            .collect(Collectors.toList());
    assertEquals(2, allFrames.size());
    List<RetracedMethodReference> nonSyntheticFrames =
        retraceFrameElement
            .streamRewritten(RetraceStackTraceContext.empty())
            .map(RetracedSingleFrame::getMethodReference)
            .collect(Collectors.toList());
    // The input should never have a synthesized inline frame - rater it should have been removed
    // from the mapping output. Because of the incorrect input we now attribute the synthesized
    // information to the entire inline block.
    assertEquals(1, nonSyntheticFrames.size());
    assertEquals(
        Reference.methodFromDescriptor("Lother;", "strawberry", "(I)I"),
        nonSyntheticFrames.get(0).asKnown().getMethodReference());
  }
}
