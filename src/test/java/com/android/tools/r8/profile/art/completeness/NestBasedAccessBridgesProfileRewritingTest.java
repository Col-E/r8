// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.profile.art.completeness.NestBasedAccessBridgesProfileRewritingTest.Main.NestMember;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.AbsentMethodSubject;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestBasedAccessBridgesProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK11));
    testForJvm(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeFalse(parameters.isCfRuntime() && parameters.asCfRuntime().isOlderThan(CfVm.JDK11));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(options -> options.callSiteOptimizationOptions().setEnabled(false))
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "2", "3", "4");
  }

  private List<byte[]> getProgramClassFileData() throws Exception {
    return ImmutableList.of(
        transformer(Main.class).setNest(Main.class, NestMember.class).transform(),
        transformer(NestMember.class)
            .setNest(Main.class, NestMember.class)
            .setPrivate(NestMember.class.getDeclaredConstructor())
            .setPrivate(NestMember.class.getDeclaredField("instanceField"))
            .setPrivate(NestMember.class.getDeclaredField("staticField"))
            .setPrivate(NestMember.class.getDeclaredMethod("instanceMethod"))
            .setPrivate(NestMember.class.getDeclaredMethod("staticMethod"))
            .transform());
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(profileInspector, inspector, false, parameters.canUseNestBasedAccessesWhenDesugaring());
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.canHaveNonReboundConstructorInvoke(),
        parameters.canUseNestBasedAccesses());
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canHaveNonReboundConstructorInvoke,
      boolean canUseNestBasedAccesses)
      throws Exception {
    ClassSubject nestMemberClassSubject = inspector.clazz(NestMember.class);
    assertThat(nestMemberClassSubject, isPresent());

    ClassSubject syntheticConstructorArgumentClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticNestConstructorArgumentClass(
                Reference.classFromClass(NestMember.class)));
    assertThat(
        syntheticConstructorArgumentClassSubject, notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject instanceInitializer = nestMemberClassSubject.init();
    assertThat(instanceInitializer, notIf(isPresent(), canHaveNonReboundConstructorInvoke));

    MethodSubject instanceInitializerWithSyntheticArgumentSubject =
        syntheticConstructorArgumentClassSubject.isPresent()
            ? nestMemberClassSubject.init(syntheticConstructorArgumentClassSubject.getFinalName())
            : new AbsentMethodSubject();
    assertThat(
        instanceInitializerWithSyntheticArgumentSubject,
        notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestInstanceFieldGetterMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestInstanceFieldGetter(
                    NestMember.class.getDeclaredField("instanceField"))
                .getMethodName());
    assertThat(
        syntheticNestInstanceFieldGetterMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestInstanceFieldSetterMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestInstanceFieldSetter(
                    NestMember.class.getDeclaredField("instanceField"))
                .getMethodName());
    assertThat(
        syntheticNestInstanceFieldSetterMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestInstanceMethodAccessorMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestInstanceMethodAccessor(
                    NestMember.class.getDeclaredMethod("instanceMethod"))
                .getMethodName());
    assertThat(
        syntheticNestInstanceMethodAccessorMethodSubject,
        notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestStaticFieldGetterMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestStaticFieldGetter(
                    NestMember.class.getDeclaredField("staticField"))
                .getMethodName());
    assertThat(
        syntheticNestStaticFieldGetterMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestStaticFieldSetterMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestStaticFieldSetter(
                    NestMember.class.getDeclaredField("staticField"))
                .getMethodName());
    assertThat(
        syntheticNestStaticFieldSetterMethodSubject, notIf(isPresent(), canUseNestBasedAccesses));

    MethodSubject syntheticNestStaticMethodAccessorMethodSubject =
        nestMemberClassSubject.uniqueMethodWithOriginalName(
            SyntheticItemsTestUtils.syntheticNestStaticMethodAccessor(
                    NestMember.class.getDeclaredMethod("staticMethod"))
                .getMethodName());
    assertThat(
        syntheticNestStaticMethodAccessorMethodSubject,
        notIf(isPresent(), canUseNestBasedAccesses));

    // Verify the residual profile contains the synthetic nest based access bridges and the
    // synthetic constructor argument class.
    profileInspector
        .assertContainsMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .applyIf(
            !canUseNestBasedAccesses,
            i ->
                i.assertContainsMethodRules(
                        instanceInitializerWithSyntheticArgumentSubject,
                        syntheticNestInstanceFieldGetterMethodSubject,
                        syntheticNestInstanceFieldSetterMethodSubject,
                        syntheticNestInstanceMethodAccessorMethodSubject,
                        syntheticNestStaticFieldGetterMethodSubject,
                        syntheticNestStaticFieldSetterMethodSubject,
                        syntheticNestStaticMethodAccessorMethodSubject)
                    .assertContainsClassRule(syntheticConstructorArgumentClassSubject))
        .assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      NestMember nestMember = new NestMember();
      nestMember.instanceField = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(nestMember.instanceField);
      System.out.println(nestMember.instanceMethod());
      NestMember.staticField = System.currentTimeMillis() > 0 ? 3 : 0;
      System.out.println(NestMember.staticField);
      System.out.println(NestMember.staticMethod());
    }

    static class NestMember {

      /*private*/ int instanceField;
      /*private*/ static int staticField;

      /*private*/ NestMember() {}

      /*private*/ int instanceMethod() {
        return System.currentTimeMillis() > 0 ? 2 : 0;
      }

      /*private*/ static int staticMethod() {
        return System.currentTimeMillis() > 0 ? 4 : 0;
      }
    }
  }
}
