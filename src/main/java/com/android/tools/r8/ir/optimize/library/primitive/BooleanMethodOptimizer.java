// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.StringUtils;
import java.util.Set;

public class BooleanMethodOptimizer extends PrimitiveMethodOptimizer {

  BooleanMethodOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  DexMethod getBoxMethod() {
    return dexItemFactory.booleanMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return dexItemFactory.booleanMembers.booleanValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedBoolean();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedBooleanType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (singleTarget.getReference().isIdenticalTo(dexItemFactory.booleanMembers.parseBoolean)) {
      optimizeParseBoolean(code, instructionIterator, invoke);
    } else {
      optimizeBoxingMethods(code, instructionIterator, invoke, singleTarget, affectedValues);
    }
    return instructionIterator;
  }

  private void optimizeParseBoolean(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value argument = invoke.getFirstArgument().getAliasedValue();
    if (argument.isDefinedByInstructionSatisfying(Instruction::isConstString)) {
      ConstString constString = argument.getDefinition().asConstString();
      if (!constString.instructionInstanceCanThrow(appView, code.context())) {
        String value = StringUtils.toLowerCase(constString.getValue().toString());
        if (value.equals("true")) {
          instructionIterator.replaceCurrentInstructionWithConstInt(code, 1);
        } else if (value.equals("false")) {
          instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
        }
      }
    }
  }

  @Override
  void optimizeBoxMethod(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod boxInvoke,
      AffectedValues affectedValues) {
    // Optimize Boolean.valueOf(b) into Boolean.FALSE or Boolean.TRUE.
    Value argument = boxInvoke.getFirstOperand();
    AbstractValue abstractValue = argument.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleNumberValue()) {
      instructionIterator.replaceCurrentInstructionWithStaticGet(
          appView,
          code,
          abstractValue.asSingleNumberValue().getBooleanValue()
              ? dexItemFactory.booleanMembers.TRUE
              : dexItemFactory.booleanMembers.FALSE,
          affectedValues);
      return;
    }
    super.optimizeBoxMethod(code, instructionIterator, boxInvoke, affectedValues);
  }
}
