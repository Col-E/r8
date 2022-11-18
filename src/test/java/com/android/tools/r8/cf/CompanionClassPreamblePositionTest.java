// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class CompanionClassPreamblePositionTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("no line", "has line");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withApiLevel(AndroidApiLevel.B)
        .enableApiLevelsForCf()
        .build();
  }

  public CompanionClassPreamblePositionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testZeroInInput() throws Exception {
    testForJvm()
        .addProgramClasses(TestClass.class, A.class)
        .addProgramClassFileData(getTransformedI(true))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkZeroLineIsPresent);
  }

  @Test
  public void testNoAddedZeroD8() throws Exception {
    Path out =
        testForD8(parameters.getBackend())
            .addProgramClasses(TestClass.class, A.class)
            .addProgramClassFileData(getTransformedI(false))
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    testForJvm()
        .addProgramFiles(out)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkZeroLineNotPresent);
  }

  private void checkZeroLineIsPresent(CodeInspector inspector) throws Exception {
    // ASM skips zero lines so use javap to check the presence of a zero line.
    ClassSubject itf = inspector.clazz(I.class);
    assertThat(itf.javap(true), containsString("line 0: 0"));
  }

  private void checkZeroLineNotPresent(CodeInspector inspector) throws Exception {
    ClassSubject companion = inspector.companionClassFor(I.class);
    assertThat(companion.javap(true), not(containsString("line 0: 0")));
  }

  private byte[] getTransformedI(boolean includeZero) throws Exception {
    return transformer(I.class)
        .setPredictiveLineNumbering(
            new LineTranslation() {
              Int2IntMap map = new Int2IntOpenHashMap();

              @Override
              public int translate(MethodContext context, int line) {
                if (context.getReference().getMethodName().equals("foo")) {
                  int newLine = map.computeIfAbsent(line, ignore -> map.size());
                  return newLine == 0 ? (includeZero ? 0 : -1) : newLine;
                }
                return line;
              }
            })
        .transform();
  }

  interface I {
    default void foo() {
      System.out.println("no line"); // Transform removes/replaces this line entry.
      System.out.println("has line");
    }
  }

  static class A implements I {}

  static class TestClass {

    public static void main(String[] args) {
      new A().foo();
    }
  }
}
