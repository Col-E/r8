// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMap.FastSortedEntrySet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Canonicalize constants.
 */
public class ConstantCanonicalizer {

  private static final int MAX_CANONICALIZED_CONSTANT = 15;

  public static void canonicalize(IRCode code) {
    Object2ObjectLinkedOpenCustomHashMap<ConstInstruction, List<Value>> valuesDefinedByConstant =
        new Object2ObjectLinkedOpenCustomHashMap<>(
            new Strategy<ConstInstruction>() {
              @Override
              public int hashCode(ConstInstruction constInstruction) {
                assert constInstruction.isConstNumber() || constInstruction.isConstString();
                if (constInstruction.isConstNumber()) {
                  return Long.hashCode(constInstruction.asConstNumber().getRawValue())
                      + 13 * constInstruction.outType().hashCode();
                }
                return constInstruction.asConstString().getValue().hashCode();
              }

              @Override
              public boolean equals(ConstInstruction a, ConstInstruction b) {
                // Constants with local info must not be canonicalized and must be filtered.
                assert a == null || !a.outValue().hasLocalInfo();
                assert b == null || !b.outValue().hasLocalInfo();
                return a != null && b != null && a.identicalNonValueNonPositionParts(b);
              }
            });

    // Collect usages of constants that can be canonicalized.
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction current = it.next();
        // Interested in ConstNumber and ConstString.
        if (!current.isConstNumber() && !current.isConstString()) {
          continue;
        }
        // Constants with local info must not be canonicalized and must be filtered.
        if (current.outValue().hasLocalInfo()) {
          continue;
        }
        // Constants that are used by invoke range are not canonicalized to be compliant with the
        // optimization splitRangeInvokeConstant that gives the register allocator more freedom in
        // assigning register to ranged invokes which can greatly reduce the number of register
        // needed (and thereby code size as well). Thus no need to do a transformation that should
        // be removed later by another optimization.
        if (constantUsedByInvokeRange(current.asConstInstruction())) {
          continue;
        }
        List<Value> oldValuesDefinedByConstant = valuesDefinedByConstant.computeIfAbsent(
            current.asConstInstruction(), k -> new ArrayList<>());
        oldValuesDefinedByConstant.add(current.outValue());
      }
    }

    if (valuesDefinedByConstant.isEmpty()) {
      return;
    }

    // Double-check the entry block does not have catch handlers.
    // Otherwise, we need to split it before moving canonicalized const-string, which may throw.
    assert !code.blocks.getFirst().hasCatchHandlers();
    Position firstNonNonePosition = findFirstNonNonePosition(code);
    FastSortedEntrySet<ConstInstruction, List<Value>> entries =
        valuesDefinedByConstant.object2ObjectEntrySet();
    // Sort the most frequently used constant first and exclude constant use only one time, such
    // as the {@code MAX_CANONICALIZED_CONSTANT} will be canonicalized into the entry block.
    entries.stream()
        .filter(a -> a.getValue().size() > 1)
        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
        .limit(MAX_CANONICALIZED_CONSTANT)
        .forEach((entry) -> {
          ConstInstruction canonicalizedConstant = entry.getKey().asConstInstruction();
          assert canonicalizedConstant.isConstNumber() || canonicalizedConstant.isConstString();
          ConstInstruction newConst;
          if (canonicalizedConstant.isConstNumber()) {
            ConstNumber canonicalizedConstantNumber = canonicalizedConstant.asConstNumber();
            newConst = ConstNumber.copyOf(code, canonicalizedConstantNumber);
          } else {
            ConstString canonicalizedConstantString = canonicalizedConstant.asConstString();
            newConst = ConstString.copyOf(code, canonicalizedConstantString);
          }
          newConst.setPosition(firstNonNonePosition);
          insertCanonicalizedConstant(code, newConst);
          for (Value outValue : entry.getValue()) {
            outValue.replaceUsers(newConst.outValue());
          }
        });

    code.removeAllTrivialPhis();
    assert code.isConsistentSSA();
  }

  private static void insertCanonicalizedConstant(
      IRCode code, ConstInstruction canonicalizedConstant) {
    BasicBlock entryBlock = code.blocks.get(0);
    // Insert the constant instruction at the start of the block right after the argument
    // instructions. It is important that the const instruction is put before any instruction
    // that can throw exceptions (since the value could be used on the exceptional edge).
    InstructionListIterator it = entryBlock.listIterator();
    while (it.hasNext()) {
      if (!it.next().isArgument()) {
        it.previous();
        break;
      }
    }
    it.add(canonicalizedConstant);
  }

  private static Position findFirstNonNonePosition(IRCode code) {
    BasicBlock entryBlock = code.blocks.get(0);
    Instruction rightAfterArguments =
        entryBlock.listIterator().nextUntil(instr -> !instr.isArgument());
    Position firstNonArgumentPosition = rightAfterArguments.getPosition();
    Set<BasicBlock> visitedBlocks = new HashSet<>();
    while (rightAfterArguments != null) {
      // Make sure we are not looping.
      if (visitedBlocks.contains(rightAfterArguments.getBlock())) {
        break;
      }
      visitedBlocks.add(rightAfterArguments.getBlock());
      // The very first non-argument instruction can be chained via goto.
      if (rightAfterArguments.isGoto()) {
        rightAfterArguments = rightAfterArguments.asGoto().getTarget().getInstructions().getFirst();
      } else if (rightAfterArguments.getPosition().isSome()) {
        return rightAfterArguments.getPosition();
      } else {
        break;
      }
    }
    return firstNonArgumentPosition;
  }

  private static boolean constantUsedByInvokeRange(ConstInstruction constant) {
    for (Instruction user : constant.outValue().uniqueUsers()) {
      if (user.isInvoke() && user.asInvoke().requiredArgumentRegisters() > 5) {
        return true;
      }
    }
    return false;
  }
}
