// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MovedPrivateInterfaceMethodProfileRewritingTest extends TestBase {

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
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private byte[] getTransformedInterface() throws Exception {
    return transformer(I.class)
        .setPrivate(I.class.getDeclaredMethod("m"))
        .transformMethodInsnInMethod(
            "bridge",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals(Opcodes.INVOKEINTERFACE, opcode);
              visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(I.class.getDeclaredMethod("m")))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring());
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canUseDefaultAndStaticInterfaceMethods)
      throws Exception {
    if (canUseDefaultAndStaticInterfaceMethods) {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      MethodSubject privateInterfaceMethodSubject = iClassSubject.uniqueMethodWithOriginalName("m");
      assertThat(privateInterfaceMethodSubject, isPresent());

      profileInspector
          .assertContainsMethodRule(privateInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    } else {
      ClassSubject companionClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(I.class));
      assertThat(companionClassSubject, isPresent());

      MethodSubject privateInterfaceMethodSubject =
          companionClassSubject.uniqueMethodWithOriginalName(
              SyntheticItemsTestUtils.syntheticPrivateInterfaceMethodAsCompanionMethod(
                      I.class.getDeclaredMethod("m"))
                  .getMethodName());
      assertThat(privateInterfaceMethodSubject, isPresent());

      profileInspector
          .assertContainsClassRule(companionClassSubject)
          .assertContainsMethodRule(privateInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    }
  }

  static class Main {

    public static void main(String[] args) {
      new A().bridge();
    }
  }

  @NoVerticalClassMerging
  interface I {

    default void bridge() {
      m(); // invoke-special
    }

    /*private*/ default void m() {
      System.out.println("Hello, world!");
    }
  }

  static class A implements I {}
}
