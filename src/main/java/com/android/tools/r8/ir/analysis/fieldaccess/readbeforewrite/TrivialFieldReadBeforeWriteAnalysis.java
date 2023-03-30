// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess.readbeforewrite;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;

class TrivialFieldReadBeforeWriteAnalysis extends FieldReadBeforeWriteAnalysis {

  @Override
  public boolean isInstanceFieldMaybeReadBeforeInstruction(
      Value receiver, DexEncodedField field, Instruction instruction) {
    return true;
  }

  @Override
  public boolean isStaticFieldMaybeReadBeforeInstruction(
      DexEncodedField field, Instruction instruction) {
    return true;
  }
}
