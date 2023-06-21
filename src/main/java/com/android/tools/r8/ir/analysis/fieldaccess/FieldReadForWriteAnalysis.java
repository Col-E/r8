// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata.Builder;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;

public class FieldReadForWriteAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  public FieldReadForWriteAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void recordFieldAccess(
      FieldInstruction instruction,
      ProgramField field,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder) {
    if (instruction.isFieldPut()) {
      return;
    }

    FieldGet fieldGet = instruction.asFieldGet();
    if (isValueOnlyUsedToWriteField(fieldGet.outValue(), field)) {
      bytecodeMetadataProviderBuilder.addMetadata(instruction, Builder::setIsReadForWrite);
    }
  }

  private boolean isValueOnlyUsedToWriteField(Value value, ProgramField field) {
    WorkList<Instruction> users = WorkList.newIdentityWorkList(value.uniqueUsers());
    if (!enqueueUsersForAnalysis(value, users)) {
      return false;
    }
    boolean foundWrite = false;
    while (users.hasNext()) {
      Instruction user = users.next();
      if (user.isArithmeticBinop() || user.isLogicalBinop() || user.isUnop()) {
        if (enqueueUsersForAnalysis(user.outValue(), users)) {
          // OK.
          continue;
        }
      } else if (user.isFieldPut()) {
        FieldPut fieldPut = user.asFieldPut();
        DexField writtenFieldReference = fieldPut.getField();
        if (writtenFieldReference.match(field.getReference())
            && fieldPut.isStaticPut() == field.getAccessFlags().isStatic()) {
          ProgramField writtenField =
              appView.appInfo().resolveField(writtenFieldReference).getSingleProgramField();
          if (writtenField != null && writtenField.isStructurallyEqualTo(field)) {
            // OK.
            foundWrite = true;
            continue;
          }
        }
      }
      return false;
    }
    return foundWrite;
  }

  private boolean enqueueUsersForAnalysis(Value value, WorkList<Instruction> users) {
    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return false;
    }
    users.addIfNotSeen(value.uniqueUsers());
    return true;
  }
}
