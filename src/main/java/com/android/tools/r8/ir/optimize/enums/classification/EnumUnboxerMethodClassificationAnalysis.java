// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.classification;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Set;

public class EnumUnboxerMethodClassificationAnalysis {

  /**
   * Simple analysis that classifies the given method using {@link
   * CheckNotNullEnumUnboxerMethodClassification} if the method is static and has a parameter of
   * type Object, which has a single if-zero user.
   */
  @SuppressWarnings("ReferenceEquality")
  public static EnumUnboxerMethodClassification analyze(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      IRCode code,
      MethodProcessor methodProcessor) {
    if (!appView.options().enableEnumUnboxing) {
      // The classification is unused when enum unboxing is disabled.
      return EnumUnboxerMethodClassification.unknown();
    }

    if (!method.getAccessFlags().isStatic() || method.getParameters().isEmpty()) {
      // Not classified for enum unboxing.
      return EnumUnboxerMethodClassification.unknown();
    }

    // Look for an argument with a single if-zero user.
    EnumUnboxerMethodClassification currentClassification =
        method.getOptimizationInfo().getEnumUnboxerMethodClassification();
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InstructionIterator entryIterator = code.entryBlock().iterator();
    for (int index = 0; index < method.getParameters().size(); index++) {
      Argument argument = entryIterator.next().asArgument();
      DexType parameter = method.getParameter(index);
      // Before enum unboxing, we classify methods with `object != null` as check-not-null methods.
      // After enum unboxing, we check correctness of the classification for check-not-zero methods.
      if (appView.hasUnboxedEnums()) {
        if (parameter != dexItemFactory.intType
            || !currentClassification.isCheckNotNullClassification()
            || currentClassification.asCheckNotNullClassification().getArgumentIndex() != index) {
          continue;
        }
      } else {
        if (parameter != dexItemFactory.objectType) {
          continue;
        }
      }

      if (onlyHasCheckNotNullUsers(argument, methodProcessor)) {
        return new CheckNotNullEnumUnboxerMethodClassification(index);
      }
    }

    return EnumUnboxerMethodClassification.unknown();
  }

  private static boolean onlyHasCheckNotNullUsers(
      Argument argument, MethodProcessor methodProcessor) {
    // Check that the argument has an if-zero user and a return user (optional).
    Value value = argument.outValue();
    if (value.hasDebugUsers() || value.hasPhiUsers()) {
      return false;
    }
    Set<Instruction> users = value.aliasedUsers();
    boolean seenIf = false;
    for (Instruction user : users) {
      switch (user.opcode()) {
        case ASSUME:
          if (user.outValue().hasDebugUsers() || user.outValue().hasPhiUsers()) {
            return false;
          }
          break;
        case IF:
          {
            If ifUser = user.asIf();
            if (!ifUser.isZeroTest()
                || (ifUser.getType() != IfType.EQ && ifUser.getType() != IfType.NE)) {
              return false;
            }
            seenIf = true;
            break;
          }
        case RETURN:
          break;
        default:
          return false;
      }
    }

    // During the primary optimization pass, we require seeing an if instruction (to limit the
    // amount of method duplication). For monotonicity, we do not require seeing an if instruction
    // in the subsequent optimization passes, since the if instruction that lead us to return a
    // CheckNotNullEnumUnboxerMethodClassification may be eliminated by (for example) the call site
    // optimization.
    return !methodProcessor.isPrimaryMethodProcessor() || seenIf;
  }
}
