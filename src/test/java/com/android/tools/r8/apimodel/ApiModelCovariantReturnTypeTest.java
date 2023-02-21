// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelCovariantReturnTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelCovariantReturnTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method main = Main.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(main).equals(method)) {
                    assertEquals(
                        parameters.isCfRuntime()
                            ? AndroidApiLevel.P
                            : parameters.getApiLevel().max(AndroidApiLevel.P),
                        apiLevel);
                  }
                }))
        .compile();
  }

  public static class Main {

    public static void main(String[] args) {
      ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
      KeySetView<String, String> strings = map.keySet();
    }
  }
}
