// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtectedBridgeRemovalTest extends TestBase {

  private static final String A_DESCRIPTOR = "LA;";
  private static final String EXPECTED_OUTPUT = StringUtils.lines("IllegalAccessError", "A.foo()");

  private static List<byte[]> programClassFileData;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void setup() throws Exception {
    programClassFileData =
        ImmutableList.of(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), A_DESCRIPTOR)
                .transform(),
            transformer(A.class).setClassDescriptor(A_DESCRIPTOR).transform(),
            transformer(B.class)
                .replaceClassDescriptorInMethodInstructions(descriptor(A.class), A_DESCRIPTOR)
                .setBridge(B.class.getDeclaredMethod("foo"))
                .setSuper(A_DESCRIPTOR)
                .transform());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(programClassFileData)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(programClassFileData)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class Main {

    public static void main(String[] args) {
      for (A a : new A[] {new A(), new B()}) {
        if (a instanceof B) {
          B b = (B) a;
          b.foo();
        } else {
          try {
            a.foo();
          } catch (IllegalAccessError e) {
            System.out.println("IllegalAccessError");
          }
        }
      }
    }
  }

  @NoVerticalClassMerging
  public static class /*other package.*/ A {

    @NeverInline
    protected void foo() {
      System.out.println("A.foo()");
    }
  }

  public static class B extends A {

    @NeverInline
    @Override
    protected /*bridge*/ void foo() {
      super.foo();
    }
  }
}
