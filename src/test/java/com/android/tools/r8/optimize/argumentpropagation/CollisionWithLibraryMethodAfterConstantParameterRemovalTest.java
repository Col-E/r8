// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CollisionWithLibraryMethodAfterConstantParameterRemovalTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoMethodStaticizingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              MethodSubject toStringMethodSubject =
                  aClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
              assertThat(toStringMethodSubject, isPresent());
              assertEquals(0, toStringMethodSubject.getProgramMethod().getReference().getArity());
              assertTrue(
                  toStringMethodSubject
                      .streamInstructions()
                      .anyMatch(instruction -> instruction.isConstString("Hello world!")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!", "false");
  }

  static class Main {

    public static void main(String[] args) {
      // If we change A.toString(String) to A.toString(), then we change the semantics of calling
      // A.toString().
      String libString = new A().toString();
      String greetingString = new A().toString("Hello world!");
      System.out.println(greetingString);
      System.out.println(libString.equals(greetingString));
    }
  }

  @NeverClassInline
  static class A {

    @NeverInline
    @NoMethodStaticizing
    public String toString(String whichOne) {
      return System.currentTimeMillis() > 0 ? whichOne : null;
    }
  }
}
