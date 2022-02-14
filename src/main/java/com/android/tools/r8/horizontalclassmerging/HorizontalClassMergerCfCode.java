// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexType;
import java.util.List;

/**
 * Similar to CfCode, but with a marker that makes it possible to recognize this is synthesized by
 * the horizontal class merger.
 */
public class HorizontalClassMergerCfCode extends CfCode {

  HorizontalClassMergerCfCode(
      DexType originalHolder, int maxStack, int maxLocals, List<CfInstruction> instructions) {
    super(originalHolder, maxStack, maxLocals, instructions);
  }

  @Override
  public boolean isHorizontalClassMergingCode() {
    return true;
  }
}
