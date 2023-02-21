// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InlineSynthesizedLambdaClass extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public InlineSynthesizedLambdaClass(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Check that the program gives the expected result.
    String javaOutput = runOnJava(Lambda.class);

    // Check that everything has been inlined into main.
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(Lambda.class)
            .addKeepMainRule(Lambda.class)
            .allowAccessModification()
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Lambda.class)
            .assertSuccessWithOutput(javaOutput)
            .inspector();
    assertEquals(1, inspector.allClasses().size());

    ClassSubject classSubject = inspector.clazz(Lambda.class);
    assertThat(classSubject, isPresent());
    assertEquals(1, classSubject.allMethods().size());
  }
}

class Lambda {

  interface Consumer<T> {
    void accept(T value);
  }

  public static void main(String... args) {
    load(s -> System.out.println(s));
    load(s -> System.out.println(s));
  }

  public static void load(Consumer<String> c) {
    c.accept("Hello!");
  }
}
