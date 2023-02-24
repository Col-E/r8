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
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.origin.Origin;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestDependencyTest extends TestBase {

  public static class Host {}

  public static class Member1 {}

  public static class Member2 {}

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello World!");
    }
  }

  // Test runner follows.

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  private final TestParameters parameters;

  public NestDependencyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private byte[] inNest(Class<?> clazz) throws Exception {
    return transformer(clazz).setNest(Host.class, Member1.class, Member2.class).transform();
  }

  @Test
  public void test() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm(parameters)
          .addProgramClassFileData(inNest(Host.class), inNest(Member1.class), inNest(Member2.class))
          .addProgramClasses(TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
    } else {
      D8TestBuilder builder = testForD8();
      DesugarGraphTestConsumer consumer = new DesugarGraphTestConsumer();
      builder.getBuilder().setDesugarGraphConsumer(consumer);
      Origin originHost = DesugarGraphUtils.addClassWithOrigin("Host", inNest(Host.class), builder);
      Origin originMember1 =
          DesugarGraphUtils.addClassWithOrigin("Member1", inNest(Member1.class), builder);
      Origin originMember2 =
          DesugarGraphUtils.addClassWithOrigin("Member2", inNest(Member2.class), builder);
      builder
          .addProgramClasses(TestClass.class)
          .setMinApi(parameters)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
      // Currently there is no level at which nest desugaring is not needed.
      assertTrue(consumer.contains(originHost, originMember1));
      assertTrue(consumer.contains(originHost, originMember2));
      assertTrue(consumer.contains(originMember1, originHost));
      assertTrue(consumer.contains(originMember2, originHost));
      assertEquals(4, consumer.totalEdgeCount());
    }
  }
}
