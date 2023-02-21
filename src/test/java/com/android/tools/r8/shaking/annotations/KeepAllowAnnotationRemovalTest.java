// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.Keep;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepAllowAnnotationRemovalTest extends TestBase {

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
        .addProgramClasses(Main.class, Keep.class)
        .addKeepRules(
            "-keep,allowannotationremoval @" + Keep.class.getTypeName() + " class * {",
            "  public static void main(java.lang.String[]);",
            "}")
        .addKeepRuntimeInvisibleAnnotations()
        .enableProguardTestOptions()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(Main.class);
              assertThat(classSubject, isPresent());
              assertThat(
                  classSubject.annotation(Keep.class),
                  onlyIf(enableCompatibilityMode, isPresent()));
            })
        .run(parameters.getRuntime(), Main.class)
        .apply(
            result ->
                result.assertSuccessWithOutputLines(
                    result.inspector().clazz(Keep.class).getFinalName()));
  }

  @Keep
  static class Main {
    public static void main(String[] args) {
      System.out.println(Keep.class.getName());
    }
  }
}
