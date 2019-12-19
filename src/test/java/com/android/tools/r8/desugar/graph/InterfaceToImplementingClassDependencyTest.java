// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.graph;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceToImplementingClassDependencyTest extends TestBase {

  // The simplest program that gives rise to a dependency edge in the graph is an interface with
  // an implementing class. In this case, changes to the interface could require re-dexing the
  // implementing class despite no derived changes happing to the classfile for the implementing
  // class.
  //
  // For example, adding default method I.foo will trigger javac compilation of I and A to I.class
  // and A.class, here I.class will have changed, but A.class is unchanged, thus only I.class will
  // be compiled to dex if nothing informs about the desugaring dependency. That is incorrect,
  // since A.dex needs to contain the desugared code for calling the default method.
  //
  // Note: the dependency of I for the compilation of A exists even when no default methods do.

  public interface I {
    // Emtpy.
  }

  public class A implements I {
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
      testForJvm()
          .addProgramClasses(I.class, A.class, TestClass.class)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
    } else {
      MyDesugarGraphConsumer consumer = new MyDesugarGraphConsumer();
      Origin originI = makeOrigin("I");
      Origin originA = makeOrigin("A");
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramClasses(TestClass.class)
          .apply(
              b ->
                  b.getBuilder()
                      .setDesugarGraphConsumer(consumer)
                      .addClassProgramData(ToolHelper.getClassAsBytes(I.class), originI)
                      .addClassProgramData(ToolHelper.getClassAsBytes(A.class), originA))
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines("Hello World!");
      // If API level indicates desugaring is needed check the edges are reported.
      if (parameters.getApiLevel().getLevel() < AndroidApiLevel.N.getLevel()) {
        assertEquals(1, consumer.edges.size());
        assertEquals(originI, consumer.edges.keySet().iterator().next());
        assertEquals(originA, consumer.edges.values().iterator().next().iterator().next());
      } else {
        assertEquals(0, consumer.edges.size());
      }
    }
  }

  private Origin makeOrigin(String name) {
    return new Origin(Origin.root()) {
      @Override
      public String part() {
        return name;
      }
    };
  }

  public static class MyDesugarGraphConsumer implements DesugarGraphConsumer {

    Map<Origin, Set<Origin>> edges = new HashMap<>();

    @Override
    public synchronized void accept(Origin dependent, Origin dependency) {
      edges.computeIfAbsent(dependency, s -> new HashSet<>()).add(dependent);
    }
  }
}
