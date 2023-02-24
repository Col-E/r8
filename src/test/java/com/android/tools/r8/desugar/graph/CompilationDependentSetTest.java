// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
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
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CompilationDependentSetTest extends TestBase {

  public interface I {
    // Empty.
  }

  public static class A implements I {
    // Empty.
  }

  public static class B {
    // Empty.
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }

  // Test runner follows.

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public CompilationDependentSetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(I.class, A.class, B.class, TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
    } else {
      Path dexInputForB =
          testForD8().addProgramClasses(B.class).setMinApi(parameters).compile().writeToZip();

      D8TestBuilder builder = testForD8();
      DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
      builder.getBuilder().setDesugarGraphConsumer(consumer);
      Origin originI = DesugarGraphUtils.addClassWithOrigin(I.class, builder);
      Origin originA = DesugarGraphUtils.addClassWithOrigin(A.class, builder);
      Origin originTestClass = DesugarGraphUtils.addClassWithOrigin(TestClass.class, builder);
      builder
          .addProgramFiles(dexInputForB)
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
      // If API level indicates desugaring is needed check the edges are reported.
      if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
        assertTrue(consumer.contains(originI, originA));
        assertEquals(1, consumer.totalEdgeCount());
      } else {
        assertEquals(0, consumer.totalEdgeCount());
      }
      // Regardless of API the potential inputs are reported.
      // Note that the DEX input is not a desugaring candidate and thus not included in the unit.
      assertEquals(
          ImmutableSet.of(originI, originA, originTestClass),
          consumer.getDesugaringCompilationUnit());
    }
  }
}
