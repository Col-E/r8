// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelFieldSuperTypeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelFieldSuperTypeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    Method main = Main.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(AccessibilityService.class),
                    "Landroid/accessibilityservice/AccessibilityService;")
                .transform())
        .addLibraryFiles(
            ToolHelper.getFirstSupportedAndroidJar(
                parameters.isCfRuntime() ? AndroidApiLevel.B : parameters.getApiLevel()))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(main).equals(method)) {
                    assertEquals(
                        parameters.isCfRuntime()
                            ? AndroidApiLevel.E
                            : parameters.getApiLevel().max(AndroidApiLevel.E),
                        apiLevel);
                  }
                }))
        .compile();
  }

  /* Only here to get the test to compile */
  public static class AccessibilityService {

    int START_CONTINUATION_MASK = 42;
  }

  public static class Main {

    public static void main(String[] args) {
      // START_CONTINUATION_MASK is inherited from android/app/Service which was introduced at
      // AndroidApiLevel.E.
      System.out.println(
          new /* android.accessibilityservice */ AccessibilityService().START_CONTINUATION_MASK);
    }
  }
}
