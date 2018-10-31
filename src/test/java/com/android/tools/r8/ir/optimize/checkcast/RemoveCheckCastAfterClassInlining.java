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
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class RemoveCheckCastAfterClassInlining extends TestBase {

  @Test
  public void test() throws Exception {
    AndroidApp input = readClasses(Lambda.class, Lambda.Consumer.class);
    AndroidApp output =
        compileWithR8(
            input,
            keepMainProguardConfiguration(Lambda.class),
            options -> options.enableMinification = false);

    // Extract main method.
    CodeInspector inspector = new CodeInspector(output);
    ClassSubject classSubject = inspector.clazz(Lambda.class);
    MethodSubject methodSubject = classSubject.mainMethod();
    assertThat(methodSubject, isPresent());

    DexEncodedMethod method = methodSubject.getMethod();
    assertTrue(method.hasCode());

    DexCode code = method.getCode().asDexCode();
    int numberOfConstStringInstructions = 0;
    for (Instruction instruction : code.instructions) {
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
