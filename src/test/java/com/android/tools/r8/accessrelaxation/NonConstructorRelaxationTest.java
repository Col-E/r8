// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.accessrelaxation.privateinstance.Base;
import com.android.tools.r8.accessrelaxation.privateinstance.Sub1;
import com.android.tools.r8.accessrelaxation.privateinstance.Sub2;
import com.android.tools.r8.accessrelaxation.privateinstance.TestMain;
import com.android.tools.r8.accessrelaxation.privatestatic.A;
import com.android.tools.r8.accessrelaxation.privatestatic.B;
import com.android.tools.r8.accessrelaxation.privatestatic.BB;
import com.android.tools.r8.accessrelaxation.privatestatic.C;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public final class NonConstructorRelaxationTest extends AccessRelaxationTestBase {

  private static final String STRING = "java.lang.String";

  private boolean enableArgumentRemoval;

  @Parameterized.Parameters(name = "{0}, argument removal: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public NonConstructorRelaxationTest(TestParameters parameters, boolean enableArgumentRemoval) {
    super(parameters);
    this.enableArgumentRemoval = enableArgumentRemoval;
  }

  @Test
  public void testStaticMethodRelaxation() throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "A::baz()",
            "A::bar()",
            "A::bar(int)",
            "A::blah(int)",
            "B::blah(int)",
            "BB::blah(int)",
            "A::foo()A::baz()A::bar()A::bar(int)",
            "B::bar() >> java.lang.IllegalAccessError",
            "java.lang.IllegalAccessError",
            "A::foo()A::baz()A::bar()A::bar(int)",
            "B::blah(int)",
            "A::foo()A::baz()A::bar()A::bar(int)",
            "B::bar() >> java.lang.IllegalAccessError",
            "C::bar(int)java.lang.IllegalAccessErrorB::bar() >> "
                + "java.lang.IllegalAccessErrorB::bar() >> java.lang.IllegalAccessError",
            "B::foo()A::foo()A::baz()A::bar()A::bar(int)",
            "C::blah(int)");
    Class<?> mainClass = C.class;
    if (parameters.isCfRuntime()) {
      // Only run JVM reference on CF runtimes.
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), mainClass)
          .assertSuccessWithOutput(expectedOutput);
    }

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()))
            .enableInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(mainClass)
            .addOptionsModification(o -> o.enableArgumentRemoval = enableArgumentRemoval)
            .noMinification()
            .addKeepRules(
                // Note: we use '-checkdiscard' to indirectly check that the access relaxation is
                // done which leads to inlining of all pB*** methods so they are removed. Without
                // access relaxation inlining is not performed and method are kept.
                "-checkdiscard class " + A.class.getCanonicalName() + "{",
                "  *** pBaz();",
                "  *** pBar();",
                "  *** pBar1();",
                "  *** pBlah1();",
                "}",
                "-checkdiscard class " + B.class.getCanonicalName() + "{",
                "  *** pBlah1();",
                "}",
                "-checkdiscard class " + BB.class.getCanonicalName() + "{",
                "  *** pBlah1();",
                "}")
            .allowAccessModification()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), mainClass);

    assertEquals(
        expectedOutput,
        result
            .getStdOut()
            .replace("java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError"));

    CodeInspector inspector = result.inspector();
    assertPublic(inspector, A.class, new MethodSignature("baz", STRING, ImmutableList.of()));
    assertPublic(inspector, A.class, new MethodSignature("bar", STRING, ImmutableList.of()));
    assertPublic(inspector, A.class, new MethodSignature("bar", STRING, ImmutableList.of("int")));

    MethodSignature blahMethodSignature =
        new MethodSignature(
            "blah", STRING, enableArgumentRemoval ? ImmutableList.of() : ImmutableList.of("int"));
    assertPublic(inspector, A.class, blahMethodSignature);
    assertPublic(inspector, B.class, blahMethodSignature);
    assertPublic(inspector, BB.class, blahMethodSignature);
  }

  @Test
  public void testInstanceMethodRelaxationWithVerticalClassMerging() throws Exception {
    testInstanceMethodRelaxation(true);
  }

  @Test
  public void testInstanceMethodRelaxationWithoutVerticalClassMerging() throws Exception {
    testInstanceMethodRelaxation(false);
  }

  private void testInstanceMethodRelaxation(boolean enableVerticalClassMerging) throws Exception {
    String expectedOutput =
        StringUtils.lines(
            "Base::foo()",
            "Base::foo1()",
            "Base::foo2()",
            "Base::foo3()",
            "Sub1::foo1()",
            "Itf1::foo1(0) >> Sub1::foo1()",
            "Sub1::bar1(0)",
            "Sub1::foo3()",
            "Sub2::foo2()",
            "Itf2::foo2(0) >> Sub2::foo2()",
            "Sub2::bar2(0)",
            "Sub2::foo3()");
    Class<?> mainClass = TestMain.class;
    if (parameters.isCfRuntime()) {
      // Only run JVM reference on CF runtimes.
      testForJvm()
          .addTestClasspath()
          .run(parameters.getRuntime(), mainClass)
          .assertSuccessWithOutput(expectedOutput);
    }

    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()))
            .addKeepMainRule(mainClass)
            .addOptionsModification(o -> o.enableVerticalClassMerging = enableVerticalClassMerging)
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .noMinification()
            .addKeepRules(
                "-checkdiscard class " + Base.class.getCanonicalName() + "{",
                "  *** p*();",
                "}",
                "-checkdiscard class " + Sub1.class.getCanonicalName() + "{",
                "  *** p*();",
                "}",
                "-checkdiscard class " + Sub2.class.getCanonicalName() + "{",
                "  *** p*();",
                "}")
            .allowAccessModification()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), mainClass);

    assertEquals(
        expectedOutput,
        result
            .getStdOut()
            .replace("java.lang.IncompatibleClassChangeError", "java.lang.IllegalAccessError"));

    // When vertical class merging is enabled, Itf1 is merged into Sub1 and Itf2 is merged into
    // Sub2, and as a result of these merges, neither Sub1 nor Sub2 end up in the output because of
    // inlining.
    CodeInspector codeInspector = result.inspector();
    assertPublic(codeInspector, Base.class, new MethodSignature("foo", STRING, ImmutableList.of()));

    // Base#foo?() can't be publicized due to Itf<1>#foo<1>().
    assertNotPublic(
        codeInspector, Base.class, new MethodSignature("foo1", STRING, ImmutableList.of()));
    assertNotPublic(
        codeInspector, Base.class, new MethodSignature("foo2", STRING, ImmutableList.of()));

    if (!enableVerticalClassMerging) {
      // Sub?#bar1(int) can be publicized as they don't bother each other.
      assertPublic(
          codeInspector, Sub1.class, new MethodSignature("bar1", STRING, ImmutableList.of("int")));
      assertPublic(
          codeInspector, Sub2.class, new MethodSignature("bar1", STRING, ImmutableList.of("int")));

      // Sub2#bar2(int) is unique throughout the hierarchy, hence publicized.
      assertPublic(
          codeInspector, Sub2.class, new MethodSignature("bar2", STRING, ImmutableList.of("int")));
    }
  }
}
