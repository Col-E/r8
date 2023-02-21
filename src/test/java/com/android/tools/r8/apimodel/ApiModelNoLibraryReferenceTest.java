// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.DescriptorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelNoLibraryReferenceTest extends TestBase {

  private static final String API_TYPE_NAME = "android.view.accessibility.AccessibilityEvent";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    MethodReference main =
        Reference.methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(AccessibilityEvent.class),
                    DescriptorUtils.javaTypeToDescriptor(API_TYPE_NAME))
                .transform())
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .setMinApi(parameters)
        .addDontWarn(API_TYPE_NAME)
        .addKeepMainRule(Main.class)
        .addAndroidBuildVersion()
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(
            ApiModelingTestHelper.addTracedApiReferenceLevelCallBack(
                (methodReference, apiLevel) -> {
                  if (methodReference.equals(main)) {
                    // The library passed to the compilation do not define the reference thus the
                    // api level assigned is unknown.
                    assertNull(apiLevel);
                  }
                }))
        .compile();
  }

  /* Only here to get the test to compile */
  public static class AccessibilityEvent {
    public AccessibilityEvent() {}
  }

  public static class Main {

    public static void main(String[] args) {
      if (AndroidBuildVersion.VERSION >= 4) {
        // Will be rewritten to android.view.accessibility.AccessibilityEvent.
        new AccessibilityEvent();
      }
    }
  }
}
