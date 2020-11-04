// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;

// Actual test parameters for a specific configuration. Currently just the runtime configuration.
public class TestParameters {

  private final TestRuntime runtime;
  private final AndroidApiLevel apiLevel;

  public TestParameters(TestRuntime runtime) {
    this(runtime, null);
  }

  public TestParameters(TestRuntime runtime, AndroidApiLevel apiLevel) {
    assert runtime != null;
    this.runtime = runtime;
    this.apiLevel = apiLevel;
  }

  public boolean canUseDefaultAndStaticInterfaceMethods() {
    assert isCfRuntime() || isDexRuntime();
    return isCfRuntime()
        || getApiLevel()
            .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport());
  }

  // Convenience predicates.
  public boolean isDexRuntime() {
    return runtime.isDex();
  }

  public boolean isCfRuntime() {
    return runtime.isCf();
  }

  public boolean isCfRuntime(CfVm vm) {
    return runtime.isCf() && runtime.asCf().getVm() == vm;
  }

  public boolean isNoneRuntime() {
    return runtime == NoneRuntime.getInstance();
  }

  public AndroidApiLevel getApiLevel() {
    if (runtime.isDex() && apiLevel == null) {
      throw new RuntimeException(
          "Use of getApiLevel without configured API levels for TestParametersCollection.");
    }
    return apiLevel;
  }

  // Access to underlying runtime/wrapper.
  public TestRuntime getRuntime() {
    return runtime;
  }

  public boolean useRuntimeAsNoneRuntime() {
    return isNoneRuntime() || (runtime != null && runtime.equals(TestRuntime.getCheckedInJdk9()));
  }

  // Helper function to get the "backend" for a given runtime target.
  public Backend getBackend() {
    return runtime.getBackend();
  }

  @Override
  public String toString() {
    if (apiLevel != null) {
      return runtime.toString() + ", api:" + apiLevel.getLevel();
    }
    return runtime.toString();
  }

  public void assertNoneRuntime() {
    assertEquals(NoneRuntime.getInstance(), runtime);
  }

  public DexVm.Version getDexRuntimeVersion() {
    assertTrue(isDexRuntime());
    return getRuntime().asDex().getVm().getVersion();
  }
}
