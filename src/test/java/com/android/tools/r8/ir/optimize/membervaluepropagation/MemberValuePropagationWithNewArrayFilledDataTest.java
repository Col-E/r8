// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberValuePropagationWithNewArrayFilledDataTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(MemberValuePropagationWithNewArrayFilledDataTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject fontClassSubject = inspector.clazz(R.font.class);
              assertThat(fontClassSubject, isPresent());

              FieldSubject robotoMonoFieldSubject =
                  fontClassSubject.uniqueFieldWithOriginalName("roboto_mono");
              assertThat(robotoMonoFieldSubject, not(isPresent()));

              FieldSubject robotoMonoBoldFieldSubject =
                  fontClassSubject.uniqueFieldWithOriginalName("roboto_mono_bold");
              assertThat(robotoMonoBoldFieldSubject, not(isPresent()));

              FieldSubject robotoMonoRegularFieldSubject =
                  fontClassSubject.uniqueFieldWithOriginalName("roboto_mono_regular");
              assertThat(robotoMonoRegularFieldSubject, not(isPresent()));

              FieldSubject robotoMonoWeightsFieldSubject =
                  fontClassSubject.uniqueFieldWithOriginalName("roboto_mono_weights");
              assertThat(robotoMonoWeightsFieldSubject, isPresent());

              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              assertThat(testClassSubject, isPresent());

              MethodSubject methodSubject = testClassSubject.mainMethod();
              assertTrue(
                  methodSubject
                      .streamInstructions()
                      .filter(InstructionSubject::isStaticGet)
                      .map(x -> x.getField().name.toSourceString())
                      .allMatch(
                          name ->
                              name.equals("out")
                                  || name.equals(robotoMonoWeightsFieldSubject.getFinalName())));
            });
  }

  static class TestClass {

    public static void main(String... args) {
      System.out.println(R.font.roboto_mono_bold);
      System.out.println(R.font.roboto_mono_weights);
    }
  }

  static class R {
    private R() {}

    public static final class font {
      private font() {}

      public static int roboto_mono = 0x7f080000;
      public static int roboto_mono_bold = 0x7f080001;
      public static int roboto_mono_regular = 0x7f080002;
      public static int[] roboto_mono_weights = {roboto_mono_bold, roboto_mono_regular};
    }
  }
}
