// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Value;
import java.util.Arrays;
import java.util.List;

public class ValueUtils {
  // We allocate an array of this size, so guard against it getting too big.
  private static int MAX_ARRAY_SIZE = 100000;

  @SuppressWarnings("ReferenceEquality")
  public static boolean isStringBuilder(Value value, DexItemFactory dexItemFactory) {
    TypeElement type = value.getType();
    return type.isClassType()
        && type.asClassType().getClassType() == dexItemFactory.stringBuilderType;
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNonNullStringBuilder(Value value, DexItemFactory dexItemFactory) {
    while (true) {
      if (value.isPhi()) {
        return false;
      }

      Instruction definition = value.getDefinition();
      if (definition.isNewInstance()) {
        NewInstance newInstance = definition.asNewInstance();
        return newInstance.clazz == dexItemFactory.stringBuilderType;
      }

      if (definition.isInvokeVirtual()) {
        InvokeVirtual invoke = definition.asInvokeVirtual();
        if (dexItemFactory.stringBuilderMethods.isAppendMethod(invoke.getInvokedMethod())) {
          value = invoke.getReceiver();
          continue;
        }
      }

      // Unhandled definition.
      return false;
    }
  }

  public static final class ArrayValues {
    private List<Value> elementValues;
    private ArrayPut[] arrayPutsByIndex;

    private ArrayValues(List<Value> elementValues) {
      this.elementValues = elementValues;
    }

    private ArrayValues(ArrayPut[] arrayPutsByIndex) {
      this.arrayPutsByIndex = arrayPutsByIndex;
    }

    /** May contain null entries when array has null entries. */
    public List<Value> getElementValues() {
      if (elementValues == null) {
        ArrayPut[] puts = arrayPutsByIndex;
        Value[] elementValuesArr = new Value[puts.length];
        for (int i = 0; i < puts.length; ++i) {
          ArrayPut arrayPut = puts[i];
          elementValuesArr[i] = arrayPut == null ? null : arrayPut.value();
        }
        elementValues = Arrays.asList(elementValuesArr);
      }
      return elementValues;
    }

    public int size() {
      return elementValues != null ? elementValues.size() : arrayPutsByIndex.length;
    }
  }

  /**
   * Attempts to determine all values for the given array. This will work only when:
   *
   * <pre>
   * 1) The Array has a single users (other than array-puts)
   *   * This constraint is to ensure other users do not modify the array.
   *   * When users are in different blocks, their order is hard to know.
   * 2) The array size is a constant.
   * 3) All array-put instructions have constant and unique indices.
   *   * With the exception of array-puts that are in the same block as singleUser, in which case
   *     non-unique puts are allowed.
   *   * Indices must be unique in other blocks because order is hard to know when multiple blocks
   *     are concerned (and reassignment is rare anyways).
   * 4) The array-put instructions are guaranteed to be executed before singleUser.
   * </pre>
   *
   * @param arrayValue The Value for the array.
   * @param singleUser The only non-array-put user, or null to auto-detect.
   * @return The computed array values, or null if they could not be determined.
   */
  public static ArrayValues computeSingleUseArrayValues(Value arrayValue, Instruction singleUser) {
    assert singleUser == null || arrayValue.uniqueUsers().contains(singleUser);
    TypeElement arrayType = arrayValue.getType();
    if (!arrayType.isArrayType() || arrayValue.hasDebugUsers() || arrayValue.isPhi()) {
      return null;
    }

    Instruction definition = arrayValue.definition;
    NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = definition.asNewArrayFilled();
    if (newArrayFilled != null) {
      // It would be possible to have new-array-filled followed by aput-array, but that sequence of
      // instructions does not commonly occur, so we don't support it here.
      if (!arrayValue.hasSingleUniqueUser() || arrayValue.hasPhiUsers()) {
        return null;
      }
      return new ArrayValues(newArrayFilled.inValues());
    } else if (newArrayEmpty == null) {
      return null;
    }

    int arraySize = newArrayEmpty.sizeIfConst();
    if (arraySize < 0 || arraySize > MAX_ARRAY_SIZE) {
      // Array is non-const size.
      return null;
    }

    if (singleUser == null) {
      for (Instruction user : arrayValue.uniqueUsers()) {
        ArrayPut arrayPut = user.asArrayPut();
        if (arrayPut == null || arrayPut.array() != arrayValue || arrayPut.value() == arrayValue) {
          if (singleUser == null) {
            singleUser = user;
          } else {
            return null;
          }
        }
      }
    }

    ArrayPut[] arrayPutsByIndex = new ArrayPut[arraySize];
    BasicBlock usageBlock = singleUser.getBlock();
    for (Instruction user : arrayValue.uniqueUsers()) {
      ArrayPut arrayPut = user.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue || arrayPut.value() == arrayValue) {
        if (user == singleUser) {
          continue;
        }
        // Found a second non-array-put user.
        return null;
      }
      // Process same-block instructions later.
      if (user.getBlock() == usageBlock) {
        continue;
      }
      int index = arrayPut.indexIfConstAndInBounds(arraySize);
      // We do not know what order blocks are in, so do not allow re-assignment.
      if (index < 0 || arrayPutsByIndex[index] != null) {
        return null;
      }
      arrayPutsByIndex[index] = arrayPut;
    }

    // Ensure that all paths from new-array-empty to |usage| contain all array-put instructions.
    DominatorChecker dominatorChecker = DominatorChecker.create(definition.getBlock(), usageBlock);
    for (Instruction user : arrayValue.uniqueUsers()) {
      if (!dominatorChecker.check(user.getBlock())) {
        return null;
      }
    }

    boolean seenSingleUser = false;
    for (Instruction inst : usageBlock.getInstructions()) {
      if (inst == singleUser) {
        seenSingleUser = true;
        continue;
      }
      ArrayPut arrayPut = inst.asArrayPut();
      if (arrayPut == null || arrayPut.array() != arrayValue) {
        continue;
      }
      if (seenSingleUser) {
        // Found an array-put after the array was used. This is too uncommon of a thing to support.
        return null;
      }
      int index = arrayPut.indexIfConstAndInBounds(arraySize);
      if (index < 0) {
        return null;
      }
      // We can allow reassignment at this point since we are visiting in order.
      arrayPutsByIndex[index] = arrayPut;
    }

    return new ArrayValues(arrayPutsByIndex);
  }
}
