// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeStaticWithNullOutvalueTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // TODO(b/112831361): support for class staticizer in CF backend.
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public InvokeStaticWithNullOutvalueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeStaticWithNullOutvalueTest.class)
        .addKeepMainRule(MAIN)
        .enableInliningAnnotations()
        .enableKeepUnusedReturnValueAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Companion#boo", "Companion#foo")
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    // Check if the instance is gone.
    ClassSubject host = inspector.clazz(Host.class);
    assertThat(host, isPresent());
    FieldSubject instance = host.uniqueFieldWithOriginalName("companion");
    assertThat(instance, not(isPresent()));

    ClassSubject companion = inspector.clazz(Host.Companion.class);
    // TODO(b/158018192): This should not be present.
    assertThat(companion, isPresent());

    // Check if the candidate methods are staticized (if necessary) and migrated.
    for (String name : ImmutableList.of("boo", "foo")) {
      // TODO(b/158018192): This should be host and not companion.
      MethodSubject oo = companion.uniqueMethodWithOriginalName(name);
      assertThat(oo, isPresent());
      assertTrue(oo.isStatic());
      assertTrue(
          oo.streamInstructions().anyMatch(
              i -> i.isInvokeVirtual()
                  && i.getMethod().toSourceString().contains("PrintStream.println")));
    }
  }

  @NeverClassInline
  static class Host {
    private static final Companion companion = new Companion();

    @NoHorizontalClassMerging
    static class Companion {
      @KeepUnusedReturnValue
      @NeverInline
      @NeverPropagateValue
      private static Object boo() {
        System.out.println("Companion#boo");
        return null;
      }

      @NeverInline
      void foo() {
        // Return value is not used, hence invoke-static without out-value.
        boo();
        System.out.println("Companion#foo");
      }
    }

    @NeverInline
    static void bar() {
      companion.foo();
    }
  }

  static class Main {
    public static void main(String[] args) {
      Host.bar();
    }
  }
}
