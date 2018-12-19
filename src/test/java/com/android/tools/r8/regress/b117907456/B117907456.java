// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b117907456;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.ReturnWide;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

class TestClass {
  public static void f(boolean loop) {
    while (loop) {
      System.out.println("Noooooo!");
    }
    throw new RuntimeException();
  }
}

public class B117907456 extends TestBase {

  private boolean isReturn(Instruction lastInstruction) {
    return lastInstruction instanceof ReturnVoid
        || lastInstruction instanceof Return
        || lastInstruction instanceof ReturnWide;
  }

  @Test
  public void testNopDupInsertionForDalvikTracingBug()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = getMethodSubject(AndroidApiLevel.K);
    Instruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    Instruction lastInstruction = instructions[instructions.length - 1];
    assertFalse(lastInstruction instanceof Throw);
    assertTrue(isReturn(lastInstruction));
  }

  @Test
  public void testNoNopDupInsertionForDalvikTracingBug()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = getMethodSubject(AndroidApiLevel.L);
    Instruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    Instruction lastInstruction = instructions[instructions.length - 1];
    assertTrue(lastInstruction instanceof Throw);
  }

  private MethodSubject getMethodSubject(AndroidApiLevel level)
      throws CompilationFailedException, IOException, ExecutionException {
    CodeInspector inspector = testForD8()
        .addProgramClasses(TestClass.class)
        .setMinApi(level)
        .compile()
        .inspector();
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "f", ImmutableList.of("boolean"));
    assertThat(method, isPresent());
    return method;
  }
}
