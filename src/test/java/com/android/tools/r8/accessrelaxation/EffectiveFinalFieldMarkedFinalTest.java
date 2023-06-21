// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isFinal;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoRedundantFieldLoadElimination;
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
public class EffectiveFinalFieldMarkedFinalTest extends TestBase {

  @Parameter(0)
  public boolean allowAccessModification;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, allowaccessmodification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .allowAccessModification(allowAccessModification)
        .enableNoRedundantFieldLoadEliminationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());
              assertThat(
                  mainClassSubject.uniqueFieldWithOriginalName("instanceField"), isPresent());
              assertThat(
                  mainClassSubject.uniqueFieldWithOriginalName("instanceField"),
                  onlyIf(
                      allowAccessModification || parameters.isAccessModificationEnabledByDefault(),
                      isFinal()));
              assertThat(mainClassSubject.uniqueFieldWithOriginalName("staticField"), isPresent());
              assertThat(
                  mainClassSubject.uniqueFieldWithOriginalName("staticField"),
                  onlyIf(
                      allowAccessModification || parameters.isAccessModificationEnabledByDefault(),
                      isFinal()));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    static String staticField = System.currentTimeMillis() > 0 ? "Hello" : null;

    @NoRedundantFieldLoadElimination
    String instanceField = System.currentTimeMillis() > 0 ? ", world!" : null;

    public static void main(String[] args) {
      System.out.print(staticField);
      System.out.println(new Main().instanceField);
    }
  }
}
