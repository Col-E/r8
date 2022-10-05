// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CompanionAsArgumentTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public CompanionAsArgumentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(CompanionAsArgumentTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Companion#foo(true)")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check if the candidate is not staticized.
    ClassSubject companion = inspector.clazz(Host.Companion.class);
    assertThat(companion, isPresent());
    MethodSubject foo = companion.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    assertTrue(
        foo.streamInstructions().anyMatch(
            i -> i.isInvokeVirtual()
                && i.getMethod().toSourceString().contains("PrintStream.println")));

    // Nothing migrated from Companion to Host.
    ClassSubject host = inspector.clazz(Host.class);
    assertThat(host, isPresent());
    MethodSubject migrated_foo = host.uniqueMethodWithOriginalName("foo");
    assertThat(migrated_foo, not(isPresent()));
  }

  @NeverClassInline
  static class Host {
    private static final Companion companion = new Companion();

    static class Companion {
      @NeverInline
      public void foo(Object arg) {
        System.out.println("Companion#foo(" + (arg != null) + ")");
      }
    }

    @NeverInline
    static void bar() {
      // The target singleton is used as not only a receiver but also an argument.
      companion.foo(companion);
    }
  }

  static class Main {
    public static void main(String[] args) {
      Host.bar();
    }
  }
}
