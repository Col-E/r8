// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaDependencyTest extends TestBase {

  public interface I {
    void foo();
  }

  public static class A {
    void bar(I i) {
      i.foo();
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      new A().bar(() -> System.out.println("lambda!"));
    }
  }

  // Test runner follows.

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public LambdaDependencyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(I.class, A.class, TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("lambda!");
    } else {
      D8TestBuilder builder = testForD8();
      DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
      builder.getBuilder().setDesugarGraphConsumer(consumer);
      Origin originI = DesugarGraphUtils.addClassWithOrigin(I.class, builder);
      Origin originA = DesugarGraphUtils.addClassWithOrigin(A.class, builder);
      Origin originMain = DesugarGraphUtils.addClassWithOrigin(TestClass.class, builder);
      builder
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("lambda!");
      // If API level indicates desugaring is needed check the edges are reported.
      if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
        // Generated lambda class in TestClass.main depends on potential default methods in I.
        assertTrue(consumer.contains(originI, originMain));
        assertEquals(1, consumer.totalEdgeCount());
      } else {
        assertEquals(0, consumer.totalEdgeCount());
      }
    }
  }
}
