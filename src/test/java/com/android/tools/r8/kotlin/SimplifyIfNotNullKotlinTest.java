// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
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

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public SimplifyIfNotNullKotlinTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void test_example1() throws Exception {
    final TestKotlinClass ex1 = new TestKotlinClass("non_null.Example1Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("forMakeAndModel", "java.util.SortedMap",
            ImmutableList.of("java.util.Collection", STRING, STRING, "java.lang.Integer"));

    final String mainClassName = ex1.getClassName();
    final String extraRules = neverInlineMethod(mainClassName, testMethodSignature);
    runTest(
            FOLDER,
            mainClassName,
            testBuilder -> testBuilder.addKeepRules(extraRules).allowAccessModification())
        .inspect(
            inspector -> {
              ClassSubject clazz = checkClassIsKept(inspector, ex1.getClassName());

              MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
              long ifzCount =
                  testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
              long paramNullCheckCount =
                  countCall(testMethod, "Intrinsics", "checkParameterIsNotNull");
              // One after Iterator#hasNext, and another in the filter predicate: sinceYear != null.
              // TODO(b/179951729): Not the same amount of ifz on CF and DEX.
              assertEquals(testParameters.isCfRuntime() ? 1 : 2, ifzCount);
              assertEquals(0, paramNullCheckCount);
            });
  }

  @Test
  public void test_example2() throws Exception {
    // TODO(b/179866251): Update tests.
    assumeTrue(kotlinc.is(KOTLINC_1_3_72) || allowAccessModification);
    final TestKotlinClass ex2 = new TestKotlinClass("non_null.Example2Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("aOrDefault", STRING, ImmutableList.of(STRING, STRING));

    final String mainClassName = ex2.getClassName();
    final String extraRules = neverInlineMethod(mainClassName, testMethodSignature);
    runTest(FOLDER, mainClassName, testBuilder -> testBuilder.addKeepRules(extraRules))
        .inspect(
            inspector -> {
              ClassSubject clazz = checkClassIsKept(inspector, ex2.getClassName());

              MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
              long ifzCount =
                  testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
              long paramNullCheckCount =
                  countCall(testMethod, "Intrinsics", "checkParameterIsNotNull");
              // ?: in aOrDefault
              // TODO(b/179951729): Not the same amount of ifz on CF and DEX.
              assertEquals(testParameters.isCfRuntime() ? 0 : 1, ifzCount);
              assertEquals(0, paramNullCheckCount);
            });
  }

  @Test
  public void test_example3() throws Exception {
    final TestKotlinClass ex3 = new TestKotlinClass("non_null.Example3Kt");
    final MethodSignature testMethodSignature =
        new MethodSignature("neverThrowNPE", "void", ImmutableList.of("non_null.Foo"));

    final String mainClassName = ex3.getClassName();
    final String extraRules = neverInlineMethod(mainClassName, testMethodSignature);
    runTest(FOLDER, mainClassName, testBuilder -> testBuilder.addKeepRules(extraRules))
        .inspect(
            inspector -> {
              ClassSubject clazz = checkClassIsKept(inspector, ex3.getClassName());

              MethodSubject testMethod = checkMethodIsKept(clazz, testMethodSignature);
              long ifzCount =
                  testMethod.streamInstructions().filter(i -> i.isIfEqz() || i.isIfNez()).count();
              // !! operator inside explicit null check should be gone.
              // One explicit null-check as well as 4 bar? accesses.
              // TODO(b/179951729): Not the same amount of ifz on CF and DEX.
              assertEquals(testParameters.isCfRuntime() ? 0 : 5, ifzCount);
            });
  }
}
