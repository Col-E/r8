// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OutlineOptimizationProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!", "Hello, world!");
  }

  private ExternalArtProfile getArtProfile() {
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .build();
  }

  private void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
    ClassSubject outlineClassSubject =
        inspector.clazz(SyntheticItemsTestUtils.syntheticOutlineClass(Main.class, 0));
    assertThat(outlineClassSubject, isPresent());

    MethodSubject outlineMethodSubject = outlineClassSubject.uniqueMethod();
    assertThat(outlineMethodSubject, isPresent());

    // TODO(b/265729283): Should contain the outline class and method.
    profileInspector
        .assertContainsMethodRule(MethodReferenceUtils.mainMethod(Main.class))
        .assertContainsNoOtherRules();
  }

  public static class Main {

    public static void main(String[] args) {
      greet1();
      greet2();
    }

    static void greet1() {
      hello();
      world();
    }

    static void greet2() {
      hello();
      world();
    }

    public static void hello() {
      System.out.print("Hello");
    }

    public static void world() {
      System.out.println(", world!");
    }
  }
}
