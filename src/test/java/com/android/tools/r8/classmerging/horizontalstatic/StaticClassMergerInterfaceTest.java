// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontalstatic;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticCompanionClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticClassMergerInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticClassMergerInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In I.a()", "In J.b()", "In A.c()");
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        // TODO(b/173990042): Extend horizontal class merging to interfaces.
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                inspector.assertNoClassesMerged();
              } else {
                inspector
                    .assertClassReferencesMerged(
                        SyntheticItemsTestUtils.syntheticCompanionClass(I.class),
                        SyntheticItemsTestUtils.syntheticCompanionClass(J.class))
                    .assertClassesNotMerged(I.class, J.class);
              }
            })
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput)
        .inspect(
            inspector -> {
              // We do not allow horizontal class merging of interfaces and classes. Therefore, A
              // should remain in the output.
              assertThat(inspector.clazz(A.class), isPresent());

              // TODO(b/173990042): I and J should be merged.
              if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
                assertThat(inspector.clazz(I.class), isPresent());
                assertThat(inspector.clazz(J.class), isPresent());
              } else {
                assertThat(inspector.clazz(syntheticCompanionClass(I.class)), isPresent());
                assertThat(inspector.clazz(syntheticCompanionClass(J.class)), isAbsent());
              }
            });
  }

  static class TestClass {

    public static void main(String[] args) {
      I.a();
      J.b();
      A.c();
    }
  }

  interface I {

    @NeverInline
    static void a() {
      System.out.println("In I.a()");
    }
  }

  interface J {

    @NeverInline
    static void b() {
      System.out.println("In J.b()");
    }
  }

  static class A {

    @NeverInline
    static void c() {
      System.out.println("In A.c()");
    }
  }
}
