// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.controlflow;

import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.Value;

public class SwitchCaseAnalyzer {

  private static final SwitchCaseAnalyzer INSTANCE = new SwitchCaseAnalyzer();

  public SwitchCaseAnalyzer() {}

  public static SwitchCaseAnalyzer getInstance() {
    return INSTANCE;
  }

  public boolean switchCaseIsUnreachable(IntSwitch theSwitch, int index) {
    Value switchValue = theSwitch.value();
    return switchValue.hasValueRange()
        && !switchValue.getValueRange().containsValue(theSwitch.getKey(index));
  }
}
