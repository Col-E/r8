// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSpecialToVirtualMethodProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClassFileData(getTransformedMain())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!", "Hello, world!");
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!", "Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getTransformedMain())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!", "Hello, world!");
  }

  private byte[] getTransformedMain() throws IOException {
    BooleanBox anyRewritten = new BooleanBox();
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (opcode == Opcodes.INVOKEVIRTUAL) {
                assertEquals("m", name);
                if (anyRewritten.isFalse()) {
                  visitor.visitMethodInsn(
                      Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
                  anyRewritten.set();
                  return;
                }
              }
              visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(Main.class.getDeclaredMethod("m")))
        .build();
  }

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());

    MethodSubject mMethodSubject = mainClassSubject.uniqueMethodWithOriginalName("m");
    assertThat(mMethodSubject, isPresent());

    MethodSubject mMovedMethodSubject =
        mainClassSubject.method(
            SyntheticItemsTestUtils.syntheticInvokeSpecialMethod(
                Main.class.getDeclaredMethod("m")));
    assertThat(mMovedMethodSubject, isPresent());
      assertNotEquals(
          mMethodSubject.getProgramMethod().getName(),
          mMovedMethodSubject.getProgramMethod().getName());

    // Verify residual profile contains private synthetic method when present.
    profileInspector
        .assertContainsMethodRules(mMethodSubject, mMovedMethodSubject)
        .assertContainsNoOtherRules();
  }

  public static class Main {

    public static void main(String[] args) {
      Main main = new Main();
      main.m(); // transformed to invoke-special
      main.m(); // remains an invoke-virtual (so that Main.m() survives shaking)
    }

    public void m() {
      System.out.println("Hello, world!");
    }
  }
}
