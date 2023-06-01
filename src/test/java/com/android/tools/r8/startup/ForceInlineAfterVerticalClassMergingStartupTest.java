// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.startup.profile.ExternalStartupClass;
import com.android.tools.r8.startup.profile.ExternalStartupItem;
import com.android.tools.r8.startup.profile.ExternalStartupMethod;
import com.android.tools.r8.startup.utils.StartupTestingUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/284334258. */
@RunWith(Parameterized.class)
public class ForceInlineAfterVerticalClassMergingStartupTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    // Create a startup profile with classes A and B to allow that A is merged into B. The method
    // B.<init>() is marked as a startup method, but A.<init>() is not, despite being called from
    // B.<init>().
    Collection<ExternalStartupItem> startupProfile =
        ImmutableList.of(
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(A.class))
                .build(),
            ExternalStartupClass.builder()
                .setClassReference(Reference.classFromClass(B.class))
                .build(),
            ExternalStartupMethod.builder()
                .setMethodReference(MethodReferenceUtils.instanceConstructor(B.class))
                .build());

    // TODO(b/284334258): Test should always succeed with "B".
    R8TestCompileResult compileResult;
    try {
      compileResult =
          testForR8(parameters.getBackend())
              .addInnerClasses(getClass())
              .addKeepMainRule(Main.class)
              .addVerticallyMergedClassesInspector(
                  inspector -> inspector.assertMergedIntoSubtype(A.class))
              .apply(
                  testBuilder -> StartupTestingUtils.addStartupProfile(testBuilder, startupProfile))
              .setMinApi(parameters)
              .compile();
    } catch (CompilationFailedException e) {
      assertTrue(parameters.isCfRuntime());
      return;
    }

    assertTrue(parameters.isDexRuntime());
    compileResult
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            parameters.getDexRuntimeVersion().isEqualToOneOf(Version.V5_1_1, Version.V6_0_1),
            runResult -> runResult.assertSuccessWithOutputLines("B"),
            runResult -> runResult.assertFailureWithErrorThatThrows(VerifyError.class));
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new B());
    }
  }

  static class A {}

  static class B extends A {

    @Override
    public String toString() {
      return "B";
    }
  }
}
