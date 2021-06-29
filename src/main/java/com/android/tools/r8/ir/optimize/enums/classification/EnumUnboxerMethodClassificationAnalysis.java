// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class EnumUnboxerMethodClassificationAnalysis {

  /**
   * Simple analysis that classifies the given method using {@link
   * CheckNotNullEnumUnboxerMethodClassification} if the method is static and has a parameter of
   * type Object, which has a single if-zero user.
   */
  public static EnumUnboxerMethodClassification analyze(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, IRCode code) {
    if (!appView.options().enableEnumUnboxing) {
      // The classification is unused when enum unboxing is disabled.
      return EnumUnboxerMethodClassification.unknown();
    }

    if (!method.getAccessFlags().isStatic() || method.getParameters().isEmpty()) {
      // Not classified for enum unboxing.
      return EnumUnboxerMethodClassification.unknown();
    }

    // Look for an argument with a single if-zero user.
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InstructionIterator entryIterator = code.entryBlock().iterator();
    for (int index = 0; index < method.getParameters().size(); index++) {
      DexType parameter = method.getParameter(index);
      if (parameter != dexItemFactory.objectType) {
        continue;
      }

      Argument argument = entryIterator.next().asArgument();
      if (hasSingleIfZeroUser(argument)) {
        return new CheckNotNullEnumUnboxerMethodClassification(index);
      }
    }

    return EnumUnboxerMethodClassification.unknown();
  }

  private static boolean hasSingleIfZeroUser(Argument argument) {
    Value value = argument.outValue();
    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return false;
    }
    Set<Instruction> users = value.uniqueUsers();
    if (users.size() != 1) {
      return false;
    }
    Instruction user = users.iterator().next();
    if (!user.isIf()) {
      return false;
    }
    If ifUser = user.asIf();
    return ifUser.isZeroTest() && (ifUser.getType() == Type.EQ || ifUser.getType() == Type.NE);
  }
}
