// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WithStaticizerTest extends TestBase {

  private static final Class<?> MAIN = Main.class;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(WithStaticizerTest.class)
        .addKeepMainRule(MAIN)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertMergedInto(Host.class, Host.Companion.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Input")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check if the candidate is indeed staticized.
    ClassSubject companion = inspector.clazz(Host.Companion.class);
    assertThat(companion, isPresent());
    MethodSubject foo = companion.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    assertThat(foo, isStatic());
  }

  @NeverClassInline
  static class Host {
    private static final Companion companion = new Companion();

    @NeverClassInline
    static class Companion {
      @NeverInline
      public void foo(Object arg) {
        // Technically same as String#valueOf
        if (arg != null) {
          System.out.println(arg.toString());
        } else {
          System.out.println("null");
        }
      }
    }

    @NeverInline
    static void bar(Object arg) {
      companion.foo(arg);
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Input {
    @NeverInline
    @Override
    public String toString() {
      return "Input";
    }
  }

  static class Main {
    public static void main(String[] args) {
      Host.bar(new Input());
    }
  }
}
