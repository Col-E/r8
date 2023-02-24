// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.addTracedApiReferenceLevelCallBack;
import static com.android.tools.r8.utils.AndroidApiLevel.LATEST;
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
public class ApiModelVirtualDispatchInterfaceOverrideTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApiModelVirtualDispatchInterfaceOverrideTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test()
  public void testR8() throws Exception {
    // android/content/res/AssetManager exists from AndroidApiLevel.B but in 21 it implements
    // java/lang/AutoCloseable. However, close()V has always been defined, so the api level should
    // always be min api.
    Method main =
        ApiModelVirtualDispatchSuperTypeTest.Main.class.getDeclaredMethod("main", String[].class);
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(AssetManager.class), "Landroid/content/res/AssetManager;")
                .transform())
        .addLibraryFiles(ToolHelper.getAndroidJar(LATEST))
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .apply(
            addTracedApiReferenceLevelCallBack(
                (method, apiLevel) -> {
                  if (Reference.methodFromMethod(main).equals(method)) {
                    assertEquals(
                        parameters.isCfRuntime() ? AndroidApiLevel.B : parameters.getApiLevel(),
                        apiLevel);
                  }
                }))
        .compile();
  }

  public static class AssetManager {

    public void close() {}
  }

  public static class Main {

    public static void main(String[] args) {
      AssetManager manager = null;
      manager.close();
    }
  }
}
