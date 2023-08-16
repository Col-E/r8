// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.StringUtils;
import java.util.Set;

public class BooleanMethodOptimizer extends StatelessLibraryMethodModelCollection {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;

  BooleanMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedBooleanType;
  }

  @Override
  public void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove) {
    if (singleTarget.getReference() == dexItemFactory.booleanMembers.booleanValue) {
      optimizeBooleanValue(code, instructionIterator, invoke);
    } else if (singleTarget.getReference() == dexItemFactory.booleanMembers.parseBoolean) {
      optimizeParseBoolean(code, instructionIterator, invoke);
    } else if (singleTarget.getReference() == dexItemFactory.booleanMembers.valueOf) {
      optimizeValueOf(code, instructionIterator, invoke, affectedValues);
    }
  }

  private void optimizeBooleanValue(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value argument = invoke.arguments().get(0).getAliasedValue();
    if (!argument.isPhi()) {
      Instruction definition = argument.definition;
      if (definition.isStaticGet()) {
        StaticGet staticGet = definition.asStaticGet();
        DexField field = staticGet.getField();
        if (field == dexItemFactory.booleanMembers.TRUE) {
          instructionIterator.replaceCurrentInstructionWithConstInt(code, 1);
        } else if (field == dexItemFactory.booleanMembers.FALSE) {
          instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
        }
      }
    }
  }

  private void optimizeParseBoolean(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value argument = invoke.arguments().get(0).getAliasedValue();
    if (!argument.isPhi()) {
      Instruction definition = argument.definition;
      if (definition.isConstString()) {
        ConstString constString = definition.asConstString();
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
  }

  private void optimizeValueOf(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Set<Value> affectedValues) {
    Value argument = invoke.arguments().get(0);
    AbstractValue abstractValue = argument.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleNumberValue()) {
      instructionIterator.replaceCurrentInstructionWithStaticGet(
          appView,
          code,
          abstractValue.asSingleNumberValue().getBooleanValue()
              ? dexItemFactory.booleanMembers.TRUE
              : dexItemFactory.booleanMembers.FALSE,
          affectedValues);
    }
  }
}
