// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.controlflow;

import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.LongInterval;

public class SwitchCaseAnalyzer {

  private static final SwitchCaseAnalyzer INSTANCE = new SwitchCaseAnalyzer();

  public SwitchCaseAnalyzer() {}

  public static SwitchCaseAnalyzer getInstance() {
    return INSTANCE;
  }

  public boolean switchCaseIsAlwaysHit(Switch theSwitch, int index) {
    Value switchValue = theSwitch.value();
    if (theSwitch.isIntSwitch()) {
      LongInterval valueRange = switchValue.getValueRange();
      return valueRange != null
          && valueRange.isSingleValue()
          && valueRange.containsValue(theSwitch.asIntSwitch().getKey(index));
    }

    assert theSwitch.isStringSwitch();

    Value rootSwitchValue = switchValue.getAliasedValue();
    DexString key = theSwitch.asStringSwitch().getKey(index);
    return rootSwitchValue.isDefinedByInstructionSatisfying(Instruction::isConstString)
        && key == rootSwitchValue.definition.asConstString().getValue();
  }

  public boolean switchCaseIsUnreachable(Switch theSwitch, int index) {
    Value switchValue = theSwitch.value();
    if (theSwitch.isIntSwitch()) {
      return switchValue.hasValueRange()
          && !switchValue.getValueRange().containsValue(theSwitch.asIntSwitch().getKey(index));
    }

    assert theSwitch.isStringSwitch();

    Value rootSwitchValue = switchValue.getAliasedValue();
    DexString key = theSwitch.asStringSwitch().getKey(index);
    return rootSwitchValue.isDefinedByInstructionSatisfying(Instruction::isConstString)
        && key != rootSwitchValue.definition.asConstString().getValue();
  }
}
