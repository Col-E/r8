// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.enclosingmethod;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidEnclosingMethodAttributeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvalidEnclosingMethodAttributeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getProgramClassFileDataWithRewrittenEnclosingMethod())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null", typeName(Main.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addProgramClassFileData(getProgramClassFileDataWithRewrittenEnclosingMethod())
        .setMinApi(parameters)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepAllClassesRule()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null", typeName(Main.class));
  }

  private byte[] getProgramClassFileDataWithRewrittenEnclosingMethod() throws IOException {
    Path innerClass = ToolHelper.getClassFilesForInnerClasses(Main.class).iterator().next();
    return transformer(innerClass, Reference.classFromBinaryName(binaryName(Main.class) + "$1"))
        .rewriteEnclosingMethod(binaryName(Main.class), "<clinit>", "()V")
        .transform();
  }

  public static class Main {

    public static Object a =
        new Object() {
          void foo() {
            System.out.println("Hello World!");
          }
        };

    public static void main(String[] args) {
      Class<? extends Object> aClass = a.getClass();
      Method enclosingMethod = aClass.getEnclosingMethod();
      if (enclosingMethod == null) {
        System.out.println("null");
      } else {
        System.out.println(enclosingMethod.getName());
      }
      Class<?> enclosingClass = aClass.getEnclosingClass();
      if (enclosingClass == null) {
        System.out.println("null");
      } else {
        System.out.println(enclosingClass.getName());
      }
    }
  }
}
