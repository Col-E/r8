// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.LongInterval;
import com.google.common.collect.Sets;
import java.util.Set;

public class ValueMayDependOnEnvironmentAnalysis {

  private final AppView<?> appView;
  private final IRCode code;
  private final DexType context;

  public ValueMayDependOnEnvironmentAnalysis(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
    this.context = code.method.method.holder;
  }

  public boolean valueMayDependOnEnvironment(Value value) {
    Value root = value.getAliasedValue();
    if (root.isConstant()) {
      return false;
    }
    if (isConstantArrayThroughoutMethod(root)) {
      return false;
    }
    return true;
  }

  public boolean isConstantArrayThroughoutMethod(Value value) {
    Value root = value.getAliasedValue();
    if (root.isPhi()) {
      // Would need to track the aliases, just give up.
      return false;
    }

    Instruction definition = root.definition;

    // Check that it is a constant array with a known size at this point in the IR.
    long size;
    if (definition.isInvokeNewArray()) {
      InvokeNewArray invokeNewArray = definition.asInvokeNewArray();
      for (Value argument : invokeNewArray.arguments()) {
        if (!argument.isConstant()) {
          return false;
        }
      }
      size = invokeNewArray.arguments().size();
    } else if (definition.isNewArrayEmpty()) {
      NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
      Value sizeValue = newArrayEmpty.size().getAliasedValue();
      if (!sizeValue.hasValueRange()) {
        return false;
      }
      LongInterval sizeRange = sizeValue.getValueRange();
      if (!sizeRange.isSingleValue()) {
        return false;
      }
      size = sizeRange.getSingleValue();
    } else {
      // Some other array creation.
      return false;
    }

    if (size < 0) {
      // Check for NegativeArraySizeException.
      return false;
    }

    if (size == 0) {
      // Empty arrays are always constant.
      return true;
    }

    // Allow array-put and new-array-filled-data instructions that immediately follow the array
    // creation.
    Set<Instruction> consumedInstructions = Sets.newIdentityHashSet();

    for (Instruction instruction : definition.getBlock().instructionsAfter(definition)) {
      if (instruction.isArrayPut()) {
        ArrayPut arrayPut = instruction.asArrayPut();
        Value array = arrayPut.array().getAliasedValue();
        if (array != root) {
          // This ends the chain of array-put instructions that are allowed immediately after the
          // array creation.
          break;
        }

        LongInterval indexRange = arrayPut.index().getValueRange();
        if (!indexRange.isSingleValue()) {
          return false;
        }

        long index = indexRange.getSingleValue();
        if (index < 0 || index >= size) {
          return false;
        }

        if (!arrayPut.value().isConstant()) {
          return false;
        }

        consumedInstructions.add(arrayPut);
        continue;
      }

      if (instruction.isNewArrayFilledData()) {
        NewArrayFilledData newArrayFilledData = instruction.asNewArrayFilledData();
        Value array = newArrayFilledData.src();
        if (array != root) {
          break;
        }

        consumedInstructions.add(newArrayFilledData);
        continue;
      }

      if (instruction.instructionMayHaveSideEffects(appView, context)) {
        // This ends the chain of array-put instructions that are allowed immediately after the
        // array creation.
        break;
      }
    }

    // Check that the array is not mutated before the end of this method.
    //
    // Currently, we only allow the array to flow into static-put instructions that are not
    // followed by an instruction that may have side effects. Instructions that do not have any
    // side effects are ignored because they cannot mutate the array.
    return !valueMayBeMutatedBeforeMethodExit(root, consumedInstructions);
  }

  private boolean valueMayBeMutatedBeforeMethodExit(Value value, Set<Instruction> whitelist) {
    assert !value.hasAliasedValue();

    if (value.numberOfPhiUsers() > 0) {
      // Could be mutated indirectly.
      return true;
    }

    Set<Instruction> visited = Sets.newIdentityHashSet();
    for (Instruction user : value.uniqueUsers()) {
      if (whitelist.contains(user)) {
        continue;
      }

      if (user.isStaticPut()) {
        StaticPut staticPut = user.asStaticPut();
        if (visited.contains(staticPut)) {
          // Already visited previously.
          continue;
        }
        for (Instruction instruction : code.getInstructionsReachableFrom(staticPut)) {
          if (!visited.add(instruction)) {
            // Already visited previously.
            continue;
          }
          if (instruction.isStaticPut()) {
            StaticPut otherStaticPut = instruction.asStaticPut();
            if (otherStaticPut.getField().holder == staticPut.getField().holder
                && instruction.instructionInstanceCanThrow(appView, context).cannotThrow()) {
              continue;
            }
            return true;
          }
          if (instruction.instructionMayTriggerMethodInvocation(appView, context)) {
            return true;
          }
        }
        continue;
      }

      // Other user than static-put, just give up.
      return false;
    }

    return false;
  }
}
