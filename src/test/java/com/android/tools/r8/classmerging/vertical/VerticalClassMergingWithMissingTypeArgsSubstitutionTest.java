// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingWithMissingTypeArgsSubstitutionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test()
  public void test() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () -> {
          testForR8Compat(parameters.getBackend())
              .addInnerClasses(getClass())
              .addKeepMainRule(Main.class)
              .addKeepClassRules(A.class)
              .addKeepAttributeSignature()
              .addVerticallyMergedClassesInspector(
                  inspector -> inspector.assertMergedIntoSubtype(B.class))
              .enableInliningAnnotations()
              .setMinApi(parameters.getApiLevel())
              .compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorsMatch(
                          DiagnosticsMatcher.diagnosticType(ExceptionDiagnostic.class)));
        });
  }

  static class Main {

    public static void main(String[] args) {
      C<String> stringC = new C<>();
      for (TypeVariable<? extends Class<? extends C>> typeParameter :
          stringC.getClass().getTypeParameters()) {
        System.out.println(typeParameter.getName());
      }
      stringC.m("Hello World");
    }
  }

  static class A<T> {}

  static class B<T> extends A<T> {

    @NeverInline
    public T foo(T t) {
      return t;
    }
  }

  static class C<T> extends B {

    void m(T t) {
      System.out.println(foo(t));
    }
  }
}
