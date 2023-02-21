// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.uninstantiatedtypes;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class B146957343 extends TestBase implements Opcodes {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public B146957343(TestParameters parameters) {
    this.parameters = parameters;
  }

  private byte[] getAimplementsI() throws IOException {
    return transformer(A.class).setImplements(I.class).transform();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, J.class, Main.class)
        .addProgramClassFileData(getAimplementsI())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("In A.f()");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, J.class, Main.class)
        .addProgramClassFileData(getAimplementsI())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("In A.f()");
  }

  public interface I {}

  public interface J extends I {}

  public static class A implements J {

    @NeverInline
    public static J createA() {
      return new A();
    }

    @NeverInline
    public void f() {
      System.out.println("In A.f()");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      ((A) A.createA()).f();
    }
  }
}
