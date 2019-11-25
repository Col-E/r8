// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestMethodAccessTest extends TestBase {
  static final String EXPECTED = StringUtils.lines("A::bar", "A::baz");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public NestMethodAccessTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(
            withNest(A.class)
                .setPrivate(A.class.getDeclaredMethod("bar"))
                .setPrivate(A.class.getDeclaredMethod("baz"))
                .transform(),
            withNest(B.class).transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    return transformer(clazz).setNest(A.class, B.class);
  }

  static class A {
    /* will be private */ void bar() {
      System.out.println("A::bar");
    }

    /* will be private */ static void baz() {
      System.out.println("A::baz");
    }
  }

  static class B {
    public void foo() {
      // Virtual invoke to private method.
      new A().bar();
      // Static invoke to private method.
      A.baz();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new B().foo();
    }
  }
}
