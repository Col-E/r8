// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SyntheticMarkerDexTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  public SyntheticMarkerDexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path out =
        testForD8(parameters.getBackend())
            .setMinApi(parameters.getApiLevel())
            .setIntermediate(true)
            .addProgramClasses(TestClass.class)
            .compile()
            .inspect(this::checkSyntheticClassIsMarked)
            .writeToZip();
    testForD8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(out)
        // Use intermediate again to preserve synthetics.
        .setIntermediate(true)
        // Disable desugaring so we are sure the lambda synthetic is created in the first round.
        .disableDesugaring()
        .compile()
        .inspect(this::checkSyntheticClassIsMarked)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private void checkSyntheticClassIsMarked(CodeInspector inspector) {
    // Compilation gives rise to the main class plus one lambda.
    assertEquals(2, inspector.allClasses().size());
    inspector.forAllClasses(
        clazz -> {
          if (!clazz.getFinalReference().equals(Reference.classFromClass(TestClass.class))) {
            // This should be the lambda class.
            DexAnnotation[] annotations = clazz.getDexProgramClass().annotations().annotations;
            assertEquals(1, annotations.length);
            DexEncodedAnnotation annotation = annotations[0].annotation;
            assertEquals(3, annotation.elements.length);
            assertEquals(
                "com.android.tools.r8.annotations.SynthesizedClassV2",
                annotation.type.toSourceString());
          }
        });
  }

  public static class TestClass {

    public static void run(Runnable r) {
      r.run();
    }

    public static void main(String[] args) {
      run(() -> System.out.println("Hello, world"));
    }
  }
}
