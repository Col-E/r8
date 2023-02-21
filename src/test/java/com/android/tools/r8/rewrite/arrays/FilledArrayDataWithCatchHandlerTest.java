// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.cf.methodhandles.fields.ClassFieldMethodHandleTest.Main.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexNewArray;
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
public class FilledArrayDataWithCatchHandlerTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("1");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public FilledArrayDataWithCatchHandlerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(FilledArrayDataWithCatchHandlerTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testReleaseD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .release()
        .setMinApi(parameters)
        .addInnerClasses(FilledArrayDataWithCatchHandlerTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkInstructions);
  }

  private void checkInstructions(CodeInspector inspector) {
    MethodSubject foo = inspector.clazz(TestClass.class).uniqueMethodWithFinalName("foo");
    List<InstructionSubject> newArrays =
        foo.streamInstructions()
            .filter(i -> i.asDexInstruction().getInstruction() instanceof DexNewArray)
            .collect(Collectors.toList());
    assertEquals(1, newArrays.size());

    List<InstructionSubject> fillArrays =
        foo.streamInstructions()
            .filter(i -> i.asDexInstruction().getInstruction() instanceof DexFillArrayData)
            .collect(Collectors.toList());
    assertEquals(1, fillArrays.size());

    InstructionOffsetSubject offsetNew = newArrays.get(0).getOffset(foo);
    InstructionOffsetSubject offsetFill = newArrays.get(0).getOffset(foo);
    assertTrue(
        foo.streamTryCatches()
            .allMatch(r -> r.getRange().includes(offsetNew) && r.getRange().includes(offsetFill)));
  }

  static class TestClass {

    public static int foo() {
      int value = 1;
      int[] array = null;
      try {
        array = new int[6];
      } catch (RuntimeException e) {
        return array[0];
      }
      array[0] = value;
      array[1] = value;
      array[2] = value;
      array[3] = value;
      array[4] = value;
      array[5] = value;
      return array[5];
    }

    public static void main(String[] args) {
      System.out.println(foo());
    }
  }
}
