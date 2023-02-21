// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticFieldClassInitMemberRebindingTest extends TestBase {

  private static final String EXPECTED = "World!";
  private static final String R8_EXPECTED = "Hello World!";

  private final String NEW_A_DESCRIPTOR = "Lfoo/A;";
  private final String NEW_B_DESCRIPTOR = "Lfoo/B;";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClassFileData(
            getMainWithRewrittenDescriptors(), getAWithPackageFoo(), getBWithRewrittenDescriptors())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            getMainWithRewrittenDescriptors(), getAWithPackageFoo(), getBWithRewrittenDescriptors())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/220668540): R8 should not change class loading semantics.
        .assertSuccessWithOutputLines(R8_EXPECTED);
  }

  private byte[] getAWithPackageFoo() throws Exception {
    return transformer(A.class).setClassDescriptor(NEW_A_DESCRIPTOR).transform();
  }

  private byte[] getBWithRewrittenDescriptors() throws Exception {
    return transformer(B.class)
        .setClassDescriptor(NEW_B_DESCRIPTOR)
        .setSuper(NEW_A_DESCRIPTOR)
        .replaceClassDescriptorInMethodInstructions(descriptor(A.class), NEW_A_DESCRIPTOR)
        .transform();
  }

  private byte[] getMainWithRewrittenDescriptors() throws Exception {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(descriptor(B.class), NEW_B_DESCRIPTOR)
        .transform();
  }

  static class /* foo. */ A {

    @NeverInline
    public static void foo() {
      System.out.println("World!");
    }
  }

  public static class /* foo */ B extends A {

    static {
      System.out.print("Hello ");
    }
  }

  public static class Main {

    @NeverInline
    public static void test() {
      B.foo(); // Resolves to A.foo(), hence does not trigger B.<clinit>().
    }

    public static void main(String[] args) {
      test();
      if (System.currentTimeMillis() == 0) {
        // Needed to ensure we do not remove B.<clinit>() in first round of treeshaking before
        // running member rebinding analysis.
        new B();
      }
    }
  }
}
