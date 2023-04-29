// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.LineTranslation;
import com.android.tools.r8.transformers.MethodTransformer.MethodContext;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MultipleZeroPositionsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public MultipleZeroPositionsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testZeroInInput() throws Exception {
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkZeroLineCount);
  }

  @Test
  public void testZeroAfterD8() throws Exception {
    Path out =
        testForD8(parameters.getBackend())
            .addProgramClassFileData(getTransformedClass())
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForJvm(parameters)
        .addProgramFiles(out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkZeroLineCount);
  }

  private void checkZeroLineCount(CodeInspector inspector) throws Exception {
    int expected = 3;
    // Until version 9.5, ASM skips zero lines so use javap to check the presence of a zero line.
    ClassSubject clazz = inspector.clazz(TestClass.class);
    String javap = clazz.javap(true);
    int actual = countOccurrences(javap, "line 0: ");
    assertEquals(
        "Expected " + expected + " 'line 0' entries, got " + actual + "\n" + javap,
        expected,
        actual);
  }

  private int countOccurrences(String string, String substring) {
    int i = 0;
    int found = 0;
    while (true) {
      i = string.indexOf(substring, i);
      if (i == -1) {
        return found;
      }
      ++found;
      ++i;
    }
  }

  private byte[] getTransformedClass() throws Exception {
    return transformer(TestClass.class)
        .setPredictiveLineNumbering(
            new LineTranslation() {
              Int2IntMap map = new Int2IntOpenHashMap();

              @Override
              public int translate(MethodContext context, int inputLine) {
                if (context.getReference().getMethodName().equals("main")) {
                  return map.computeIfAbsent(
                      inputLine, ignore -> map.size() % 2 == 0 ? 1 + map.size() : 0);
                }
                return inputLine;
              }
            })
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.nanoTime(); // line
      System.nanoTime(); // 0
      System.nanoTime(); // line
      System.nanoTime(); // 0
      System.out.println("Hello, world");
      ; // line
    }
  }
}
