// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.utils.AndroidApiLevel.LATEST;
import static com.android.tools.r8.utils.AndroidApiLevel.R;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelVirtualDispatchLinkInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelVirtualDispatchLinkInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction; is introduced at api
    // level 21 and on api level 30 it implements android.os.Parcelable.
    // android.os.Parcelable.describeContents() has api level B. Since the invoke goes through the
    // link established at level 30, the api reference level should be 30.
    Method main = Main.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(AccessibilityNodeInfo$AccessibilityAction.class),
                    "Landroid/view/accessibility/AccessibilityNodeInfo$AccessibilityAction;")
                .transform())
        .addLibraryFiles(ToolHelper.getAndroidJar(LATEST))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        // We are testing that we do not inline/merge higher api-levels
        .apply(ApiModelingTestHelper::disableOutliningAndStubbing)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(main).equals(method)) {
                    assertTrue(apiLevel.isGreaterThanOrEqualTo(R));
                  }
                }))
        .compile();
  }

  public static class AccessibilityNodeInfo$AccessibilityAction {

    private int i;

    public AccessibilityNodeInfo$AccessibilityAction(int i, CharSequence sequence) {
      this.i = i;
    }

    int describeContents() {
      return i;
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new AccessibilityNodeInfo$AccessibilityAction(42, "foobar").describeContents();
    }
  }
}
