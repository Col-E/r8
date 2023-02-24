// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceBridgeDependencyTest extends TestBase {

  public interface I {
    Object foo();
  }

  public interface J extends I {
    // Covariant override of foo will result in a synthetic bridge method and J will depend on I.
    default String foo() {
      return "J::foo";
    }
  }

  public interface K extends I {
    // A simple override does not cause a bridge and there is no desugar dependencies for K.
    default Object foo() {
      return "K::foo";
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      // There are no instances of I, J or K, so just make sure that the code requires they exist.
      System.out.println(I.class.getName());
      System.out.println(J.class.getName());
      System.out.println(K.class.getName());
    }
  }

  // Test runner follows.

  private static final String EXPECTED =
      StringUtils.lines(I.class.getName(), J.class.getName(), K.class.getName());

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public InterfaceBridgeDependencyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(I.class, J.class, K.class, TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
    } else {
      D8TestBuilder builder = testForD8();
      DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
      builder.getBuilder().setDesugarGraphConsumer(consumer);
      Origin originI = DesugarGraphUtils.addClassWithOrigin(I.class, builder);
      Origin originJ = DesugarGraphUtils.addClassWithOrigin(J.class, builder);
      Origin originK = DesugarGraphUtils.addClassWithOrigin(K.class, builder);
      builder
          .setMinApi(parameters)
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      // If API level indicates desugaring is needed check the edges are reported.
      if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
        assertTrue(consumer.contains(originI, originJ));
        assertFalse(consumer.contains(originI, originK));
        assertEquals(1, consumer.totalEdgeCount());
      } else {
        assertEquals(0, consumer.totalEdgeCount());
      }
    }
  }
}
