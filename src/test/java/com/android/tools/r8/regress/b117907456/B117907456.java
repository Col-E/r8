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
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexThrow;
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

  private boolean isGoto(DexInstruction lastInstruction) {
    return lastInstruction instanceof DexGoto;
  }

  @Test
  public void testNopDupInsertionForDalvikTracingBug()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = getMethodSubject(AndroidApiLevel.K);
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    DexInstruction lastInstruction = instructions[instructions.length - 1];
    assertFalse(lastInstruction instanceof DexThrow);
    assertTrue(isGoto(lastInstruction));
  }

  @Test
  public void testNoNopDupInsertionForDalvikTracingBug()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = getMethodSubject(AndroidApiLevel.L);
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    DexInstruction lastInstruction = instructions[instructions.length - 1];
    assertTrue(lastInstruction instanceof DexThrow);
  }

  private MethodSubject getMethodSubject(AndroidApiLevel level)
      throws CompilationFailedException, IOException {
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
