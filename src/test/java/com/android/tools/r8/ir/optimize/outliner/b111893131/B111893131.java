// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner.b111893131;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

class TestClass {
  public interface Act {
    // Need both builder and arg to create code snippets for outline candidates.
    String get(StringBuilder builder, String arg);
  }

  public static void main(String[] args) {
    System.out.println(test(new TestClass("OK").toAct(), new StringBuilder(), "1"));
  }

  // Need to pass Act and call #get to create private instance lambda$
  private static String test(Act act, StringBuilder builder, String arg) {
    // Outline candidate
    builder.append(arg).append(arg).append(arg);
    act.get(builder, "#");
    return builder.toString();
  }

  private final String foo;

  TestClass(String foo) {
    this.foo = foo;
  }

  private Act toAct() {
    return (builder, arg) -> {
      // Outline candidate
      builder.append(arg).append(arg).append(arg);
      return foo;
    };
  }
}

@RunWith(VmTestRunner.class)
public class B111893131 extends TestBase {

  @Ignore("b/111893131")
  @Test
  public void test() throws Exception {
    String javaResult = runOnJava(TestClass.class);

    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClass.Act.class));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    String config = keepMainProguardConfiguration(TestClass.class);
    builder.addProguardConfiguration(ImmutableList.of(config), Origin.unknown());
    AndroidApp app = ToolHelper.runR8(builder.build(), options -> {
      // To trigger outliner, set # of expected outline candidate as threshold.
      options.outline.threshold = 2;
      options.enableInlining = false;
      options.enableMinification = false;
    });
    ProcessResult result = runOnArtRaw(app, TestClass.class);
    assertEquals(0, result.exitCode);
    assertEquals(javaResult, result.stdout);

    CodeInspector inspector = new CodeInspector(app);
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    DexClass clazz = classSubject.getDexClass();
    clazz.forEachMethod(encodedMethod -> {
      Code code = encodedMethod.getCode();
      assertTrue(code.isDexCode());
      DexCode dexCode = code.asDexCode();
      // TODO(b/111893131): all outline candidate should be replaced with a call to outlined code.
      verifyAbsenceOfStringBuilderAppend(dexCode.instructions);
    });
  }

  private void verifyAbsenceOfStringBuilderAppend(Instruction[] instructions) {
    for (Instruction instr : instructions) {
      if (instr instanceof InvokeVirtual) {
        InvokeVirtual invokeVirtual = (InvokeVirtual) instr;
        DexMethod invokedMethod = invokeVirtual.getMethod();
        if (invokedMethod.getHolder().getName().endsWith("StringBuilder")) {
          assertNotEquals("append", invokedMethod.name.toString());
        }
      }
    }
  }

}
