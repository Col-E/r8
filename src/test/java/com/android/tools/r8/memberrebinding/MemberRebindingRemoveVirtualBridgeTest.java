// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isInstanceInitializer;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.NoInliningOfDefaultInitializer;
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
public class MemberRebindingRemoveVirtualBridgeTest extends TestBase {

  private final TestParameters parameters;
  private final String newMainDescriptor = "La/Main;";
  private final String newMainTypeName = DescriptorUtils.descriptorToJavaType(newMainDescriptor);

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingRemoveVirtualBridgeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .addProgramClassFileData(
            transformer(Main.class).setClassDescriptor(newMainDescriptor).transform())
        .setMinApi(parameters)
        .addKeepMainRule(newMainTypeName)
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoInliningOfDefaultInitializerAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .run(parameters.getRuntime(), newMainTypeName)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(B.class);
              assertThat(clazz, isPresent());
              if (parameters.canHaveNonReboundConstructorInvoke()) {
                assertEquals(0, clazz.allMethods().size());
              } else {
                assertEquals(1, clazz.allMethods().size());
                assertThat(clazz.allMethods().get(0), isInstanceInitializer());
              }
            });
  }

  @NoAccessModification
  @NoVerticalClassMerging
  static class A {

    @NeverInline
    public void foo() {
      System.out.println("Hello World!");
    }
  }

  @NeverClassInline
  @NoInliningOfDefaultInitializer
  public static class B extends A {}

  public static class /* a.Main */ Main {

    public static void main(String[] args) {
      new B().foo();
    }
  }
}
