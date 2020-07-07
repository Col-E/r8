// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import java.util.List;

public class CfSourceUtils {

  public static CfLabel ensureLabel(List<CfInstruction> instructions) {
    CfInstruction last = getLastInstruction(instructions);
    if (last != null && last.isLabel()) {
      return last.asLabel();
    }
    CfLabel label = new CfLabel();
    instructions.add(label);
    return label;
  }

  private static CfInstruction getLastInstruction(List<CfInstruction> instructions) {
    return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
  }
}
