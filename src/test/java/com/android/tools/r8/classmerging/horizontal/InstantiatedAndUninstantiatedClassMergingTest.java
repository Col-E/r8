// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class InstantiatedAndUninstantiatedClassMergingTest extends HorizontalClassMergingTestBase {

  public InstantiatedAndUninstantiatedClassMergingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    test(testForR8(parameters.getBackend()));
  }

  @Test
  public void testR8Compat() throws Exception {
    test(testForR8Compat(parameters.getBackend()));
  }

  private <T extends R8TestBuilder<T>> void test(T testBuilder) throws Exception {
    testBuilder
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(Instantiated.class), isPresent());
              assertThat(
                  inspector.clazz(Uninstantiated.class),
                  notIf(isPresent(), testBuilder.isR8CompatTestBuilder()));
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Instantiated", "Uninstantiated");
  }

  static class TestClass {

    public static void main(String[] args) {
      new Instantiated();
      Uninstantiated.method();
    }
  }

  @NeverClassInline
  public static final class Instantiated {

    @NeverInline
    Instantiated() {
      System.out.println("Instantiated");
    }
  }

  public static final class Uninstantiated {

    @NeverInline
    static void method() {
      System.out.println("Uninstantiated");
    }
  }
}
