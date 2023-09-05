// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.LogicalBinop;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.BitAccessInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;

public class FieldBitAccessAnalysis {

  public void recordFieldAccess(
      FieldInstruction instruction, DexEncodedField field, OptimizationFeedback feedback) {
    if (!field.getReference().type.isIntType()) {
      return;
    }

    if (BitAccessInfo.allBitsRead(field.getOptimizationInfo().getReadBits())) {
      return;
    }

    if (instruction.isFieldGet()) {
      feedback.markFieldBitsRead(field, computeBitsRead(instruction, field));
    }
  }

  private int computeBitsRead(FieldInstruction instruction, DexEncodedField encodedField) {
    Value outValue = instruction.outValue();
    if (outValue.numberOfPhiUsers() > 0) {
      // No need to track aliases, just give up.
      return BitAccessInfo.getAllBitsReadValue();
    }

    int bitsRead = BitAccessInfo.getNoBitsReadValue();
    for (Instruction user : outValue.uniqueUsers()) {
      if (isOnlyUsedToUpdateFieldValue(user, encodedField)) {
        continue;
      }
      if (user.isAnd()) {
        And andInstruction = user.asAnd();
        Value other =
            andInstruction
                .inValues()
                .get(1 - andInstruction.inValues().indexOf(outValue))
                .getAliasedValue();
        if (other.isPhi() || !other.definition.isConstNumber()) {
          // Could potentially read all bits, give up.
          return BitAccessInfo.getAllBitsReadValue();
        }
        bitsRead |= other.definition.asConstNumber().getIntValue();
      } else {
        // Unknown usage, give up.
        return BitAccessInfo.getAllBitsReadValue();
      }
    }
    return bitsRead;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isOnlyUsedToUpdateFieldValue(Instruction user, DexEncodedField encodedField) {
    if (user.isLogicalBinop()) {
      LogicalBinop binop = user.asLogicalBinop();
      Value outValue = binop.outValue();
      if (outValue.numberOfPhiUsers() > 0) {
        // Not tracking aliased values, give up.
        return false;
      }
      for (Instruction indirectUser : outValue.uniqueUsers()) {
        if (!isOnlyUsedToUpdateFieldValue(indirectUser, encodedField)) {
          return false;
        }
      }
      return true;
    }
    if (user.isFieldPut()) {
      FieldInstruction fieldInstruction = user.asFieldInstruction();
      if (fieldInstruction.getField() == encodedField.getReference()) {
        return true;
      }
    }
    return false;
  }
}
