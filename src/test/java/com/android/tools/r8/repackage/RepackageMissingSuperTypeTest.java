// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageMissingSuperTypeTest extends RepackageTestBase {

  private final String[] EXPECTED =
      new String[] {
        "ClassWithSuperCall::foo",
        "MissingSuperType::foo",
        "ClassWithoutSuperCall::foo",
        "MissingSuperType::foo"
      };

  public RepackageMissingSuperTypeTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8WithoutRepackaging() throws Exception {
    runTest(false);
  }

  @Test
  public void testR8() throws Exception {
    runTest(true);
  }

  private void runTest(boolean repackage) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            ClassWithSuperCall.class,
            ClassWithoutSuperCall.class,
            ClassWithoutDefinition.class,
            Main.class)
        .addKeepMainRule(Main.class)
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters)
        .addDontWarn(MissingSuperType.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              if (repackage) {
                assertThat(ClassWithSuperCall.class, isRepackaged(inspector));
                assertThat(ClassWithoutSuperCall.class, isRepackaged(inspector));
              } else {
                assertThat(inspector.clazz(ClassWithSuperCall.class), isPresentAndRenamed());
                assertThat(inspector.clazz(ClassWithoutSuperCall.class), isPresentAndRenamed());
              }
            })
        .addRunClasspathClasses(MissingSuperType.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class MissingSuperType {

    public void foo() {
      System.out.println("MissingSuperType::foo");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class ClassWithSuperCall extends MissingSuperType {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("ClassWithSuperCall::foo");
      super.foo();
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class ClassWithoutSuperCall extends MissingSuperType {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("ClassWithoutSuperCall::foo");
    }
  }

  @NeverClassInline
  public static class ClassWithoutDefinition extends MissingSuperType {}

  public static class Main {

    public static void main(String[] args) {
      new ClassWithSuperCall().foo();
      new ClassWithoutSuperCall().foo();
      new ClassWithoutDefinition().foo();
    }
  }
}
