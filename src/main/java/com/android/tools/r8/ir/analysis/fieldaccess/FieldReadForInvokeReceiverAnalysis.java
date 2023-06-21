// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldaccess;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Set;

public class FieldReadForInvokeReceiverAnalysis {

  private final AppView<AppInfoWithLiveness> appView;

  public FieldReadForInvokeReceiverAnalysis(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void recordFieldAccess(
      FieldInstruction instruction,
      ProgramField field,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder,
      ProgramMethod context) {
    if (!instruction.isStaticGet()) {
      return;
    }

    StaticGet staticGet = instruction.asStaticGet();
    Set<DexMethod> methods = getMethods(staticGet.outValue(), context);
    if (methods == null || methods.isEmpty()) {
      return;
    }

    bytecodeMetadataProviderBuilder.addMetadata(
        instruction, builder -> builder.setIsReadForInvokeReceiver(methods));
  }

  private Set<DexMethod> getMethods(Value value, ProgramMethod context) {
    WorkList<Instruction> users = WorkList.newIdentityWorkList();
    if (!enqueueUsersForAnalysis(value, users)) {
      return null;
    }
    Set<DexMethod> methods = Sets.newIdentityHashSet();
    while (users.hasNext()) {
      Instruction user = users.next();
      if (user.isAssume()) {
        if (enqueueUsersForAnalysis(user.outValue(), users)) {
          // OK.
          continue;
        }
      } else if (user.isInvokeMethodWithReceiver()) {
        InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
        for (int argumentIndex = 1; argumentIndex < invoke.arguments().size(); argumentIndex++) {
          if (invoke.getArgument(argumentIndex).getAliasedValue() == value) {
            return null;
          }
        }
        assert invoke.getReceiver().getAliasedValue() == value;

        ProgramMethod singleTarget = invoke.lookupSingleProgramTarget(appView, context);
        if (singleTarget == null) {
          return null;
        }

        methods.add(singleTarget.getReference());
        continue;
      }
      return null;
    }
    return methods;
  }

  private boolean enqueueUsersForAnalysis(Value value, WorkList<Instruction> users) {
    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return false;
    }
    users.addIfNotSeen(value.uniqueUsers());
    return true;
  }
}
