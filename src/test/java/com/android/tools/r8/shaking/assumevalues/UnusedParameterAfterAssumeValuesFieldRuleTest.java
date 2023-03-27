// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.assumevalues;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedParameterAfterAssumeValuesFieldRuleTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-assumevalues class * { int f return 42; }")
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());

              List<FoundMethodSubject> aConstructorSubjects =
                  aClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer);
              if (parameters.canHaveNonReboundConstructorInvoke()) {
                assertEquals(0, aConstructorSubjects.size());
              } else {
                assertEquals(1, aConstructorSubjects.size());

                FoundMethodSubject aConstructorSubject = aConstructorSubjects.get(0);
                assertEquals(0, aConstructorSubject.getParameters().size());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new A(args.length));
    }
  }

  static class A {

    int f;

    A(int f) {
      this.f = f;
    }

    @Override
    public String toString() {
      return Integer.toString(f);
    }
  }
}
