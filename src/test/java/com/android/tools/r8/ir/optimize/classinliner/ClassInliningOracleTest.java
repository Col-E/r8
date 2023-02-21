// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Regression test for b/121119666. */
@RunWith(Parameterized.class)
public class ClassInliningOracleTest extends TestBase {

  private final boolean enableInvokeSuperToInvokeVirtualRewriting;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, enable invoke-super to invoke-virtual rewriting: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public ClassInliningOracleTest(
      boolean enableInvokeSuperToInvokeVirtualRewriting, TestParameters parameters) {
    this.enableInvokeSuperToInvokeVirtualRewriting = enableInvokeSuperToInvokeVirtualRewriting;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInliningOracleTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options ->
                options.testing.enableInvokeSuperToInvokeVirtualRewriting =
                    enableInvokeSuperToInvokeVirtualRewriting)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableUnusedArgumentAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              if (enableInvokeSuperToInvokeVirtualRewriting) {
                assertThat(inspector.clazz(Builder.class), not(isPresent()));
              } else {
                assertThat(inspector.clazz(Builder.class), isPresent());
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  static class TestClass {

    public static void main(String[] args) {
      // In order to class inline the Builder, we would need to force-inline the help() method.
      // We can't do this alone, though, since force-inlining of help() would lead to an illegal
      // invoke-super instruction in main().
      Builder builder = new Builder();
      new Helper().help(builder);
      System.out.print(builder.build());
    }
  }

  @NoVerticalClassMerging
  static class HelperBase {

    @NeverInline
    public void hello() {
      System.out.println("Hello");
    }
  }

  @NeverClassInline
  static class Helper extends HelperBase {

    @KeepUnusedArguments
    @NeverInline
    public void help(Builder builder) {
      super.hello();
    }
  }

  static class Builder {

    @NeverInline
    public Object build() {
      return new Object();
    }
  }
}
