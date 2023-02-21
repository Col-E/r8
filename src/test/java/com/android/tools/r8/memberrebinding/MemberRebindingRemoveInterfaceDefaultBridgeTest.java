// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MemberRebindingRemoveInterfaceDefaultBridgeTest extends TestBase {

  private final TestParameters parameters;
  private final String newMainDescriptor = "La/Main;";
  private final String newMainTypeName = DescriptorUtils.descriptorToJavaType(newMainDescriptor);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingRemoveInterfaceDefaultBridgeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, I.class, J.class)
        .addProgramClassFileData(
            transformer(Main.class).setClassDescriptor(newMainDescriptor).transform())
        .setMinApi(parameters)
        .addKeepMainRule(newMainTypeName)
        .addKeepClassAndMembersRules(I.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), newMainTypeName)
        .assertSuccessWithOutputLines("I::foo")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(J.class);
              assertThat(clazz, isPresent());
              assertTrue(clazz.allMethods().isEmpty());
              if (!parameters.canUseDefaultAndStaticInterfaceMethods()) {
                ClassSubject classSubject = clazz.toCompanionClass();
                assertThat(classSubject, isAbsent());
              }
            });
  }

  interface I {

    @NeverInline
    default void foo() {
      System.out.println("I::foo");
    }
  }

  @NoVerticalClassMerging
  public interface J extends I {}

  @NoHorizontalClassMerging
  public static class A implements J {}

  public static class /* a.Main */ Main {

    public static void main(String[] args) {
      callJ(args.length == 0 ? new A() : null);
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void callJ(J j) {
      j.foo();
    }
  }
}
