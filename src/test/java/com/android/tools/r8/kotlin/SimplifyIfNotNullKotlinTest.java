// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SimplifyIfNotNullKotlinTest extends AbstractR8KotlinTestBase {
  private static final String FOLDER = "non_null";
  private static final String STRING = "java.lang.String";

  @Parameterized.Parameters(name = "target: {0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public SimplifyIfNotNullKotlinTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void test_example1() throws Exception {
    final TestKotlinClass ex1 = new TestKotlinClass("non_null.Example1Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("forMakeAndModel", "java.util.SortedMap",
            ImmutableList.of("java.util.Collection", STRING, STRING, "java.lang.Integer"));

    final String mainClassName = ex1.getClassName();
    final String extraRules =
        keepMainMethod(mainClassName) + neverInlineMethod(mainClassName, testMethodSignature);
    runTest(
        FOLDER,
        mainClassName,
        extraRules,
        app -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject clazz = checkClassIsKept(codeInspector, ex1.getClassName());

          MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
          long ifzCount =
              testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
          long paramNullCheckCount = countCall(testMethod, "Intrinsics", "checkParameterIsNotNull");
          // One after Iterator#hasNext, and another in the filter predicate: sinceYear != null.
          assertEquals(2, ifzCount);
          assertEquals(0, paramNullCheckCount);
        });
  }

  @Test
  public void test_example2() throws Exception {
    final TestKotlinClass ex2 = new TestKotlinClass("non_null.Example2Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("aOrDefault", STRING, ImmutableList.of(STRING, STRING));

    final String mainClassName = ex2.getClassName();
    final String extraRules =
        keepMainMethod(mainClassName) + neverInlineMethod(mainClassName, testMethodSignature);
    runTest(FOLDER, mainClassName, extraRules,
        app -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject clazz = checkClassIsKept(codeInspector, ex2.getClassName());

          MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
          long ifzCount =
              testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
          long paramNullCheckCount =
              countCall(testMethod, "Intrinsics", "checkParameterIsNotNull");
          // ?: in aOrDefault
          assertEquals(1, ifzCount);
          assertEquals(allowAccessModification ? 0 : 1, paramNullCheckCount);
        });
  }

  @Test
  public void test_example3() throws Exception {
    final TestKotlinClass ex3 = new TestKotlinClass("non_null.Example3Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("neverThrowNPE", "void", ImmutableList.of("non_null.Foo"));

    final String mainClassName = ex3.getClassName();
    final String extraRules =
        keepMainMethod(mainClassName) + neverInlineMethod(mainClassName, testMethodSignature);
    runTest(FOLDER, mainClassName, extraRules,
        app -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject clazz = checkClassIsKept(codeInspector, ex3.getClassName());

          MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
          long ifzCount =
              testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
          // !! operator inside explicit null check should be gone.
          // One explicit null-check as well as 4 bar? accesses.
          assertEquals(5, ifzCount);
        });
  }
}
