// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.enclosingmethod;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
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
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getProgramClassFileDataWithRewrittenEnclosingMethod())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(
            parameters.isCfRuntime()
                || parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            "null",
            typeName(Main.class))
        .assertSuccessWithOutputLinesIf(
            parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik(),
            "<clinit>",
            typeName(Main.class))
        .assertFailureWithErrorThatThrowsIf(
            parameters.isDexRuntime()
                && parameters
                    .getDexRuntimeVersion()
                    .isInRangeInclusive(Version.V5_1_1, Version.V6_0_1),
            IncompatibleClassChangeError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getProgramClassFileDataWithRewrittenEnclosingMethod())
        .setMinApi(parameters.getApiLevel())
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepAllClassesRule()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/201790364): We remove the method even if keeping all classes due to <clinit> not
        //  being pinned.
        .assertSuccessWithOutputLines("null", "null");
  }

  private byte[] getProgramClassFileDataWithRewrittenEnclosingMethod() throws IOException {
    Path innerClass = ToolHelper.getClassFilesForInnerClasses(Main.class).iterator().next();
    return transformer(innerClass, Reference.classFromBinaryName(binaryName(A.class) + "$1"))
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
