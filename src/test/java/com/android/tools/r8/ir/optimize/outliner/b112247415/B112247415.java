// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b112247415;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

class TestClass {
  interface Act {
    default String get(StringBuilder builder, String arg) {
      builder.append(arg).append(arg).append(arg);
      return builder.toString();
    }
  }

  public static void main(String[] args) {
    System.out.println(get(new TestClass().toActOverridden(), new StringBuilder(), "a"));
    System.out.println(get(new TestClass().toActDefault(), new StringBuilder(), "b"));
  }

  static String get(Act act, StringBuilder builder, String arg) {
    act.get(builder, arg);
    return builder.toString();
  }

  Act toActOverridden() {
    return new Act() {
      @Override
      public String get(StringBuilder builder, String arg) {
        builder.append(arg).append(arg).append(arg);
        return builder.toString();
      }
    };
  }

  Act toActDefault() {
    return new Act() {
    };
  }
}

@RunWith(VmTestRunner.class)
public class B112247415 extends TestBase {

  @Test
  public void test() throws Exception {
    String javaResult = runOnJava(TestClass.class);

    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(TestClass.Act.class.getPackage()),
        path -> path.getFileName().toString().startsWith("TestClass")));
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
    for (FoundClassSubject clazz : inspector.allClasses()) {
      clazz.getDexClass().forEachMethod(encodedMethod -> {
        Code code = encodedMethod.getCode();
        if (code != null && !encodedMethod.method.name.toString().startsWith("outline")) {
          verifyAbsenceOfStringBuilderAppend(code.asDexCode().instructions);
        }
      });
    }
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
