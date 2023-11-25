// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b115552239;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.code.DexCmpgFloat;
import com.android.tools.r8.dex.code.DexIfGez;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  public float f(float d) {
    while (d < 0f) {
      d += 360f;
    }
    return d % 360f;
  }
}

@RunWith(Parameterized.class)
public class B115552239 extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public B115552239(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private MethodSubject compileTestClassAndGetMethod(int apiLevel)
      throws IOException, CompilationFailedException {
    AndroidApp app =
        ToolHelper.runD8(
            D8Command.builder()
                .addClassProgramData(ToolHelper.getClassAsBytes(TestClass.class), Origin.unknown())
                .setMinApiLevel(apiLevel)
                .setMode(CompilationMode.RELEASE));
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("float", "f", ImmutableList.of("float"));
    assertThat(method, isPresent());
    return method;
  }

  @Test
  public void noLowering()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = compileTestClassAndGetMethod(AndroidApiLevel.L.getLevel());
    boolean previousWasCmp = false;
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    assertTrue(Arrays.stream(instructions).anyMatch(i -> i instanceof DexCmpgFloat));
    for (DexInstruction instruction : instructions) {
      if (instruction instanceof DexCmpgFloat) {
        previousWasCmp = true;
        continue;
      } else if (previousWasCmp) {
        assertTrue(instruction instanceof DexIfGez);
      }
      previousWasCmp = false;
    }
  }

  @Test
  public void lowering() throws IOException, CompilationFailedException {
    MethodSubject method = compileTestClassAndGetMethod(AndroidApiLevel.M.getLevel());
    boolean previousWasCmp = false;
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    assertTrue(Arrays.stream(instructions).anyMatch(i -> i instanceof DexCmpgFloat));
    for (DexInstruction instruction : instructions) {
      if (instruction instanceof DexCmpgFloat) {
        previousWasCmp = true;
        continue;
      } else if (previousWasCmp) {
        // We lowered the const instruction as close to its use as possible.
        assertFalse(instruction instanceof DexIfGez);
      }
      previousWasCmp = false;
    }
  }
}
