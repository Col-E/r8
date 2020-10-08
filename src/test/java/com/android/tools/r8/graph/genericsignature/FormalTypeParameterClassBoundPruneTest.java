// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import java.lang.reflect.TypeVariable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FormalTypeParameterClassBoundPruneTest extends TestBase {

  private final TestParameters parameters;
  private final String INTERFACE_BOUND =
      "L" + DescriptorUtils.getBinaryNameFromJavaType(Interface.class.getTypeName()) + "<TT;>;";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public FormalTypeParameterClassBoundPruneTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntimeWithNoPrunedSuperType() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(Main.class).removeInnerClasses().transform(),
            transformer(Super.class).removeInnerClasses().transform(),
            transformer(Interface.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            Super.class.getTypeName() + "<T>", Interface.class.getTypeName() + "<T>");
  }

  @Test
  public void testRuntimeWithPrunedSuperType() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setGenericSignature("<T::" + INTERFACE_BOUND + ">Ljava/lang/Object;")
                .transform(),
            transformer(Super.class).removeInnerClasses().transform(),
            transformer(Interface.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(Interface.class.getTypeName() + "<T>");
  }

  @Test
  public void testRuntimeWithObjectSuperType() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            transformer(Main.class)
                .removeInnerClasses()
                .setGenericSignature(
                    "<T:Ljava/lang/Object;:" + INTERFACE_BOUND + ">Ljava/lang/Object;")
                .transform(),
            transformer(Super.class).removeInnerClasses().transform(),
            transformer(Interface.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "class java.lang.Object", Interface.class.getTypeName() + "<T>");
  }

  public static class Super<T> {}

  public interface Interface<T> {}

  public static class Main<T extends Super<T> & Interface<T>> {

    public static void main(String[] args) {
      TypeVariable<Class<Main>> typeParameter = Main.class.getTypeParameters()[0];
      for (java.lang.reflect.Type bound : typeParameter.getBounds()) {
        System.out.println(bound.toString());
      }
    }
  }
}
