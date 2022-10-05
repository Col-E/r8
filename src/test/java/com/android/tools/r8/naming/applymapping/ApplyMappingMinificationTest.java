// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ApplyMappingMinificationTest extends TestBase {

  @NeverClassInline
  public static class A {

    @NeverPropagateValue public int fieldA = 1;

    @NeverPropagateValue public int fieldB = 2;

    @NeverInline
    public void methodA() {
      System.out.println("A.methodA");
    }

    @NeverInline
    public void methodB() {
      System.out.println("A.methodB");
    }

    @NeverInline
    public void methodC() {
      System.out.println("A.methodC");
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void foo() {
      System.out.println("B.foo");
    }
  }

  public static class C {

    public static void main(String[] args) {
      System.out.println(new A().fieldA);
      System.out.println(new A().fieldB);
      new A().methodA();
      new A().methodB();
      new A().methodC();
      new B().foo();
    }
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingMinificationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testApplyMappingFollowedByMinification()
      throws IOException, CompilationFailedException, ExecutionException, NoSuchMethodException {
    String[] pgMap =
        new String[] {
          A.class.getTypeName() + " -> a:", "  int fieldA -> a", "  void methodA() -> a"
        };
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(ApplyMappingMinificationTest.class)
            .addKeepMainRule(C.class)
            .addKeepRules(
                "-keepclassmembers class " + A.class.getTypeName() + " { void methodC(); }")
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .enableNeverClassInliningAnnotations()
            .addApplyMapping(StringUtils.lines(pgMap))
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), C.class)
            .assertSuccessWithOutputLines("1", "2", "A.methodA", "A.methodB", "A.methodC", "B.foo")
            .inspect(
                inspector -> {
                  ClassSubject clazzB = inspector.clazz(B.class);
                  assertThat(clazzB, isPresent());
                  assertTrue(clazzB.isRenamed());
                  ClassSubject clazzA = inspector.clazz(A.class);
                  assertThat(clazzA, isPresent());
                  assertEquals("a", clazzA.getFinalName());
                  FieldSubject fieldA = clazzA.uniqueFieldWithOriginalName("fieldA");
                  assertThat(fieldA, isPresent());
                  assertEquals("a", fieldA.getFinalName());
                  MethodSubject methodA = clazzA.uniqueMethodWithOriginalName("methodA");
                  assertThat(methodA, isPresent());
                  assertEquals("a", methodA.getFinalName());
                  FieldSubject fieldB = clazzA.uniqueFieldWithOriginalName("fieldB");
                  assertThat(fieldB, isPresent());
                  assertTrue(fieldB.isRenamed());
                  MethodSubject methodB = clazzA.uniqueMethodWithOriginalName("methodB");
                  assertThat(methodB, isPresent());
                  assertTrue(methodB.isRenamed());
                  MethodSubject methodC = clazzA.uniqueMethodWithOriginalName("methodC");
                  assertThat(methodC, isPresent());
                  assertFalse(methodC.isRenamed());
                });
    // Ensure that the proguard map is extended with all the new minified names.
    for (String pgLine : pgMap) {
      runResult.proguardMap().contains(pgLine);
    }
  }
}
