// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelBackportTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelBackportTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // For dex runtimes the `callBackport` is always min-api, because we either:
    // - backport `Integer.toUnsignedString` thus the code is program code
    // - call the implementation in the library where min-api >= 0.
    // For CF we do not desugar the call and thus have to keep it at the api level O.
    Method callBackport = ApiCaller.class.getDeclaredMethod("callBackport", int.class);
    testForR8(parameters.getBackend())
        .addProgramClasses(ApiCaller.class, ApiCallerCaller.class, Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(callBackport).equals(method)) {
                    assertEquals(
                        parameters.isCfRuntime() ? AndroidApiLevel.O : parameters.getApiLevel(),
                        apiLevel);
                  }
                }))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0")
        .inspect(
            inspector -> {
              if (parameters.isCfRuntime()) {
                assertThat(inspector.clazz(ApiCaller.class), isPresent());
                assertThat(inspector.clazz(Main.class), isPresent());
                assertEquals(2, inspector.allClasses().size());
              } else {
                assertThat(inspector.clazz(ApiCaller.class), not(isPresent()));
                assertThat(inspector.clazz(Main.class), isPresent());
                assertEquals(1, inspector.allClasses().size());
              }
            });
  }

  public static class ApiCaller {

    public static String callBackport(int length) {
      return Integer.toUnsignedString(length);
    }
  }

  public static class ApiCallerCaller {

    public static String callCallBackport(int length) {
      return ApiCaller.callBackport(length);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(ApiCallerCaller.callCallBackport(args.length));
    }
  }
}
