// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
public class ApplyMappingRepackagingTest extends TestBase {

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
      new B().foo();
    }
  }

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingRepackagingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testApplyMappingFollowedByMinification()
      throws IOException, CompilationFailedException, ExecutionException {
    String[] pgMap =
        new String[] {
          A.class.getTypeName() + " -> baz:", "  int fieldA -> foo", "  void methodA() -> bar"
        };
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(ApplyMappingRepackagingTest.class)
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .enableNeverClassInliningAnnotations()
            .addApplyMapping(StringUtils.lines(pgMap))
            .setMinApi(parameters)
            .addKeepMainRule(C.class)
            .addKeepRules("-repackageclasses")
            .run(parameters.getRuntime(), C.class)
            .assertSuccessWithOutputLines("1", "2", "A.methodA", "A.methodB", "B.foo")
            .inspect(
                inspector -> {
                  assertThat(inspector.clazz(B.class), isPresentAndRenamed());
                  ClassSubject clazzA = inspector.clazz(A.class);
                  assertThat(clazzA, isPresent());
                  assertEquals("baz", clazzA.getFinalName());
                  FieldSubject fieldA = clazzA.uniqueFieldWithOriginalName("fieldA");
                  assertThat(fieldA, isPresent());
                  assertEquals("foo", fieldA.getFinalName());
                  MethodSubject methodA = clazzA.uniqueMethodWithOriginalName("methodA");
                  assertThat(methodA, isPresent());
                  assertEquals("bar", methodA.getFinalName());
                  assertThat(clazzA.uniqueFieldWithOriginalName("fieldB"), isPresentAndRenamed());
                  assertThat(clazzA.uniqueMethodWithOriginalName("methodB"), isPresentAndRenamed());
                });
    // Ensure that the proguard map is extended with all the new minified names.
    for (String pgLine : pgMap) {
      runResult.proguardMap().contains(pgLine);
    }
  }
}
