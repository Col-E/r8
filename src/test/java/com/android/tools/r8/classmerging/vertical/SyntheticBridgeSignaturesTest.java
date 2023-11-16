// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticBridgeSignaturesTest extends VerticalClassMergerTestBase {

  // Try both with and without inlining. If the bridge signatures are not updated properly, and
  // inlining is enabled, then there can be issues with our inlining invariants regarding the
  // outermost caller. If inlining is disabled, there is a risk that the methods will end up
  // having the wrong signatures, or that the generated Proguard maps are incorrect (this will be
  // caught by the debugging test, which is carried out by the call to runTestOnInput()).
  private final boolean allowInlining;

  @Parameters(name = "{1}, inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), TestBase.getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public SyntheticBridgeSignaturesTest(boolean allowInlining, TestParameters parameters) {
    super(parameters);
    this.allowInlining = allowInlining;
  }

  @Test
  public void test() throws Throwable {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(TestClass.class)
            .applyIf(
                !allowInlining,
                builder -> builder.addOptionsModification(InlinerOptions::setOnlyForceInlining))
            .addVerticallyMergedClassesInspector(this::inspectVerticallyMergedClasses)
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .compile();

    compileResult
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();

    if (parameters.isDexRuntime()) {
      runDebugTest(TestClass.class, compileResult);
    }
  }

  private void inspectVerticallyMergedClasses(VerticallyMergedClassesInspector inspector) {
    inspector.assertMergedIntoSubtype(A.class, B.class);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(TestClass.class), isPresent());
    assertThat(inspector.clazz(ASub.class), isPresent());
    assertThat(inspector.clazz(BSub.class), isPresent());
    assertThat(inspector.clazz(A.class), not(isPresent()));
    assertThat(inspector.clazz(B.class), not(isPresent()));
  }

  static class TestClass {

    // If A is merged into ASub first, then the synthetic bridge for "void A.m(B)" will originally
    // get the signature "void ASub.m(B)". Otherwise, if B is merged into BSub first, then the
    // synthetic bridge will get the signature "void BSub.m(A)". In either case, it is important
    // that
    // the signatures of the bridge methods are updated after all classes have been merged
    // vertically.
    public static void main(String[] args) {
      ASub a = new ASub();
      BSub b = new BSub();
      a.m(b);
      b.m(a);

      // Ensure that the instantiations are not dead code eliminated.
      escape(a);
      escape(b);
    }

    @NeverInline
    static void escape(Object o) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(o);
      }
    }
  }

  private static class A {

    public void m(B object) {
      System.out.println("In A.m()");
    }
  }

  private static class ASub extends A {}

  private static class B {

    public void m(A object) {
      System.out.println("In B.m()");
    }
  }

  @NoHorizontalClassMerging
  private static class BSub extends B {}
}
