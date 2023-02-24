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
public class InterfaceToImplementingClassDependencyTest extends TestBase {

  // The simplest program that gives rise to a dependency edge in the graph is an interface with
  // an implementing class. In this case, changes to the interface could require re-dexing the
  // implementing class despite no derived changes happening to the classfile for the implementing
  // class.
  //
  // For example, adding default method I.foo will trigger javac compilation of I and A to I.class
  // and A.class, here I.class will have changed, but A.class is unchanged, thus only I.class will
  // be compiled to dex if nothing informs about the desugaring dependency. That is incorrect,
  // since A.dex needs to contain the desugared code for calling the default method.
  //
  // Note: the dependency of I for the compilation of A exists even when no default methods do.

  public interface I {
    // Empty.
  }

  public static class A implements I {
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

  public InterfaceToImplementingClassDependencyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClasses(I.class, A.class, TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
    } else {
      D8TestBuilder builder = testForD8();
      DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
      builder.getBuilder().setDesugarGraphConsumer(consumer);
      Origin originI = DesugarGraphUtils.addClassWithOrigin(I.class, builder);
      Origin originA = DesugarGraphUtils.addClassWithOrigin(A.class, builder);
      builder
          .addProgramClasses(TestClass.class)
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
    }
  }

}
