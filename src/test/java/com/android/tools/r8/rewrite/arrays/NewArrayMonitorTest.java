// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexFilledNewArray;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionOffsetSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NewArrayMonitorTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("1");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public NewArrayMonitorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(NewArrayMonitorTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testReleaseD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .release()
        .setMinApi(parameters)
        .addInnerClasses(NewArrayMonitorTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInstructions);
  }

  private void checkInstructions(CodeInspector inspector) {
    MethodSubject foo = inspector.clazz(TestClass.class).uniqueMethodWithFinalName("foo");
    List<InstructionSubject> filledArrayInstructions =
        foo.streamInstructions()
            .filter(i -> i.asDexInstruction().getInstruction() instanceof DexFilledNewArray)
            .collect(Collectors.toList());
    assertEquals(1, filledArrayInstructions.size());
    InstructionOffsetSubject offset = filledArrayInstructions.get(0).getOffset(foo);
    assertTrue(foo.streamTryCatches().allMatch(r -> r.getRange().includes(offset)));
  }

  static class TestClass {

    public static synchronized int foo() {
      int value = 1;
      int[] array = new int[1];
      try {
        array[0] = value;
      } catch (RuntimeException e) {
        return array[0];
      }
      return array[0];
    }

    public static void main(String[] args) {
      System.out.println(foo());
    }
  }
}
