// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureEnclosingTest extends TestBase {

  private final TestParameters parameters;
  private final boolean isCompat;

  @Parameters(name = "{0}, isCompat: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GenericSignatureEnclosingTest(TestParameters parameters, boolean isCompat) {
    this.parameters = parameters;
    this.isCompat = isCompat;
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    (isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Foo.class, Bar.class)
        .addKeepMainRule(Main.class)
        // TODO(b/186630805): We should be able to compile with signature and not inner classes.
        .addKeepAttributeSignature()
        .setMinApi(parameters.getApiLevel())
        // When this test can compile, we should assert that the generic signatures for Bar$1 and
        // Bar$2 are not on enclosing form, even though Bar is kept.
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorThatMatches(
                  diagnosticMessage(
                      containsString("Attribute Signature requires InnerClasses attribute")));
            });
  }

  public abstract static class Foo<T, R> {

    R foo(T r) {
      System.out.println("Hello World");
      return null;
    }
  }

  public static class Bar {

    public static <T, R extends Main> Foo<T, R> enclosingMethod() {
      return new Foo<T, R>() {
        @Override
        R foo(T r) {
          System.out.println("Bar::enclosingMethod");
          return super.foo(r);
        }
      };
    }

    public static <T, R> Foo<T, R> enclosingMethod2() {
      return new Foo<T, R>() {
        @Override
        R foo(T r) {
          System.out.println("Bar::enclosingMethod2");
          return super.foo(r);
        }
      };
    }

    public static void run() {
      enclosingMethod().foo(null);
      enclosingMethod2().foo(null);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Bar.run();
    }
  }
}
