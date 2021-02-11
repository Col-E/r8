// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageMissingSuperTypeTest extends RepackageTestBase {

  public RepackageMissingSuperTypeTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testR8WithoutRepackaging() throws Exception {
    runTest(false)
        .assertSuccessWithOutputLines(
            "ClassWithSuperCall::foo",
            "MissingSuperType::foo",
            "ClassWithoutSuperCall::foo",
            "MissingSuperType::foo");
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult r8TestRunResult = runTest(true);
    if (parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isOlderThanOrEqual(Version.V4_4_4)) {
      r8TestRunResult.assertFailureWithErrorThatThrows(NoClassDefFoundError.class);
    } else {
      r8TestRunResult.assertFailureWithErrorThatThrows(IllegalAccessError.class);
    }
  }

  private R8TestRunResult runTest(boolean repackage) throws Exception {
    return testForR8(parameters.getBackend())
        .addProgramClasses(
            ClassWithSuperCall.class,
            ClassWithoutSuperCall.class,
            ClassWithoutDefinition.class,
            Main.class)
        .addKeepMainRule(Main.class)
        .applyIf(repackage, this::configureRepackaging)
        .setMinApi(parameters.getApiLevel())
        .addDontWarn(MissingSuperType.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              // TODO(b/179889105): These should probably not be repackaged.
              assertThat(ClassWithSuperCall.class, isRepackagedIf(inspector, repackage));
              assertThat(ClassWithoutSuperCall.class, isRepackagedIf(inspector, repackage));
            })
        .addRunClasspathClasses(MissingSuperType.class)
        .run(parameters.getRuntime(), Main.class);
  }

  static class MissingSuperType {

    public void foo() {
      System.out.println("MissingSuperType::foo");
    }
  }

  @NeverClassInline
  public static class ClassWithSuperCall extends MissingSuperType {

    @Override
    @NeverInline
    public void foo() {
      System.out.println("ClassWithSuperCall::foo");
      super.foo();
    }
  }

  @NeverClassInline
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
