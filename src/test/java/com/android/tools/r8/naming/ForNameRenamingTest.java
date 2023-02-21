// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ForNameRenamingTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public ForNameRenamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ForNameRenamingTest.class)
        .addKeepMainRule(MAIN)
        .addKeepClassRulesWithAllowObfuscation(Boo.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("true")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject boo = inspector.clazz(Boo.class);
    assertThat(boo, isPresentAndRenamed());

    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject mainMethod = main.mainMethod();
    assertThat(mainMethod, isPresent());

    assertTrue(
        mainMethod.streamInstructions()
            .noneMatch(i -> i.isConstString(
                "com.android.tools.r8.naming.ForNameRenamingTest$Boo", JumboStringMode.ALLOW)));
    assertTrue(
        mainMethod.streamInstructions()
            .anyMatch(i -> i.isConstString(boo.getFinalName(), JumboStringMode.ALLOW)));
  }

  static class Main {
    public static void main(String... args) throws Exception {
      Class<?> a = Class.forName("com.android.tools.r8.naming.ForNameRenamingTest$Boo");
      Class<?> b = Class.forName(
          "com.android.tools.r8.naming.ForNameRenamingTest$Boo", true, Boo.class.getClassLoader());
      System.out.println(a.getSimpleName().equals(b.getSimpleName()));
    }
  }

  static class Boo {}
}
