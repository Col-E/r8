// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.optimize.info.BitAccessInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;

public class FieldBitAccessAnalysis {

  private final AppView<? extends AppInfoWithSubtyping> appView;

  public FieldBitAccessAnalysis(AppView<? extends AppInfoWithSubtyping> appView) {
    assert appView.enableWholeProgramOptimizations();
    this.appView = appView;
  }

  public void recordFieldAccesses(IRCode code, OptimizationFeedback feedback) {
    if (!code.metadata().mayHaveFieldInstruction()) {
      return;
    }

    for (Instruction instruction : code.instructions()) {
      if (instruction.isFieldInstruction()) {
        FieldInstruction fieldInstruction = instruction.asFieldInstruction();
        DexField field = fieldInstruction.getField();
        if (!field.type.isIntType()) {
          continue;
        }

        DexEncodedField encodedField = appView.appInfo().resolveField(field);
        if (encodedField == null || !encodedField.isProgramField(appView)) {
          continue;
        }

        if (BitAccessInfo.allBitsRead(encodedField.getOptimizationInfo().getReadBits())) {
          continue;
        }

        if (fieldInstruction.isFieldGet()) {
          recordFieldGet(fieldInstruction, encodedField, feedback);
        } else {
          recordFieldPut(fieldInstruction, encodedField, feedback);
        }
      }
    }
  }

  private void recordFieldGet(
      FieldInstruction instruction, DexEncodedField encodedField, OptimizationFeedback feedback) {
    assert instruction.isFieldGet();
    // TODO(b/140540714): Recognize relevant patterns.
    feedback.markFieldHasUnknownAccess(encodedField);
  }

  private void recordFieldPut(
      FieldInstruction instruction, DexEncodedField encodedField, OptimizationFeedback feedback) {
    assert instruction.isFieldPut();
    // TODO(b/140540714): Recognize relevant patterns.
    feedback.markFieldHasUnknownAccess(encodedField);
  }
}
