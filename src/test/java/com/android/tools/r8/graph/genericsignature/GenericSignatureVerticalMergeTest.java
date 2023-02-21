// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.reflect.Type;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureVerticalMergeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureVerticalMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult runResult =
        testForR8Compat(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .addKeepClassRules(I.class, J.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(Base.class)
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addKeepAttributeSignature()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .addVerticallyMergedClassesInspector(
                inspector -> inspector.assertMergedIntoSubtype(A.class))
            .run(parameters.getRuntime(), Main.class);
    ClassSubject baseSubject = runResult.inspector().clazz(Base.class);
    assertThat(baseSubject, isPresent());
    runResult.assertSuccessWithOutputLines(
        baseSubject.getFinalName() + "<" + String.class.getTypeName() + ">",
        J.class.getTypeName() + "<X>",
        // TODO(b/191871201): This could be `X` if we tracked where type-variables are introduced.
        I.class.getTypeName() + "<java.lang.Object>",
        "I::t",
        "B::r");
  }

  public interface I<T> {

    T t();
  }

  @NoVerticalClassMerging
  public static class Base<T> {}

  public static class A<S, T> extends Base<S> implements I<T> {

    @Override
    @NeverInline
    public T t() {
      System.out.println("I::t");
      return null;
    }
  }

  public interface J<R> {

    void r(R r);
  }

  @NeverClassInline
  public static class B<X> extends A<String, X> implements J<X> {

    @Override
    @NeverInline
    public void r(X x) {
      System.out.println("B::r");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(B.class.getGenericSuperclass());
      for (Type genericInterface : B.class.getGenericInterfaces()) {
        System.out.println(genericInterface);
      }
      B<String> b = new B<>();
      b.r(b.t());
    }
  }
}
