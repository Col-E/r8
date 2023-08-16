// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.DequeUtils;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class IRCodeUtils {

  public static InvokeDirect getUniqueConstructorInvoke(
      Value value, DexItemFactory dexItemFactory) {
    InvokeDirect result = null;
    for (Instruction user : value.uniqueUsers()) {
      if (user.isInvokeDirect()) {
        InvokeDirect invoke = user.asInvokeDirect();
        if (!dexItemFactory.isConstructor(invoke.getInvokedMethod())) {
          continue;
        }
        if (invoke.getReceiver() != value) {
          continue;
        }
        if (result != null) {
          // Does not have a unique constructor invoke.
          return null;
        }
        result = invoke;
      }
    }
    return result;
  }

  /**
   * Finds the single assignment to the fields in {@param fields} in {@param code}. Note that this
   * does not guarantee that the assignments found dominate all the normal exits.
   */
  public static Map<DexEncodedField, StaticPut> findUniqueStaticPuts(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCode code,
      Set<DexEncodedField> fields) {
    Set<DexEncodedField> writtenMoreThanOnce = Sets.newIdentityHashSet();
    Map<DexEncodedField, StaticPut> uniqueStaticPuts = new IdentityHashMap<>();
    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      DexEncodedField field =
          appView.appInfo().resolveField(staticPut.getField()).getResolvedField();
      if (field == null || !fields.contains(field) || writtenMoreThanOnce.contains(field)) {
        continue;
      }
      if (uniqueStaticPuts.put(field, staticPut) != null) {
        writtenMoreThanOnce.add(field);
        uniqueStaticPuts.remove(field);
      }
    }
    return uniqueStaticPuts;
  }

  /**
   * Removes {@param instruction} if it is a {@link NewArrayEmpty} instruction, which only has
   * array-put users. Also removes all instructions that contribute to the computation of the
   * indices and the elements, if they end up being unused, even if the instructions may have side
   * effects (!).
   *
   * <p>Use with caution!
   */
  public static void removeArrayAndTransitiveInputsIfNotUsed(IRCode code, Instruction definition) {
    if (definition.isConstNumber()) {
      // No need to explicitly remove `null`, it will be removed by ordinary dead code elimination
      // anyway.
      assert definition.asConstNumber().isZero();
      return;
    }
    Value arrayValue = definition.outValue();
    if (arrayValue.hasPhiUsers() || arrayValue.hasDebugUsers()) {
      return;
    }
    if (!definition.isNewArrayEmptyOrNewArrayFilled()) {
      assert false;
      return;
    }
    Deque<InstructionOrPhi> worklist = new ArrayDeque<>();
    NewArrayFilled newArrayFilled = definition.asNewArrayFilled();
    if (newArrayFilled != null) {
      worklist.add(definition);
    } else if (definition.isNewArrayEmpty()) {
      for (Instruction user : arrayValue.uniqueUsers()) {
        // If we encounter an Assume instruction here, we also need to consider indirect users.
        assert !user.isAssume();
        if (!user.isArrayPut()) {
          return;
        }
        worklist.add(user);
      }
    } else {
      assert false;
    }
    internalRemoveInstructionAndTransitiveInputsIfNotUsed(code, worklist);
  }

  /**
   * Removes the given instruction and all the instructions that are used to define the in-values of
   * the given instruction, even if the instructions may have side effects (!).
   *
   * <p>Use with caution!
   */
  public static void removeInstructionAndTransitiveInputsIfNotUsed(
      IRCode code, Instruction instruction) {
    internalRemoveInstructionAndTransitiveInputsIfNotUsed(
        code, DequeUtils.newArrayDeque(instruction));
  }

  private static void internalRemoveInstructionAndTransitiveInputsIfNotUsed(
      IRCode code, Deque<InstructionOrPhi> worklist) {
    Set<InstructionOrPhi> removed = Sets.newIdentityHashSet();
    while (!worklist.isEmpty()) {
      InstructionOrPhi instructionOrPhi = worklist.removeFirst();
      if (removed.contains(instructionOrPhi)) {
        // Already removed.
        continue;
      }
      if (instructionOrPhi.isPhi()) {
        Phi current = instructionOrPhi.asPhi();
        if (!current.hasUsers() && !current.hasDebugUsers()) {
          boolean hasOtherPhiUserThanSelf = false;
          for (Phi phiUser : current.uniquePhiUsers()) {
            if (phiUser != current) {
              hasOtherPhiUserThanSelf = true;
              break;
            }
          }
          if (!hasOtherPhiUserThanSelf) {
            current.removeDeadPhi();
            for (Value operand : current.getOperands()) {
              worklist.add(operand.isPhi() ? operand.asPhi() : operand.definition);
            }
            removed.add(current);
          }
        }
      } else {
        Instruction current = instructionOrPhi.asInstruction();
        if (!current.hasOutValue() || !current.outValue().hasAnyUsers()) {
          current.getBlock().listIterator(code, current).removeOrReplaceByDebugLocalRead();
          for (Value inValue : current.inValues()) {
            worklist.add(inValue.isPhi() ? inValue.asPhi() : inValue.definition);
          }
          removed.add(current);
        }
      }
    }
  }
}
