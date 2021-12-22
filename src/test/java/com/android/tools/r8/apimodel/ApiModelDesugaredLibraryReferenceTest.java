// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelDesugaredLibraryReferenceTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public ApiModelDesugaredLibraryReferenceTest(
      TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.parameters = parameters;
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
  }

  @Test
  public void testClockR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    Method printZone = DesugaredLibUser.class.getDeclaredMethod("printZone");
    Method main = Executor.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClasses(Executor.class, DesugaredLibUser.class)
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addKeepMainRule(Executor.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(
            ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                (reference, apiLevel) -> {
                  if (reference.equals(Reference.methodFromMethod(printZone))) {
                    assertEquals(parameters.getApiLevel(), apiLevel);
                  }
                }))
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutputLines("Z")
        .inspect(
            inspector ->
                ApiModelingTestHelper.verifyThat(inspector, parameters, printZone)
                    .inlinedInto(main));
  }

  static class Executor {

    public static void main(String[] args) {
      DesugaredLibUser.printZone();
    }
  }

  static class DesugaredLibUser {

    public static void printZone() {
      System.out.println(Clock.systemUTC().getZone());
    }
  }
}
