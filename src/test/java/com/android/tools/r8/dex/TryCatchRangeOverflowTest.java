// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// Regression test for b/297320921
@RunWith(Parameterized.class)
public class TryCatchRangeOverflowTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build();
  }

  public TryCatchRangeOverflowTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Each add/2addr instruction has size 1, so we add have as many instruction minus some padding
  // to make room for the instructions before and after but still in the same block.
  // Notice that this value may change if the generated code by the compiler changes. It must then
  // be updated to the precise limit again so that the test for jumbo-string exactly hits the
  // crossing point.
  private final int PADDING = 33;
  private final int UNSPLIT_LIMIT = 0xFFFF - PADDING;
  private final int SPLIT_2_LIMIT = 0xFFFF * 2 - PADDING;

  @Test
  public void testWithinU2() throws Exception {
    parameters.assumeDexRuntime();
    int addCount = UNSPLIT_LIMIT;
    compile(addCount)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("" + addCount)
        .inspect(inspector -> checkTryCatchHandlers(1, inspector));
  }

  @Test
  public void testJumboExceedsU2() throws Exception {
    parameters.assumeDexRuntime();
    int addCount = UNSPLIT_LIMIT;
    compile(addCount)
        .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("" + addCount)
        .inspect(inspector -> checkTryCatchHandlers(2, inspector));
  }

  @Test
  public void testExceedsU2() throws Exception {
    parameters.assumeDexRuntime();
    // Test with a few values above the limit.
    for (int addCount : Arrays.asList(UNSPLIT_LIMIT + 1, UNSPLIT_LIMIT + 2, UNSPLIT_LIMIT + 100)) {
      compile(addCount)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("" + addCount)
          .inspect(inspector -> checkTryCatchHandlers(2, inspector));
    }
  }

  @Test
  public void testWithinU2x2() throws Exception {
    parameters.assumeDexRuntime();
    int addCount = SPLIT_2_LIMIT;
    compile(addCount)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("" + addCount)
        .inspect(inspector -> checkTryCatchHandlers(2, inspector));
  }

  @Test
  public void testJumboExceedsU2x2() throws Exception {
    parameters.assumeDexRuntime();
    int addCount = SPLIT_2_LIMIT;
    compile(addCount)
        .addOptionsModification(o -> o.testing.forceJumboStringProcessing = true)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("" + addCount)
        .inspect(inspector -> checkTryCatchHandlers(3, inspector));
  }

  @Test
  public void testExceedsU2x2() throws Exception {
    parameters.assumeDexRuntime();
    int addCount = SPLIT_2_LIMIT + 1;
    compile(addCount)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("" + addCount)
        .inspect(inspector -> checkTryCatchHandlers(3, inspector));
  }

  private D8TestBuilder compile(int addCount) throws Exception {
    return testForD8(Backend.DEX)
        .addProgramClasses(TestClass.class)
        .addOptionsModification(
            o ->
                o.testing.irModifier =
                    (code, appView) -> amendCodeWithAddInstructions(addCount, code))
        .setMinApi(parameters);
  }

  private static void amendCodeWithAddInstructions(int addCount, IRCode code) {
    if (!code.context().getReference().qualifiedName().endsWith("main")) {
      return;
    }
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.isAdd()) {
        TypeElement outType = instruction.getOutType();
        DebugLocalInfo localInfo = instruction.getLocalInfo();
        // Create the last value which will replace the users of the original value in the
        // continuations.
        Value newLastValue = code.createValue(outType, localInfo);
        instruction.outValue().replaceUsers(newLastValue);

        Add add = instruction.asAdd();
        NumericType numericType = add.getNumericType();
        assert add.rightValue().isConstNumber();
        for (int i = 1; i < addCount; i++) {
          Value dest = i == addCount - 1 ? newLastValue : code.createValue(outType, localInfo);
          Add newAdd = Add.create(numericType, dest, add.outValue(), add.rightValue());
          add.outValue().addDebugLocalEnd(newAdd);
          newAdd.setPosition(add.getPosition());
          it.add(newAdd);
          add = newAdd;
        }
        return;
      }
    }
    fail("Expected to find an Add instruction.");
  }

  private static void checkTryCatchHandlers(int tryCount, CodeInspector inspector)
      throws NoSuchMethodException {

    MethodSubject main = inspector.method(TestClass.class.getMethod("main", String[].class));
    Try[] tries = main.getMethod().getCode().asDexCode().tries;
    assertEquals(Arrays.toString(tries), tryCount, tries.length);
  }

  static class TestClass {

    public static void main(String[] args) {
      int i = 0;
      try {
        String str;
        int len = args.length;
        if (len == 0) {
          str = "";
        } else if (len == 1 /* Using a constant 1 here causes the add to be an add/2addr */) {
          str = "Strings might become jumbos";
        } else if (len % 2 == 0) {
          str = "We need 4";
        } else {
          str = "to ensure overflow.";
        }
        i = str.length();
        ++i; // repeated count number of times.
        i += args[0].length();
      } catch (Throwable e) {
        System.out.println(i);
        return;
      }
      System.out.println("unexpected i " + i);
    }
  }
}
