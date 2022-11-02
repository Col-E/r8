// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingRemoveInterfaceBridgeWithSubTypeTest extends TestBase {

  private final TestParameters parameters;
  private final String newMainDescriptor = "La/Main;";
  private final String newMainTypeName = DescriptorUtils.descriptorToJavaType(newMainDescriptor);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingRemoveInterfaceBridgeWithSubTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class, I.class)
        .addProgramClassFileData(
            transformer(Main.class).setClassDescriptor(newMainDescriptor).transform())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(newMainTypeName)
        .addKeepClassAndMembersRules(I.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addDontObfuscate()
        .run(parameters.getRuntime(), newMainTypeName)
        .assertSuccessWithOutputLines("B::foo")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(A.class);
              assertThat(clazz, isPresent());
              assertTrue(clazz.virtualMethods().isEmpty());
            });
  }

  interface I {

    void foo();
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  public abstract static class A implements I {}

  @NoHorizontalClassMerging
  public static class B extends A {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("B::foo");
    }
  }

  public static class /* a.Main */ Main {

    public static void main(String[] args) {
      callJ(args.length == 0 ? new B() : null);
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void callJ(A a) {
      a.foo();
    }
  }
}
