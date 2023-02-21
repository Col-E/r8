// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.containsThrow;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepUnusedReturnValue;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepDisallowAnnotationRemovalAllowOptimizationTest extends TestBase {

  @Parameter(0)
  public boolean enableCompatibilityMode;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, compat: {0}")
  public static List<Object[]> parameters() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(parameters.getBackend(), enableCompatibilityMode)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(NeverInline.class)
        .addKeepRules(
            "-keepclassmembers,allowobfuscation,allowoptimization,allowshrinking class * {",
            "  static java.lang.Object getNonNull();",
            "}")
        .addKeepRuntimeInvisibleAnnotations()
        // In compatibility mode the rule above is a no-op.
        .allowUnusedProguardConfigurationRules(enableCompatibilityMode)
        .enableInliningAnnotations()
        .enableKeepUnusedReturnValueAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(Main.class);
              assertThat(classSubject, isPresent());

              // The annotation on getNonNull() is kept meanwhile it is subject to other
              // optimizations.
              MethodSubject getNonNullSubject =
                  classSubject.uniqueMethodWithOriginalName("getNonNull");
              assertThat(getNonNullSubject, isPresentAndRenamed());
              assertThat(getNonNullSubject.annotation(NeverInline.class), isPresent());

              // Check that the code has been optimized using the fact that getNonNull() returns a
              // non-null value.
              assertThat(classSubject.uniqueMethodWithOriginalName("dead"), isAbsent());
              assertThat(classSubject.mainMethod(), not(containsThrow()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("getNonNull()");
  }

  static class Main {
    public static void main(String[] args) {
      Object o = getNonNull();
      if (o == null) {
        dead();
      }
    }

    @KeepUnusedReturnValue
    @NeverInline
    static Object getNonNull() {
      System.out.println("getNonNull()");
      return new Object();
    }

    @NeverInline
    static void dead() {
      throw new RuntimeException();
    }
  }
}
