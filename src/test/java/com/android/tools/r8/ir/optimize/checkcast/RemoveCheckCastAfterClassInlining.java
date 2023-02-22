// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RemoveCheckCastAfterClassInlining extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // Extract main method.
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(Lambda.class)
            .addKeepMainRule(Lambda.class)
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile()
            .inspector();
    ClassSubject classSubject = inspector.clazz(Lambda.class);
    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    DexEncodedMethod method = methodSubject.getMethod();
    assertTrue(method.hasCode());

    DexCode code = method.getCode().asDexCode();
    int numberOfConstStringInstructions = 0;
    for (DexInstruction instruction : code.instructions) {
      // Make sure that we do not load a const-string and then subsequently use a check-cast
      // instruction to check if it is actually a string.
      assertFalse(instruction.isCheckCast());
      if (instruction.isConstString()) {
        numberOfConstStringInstructions++;
      }
    }

    // Sanity check that load() was actually inlined.
    assertThat(
        classSubject.method("void", "load", ImmutableList.of(Lambda.Consumer.class.getName())),
        not(isPresent()));
    assertEquals(1, numberOfConstStringInstructions);
  }
}

class Lambda {

  interface Consumer<T> {
    void accept(T value);
  }

  public static void main(String... args) {
    load(s -> System.out.println(s));
    // Other code…
    load(s -> System.out.println(s));
  }

  public static void load(Consumer<String> c) {
    c.accept("Hello!");
  }
}
