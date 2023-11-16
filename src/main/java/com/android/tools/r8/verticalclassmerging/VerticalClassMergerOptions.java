// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.utils.InternalOptions;

public class VerticalClassMergerOptions {

  private final InternalOptions options;

  private boolean enabled = true;

  public VerticalClassMergerOptions(InternalOptions options) {
    this.options = options;
  }

  public void disable() {
    setEnabled(false);
  }

  public boolean isDisabled() {
    return !isEnabled();
  }

  public boolean isEnabled() {
    return enabled && options.isOptimizing() && options.isShrinking();
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }
}
