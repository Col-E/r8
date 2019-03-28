// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;

// Actual test parameters for a specific configuration. Currently just the runtime configuration.
public class TestParameters {

  private final TestRuntime runtime;

  public TestParameters(TestRuntime runtime) {
    assert runtime != null;
    this.runtime = runtime;
  }

  // Convenience predicates.
  public boolean isDexRuntime() {
    return runtime.isDex();
  }

  public boolean isCfRuntime() {
    return runtime.isCf();
  }

  // Access to underlying runtime/wrapper.
  public TestRuntime getRuntime() {
    return runtime;
  }

  // Helper function to get the "backend" for a given runtime target.
  public Backend getBackend() {
    return runtime.getBackend();
  }

  @Override
  public String toString() {
    return runtime.toString();
  }
}
