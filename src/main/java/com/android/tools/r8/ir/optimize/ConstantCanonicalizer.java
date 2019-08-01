// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.utils.StringUtils;
import it.unimi.dsi.fastutil.Hash.Strategy;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenCustomHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectSortedMap.FastSortedEntrySet;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonicalize constants.
 */
public class ConstantCanonicalizer {
  // Threshold to limit the number of constant canonicalization.
  private static final int MAX_CANONICALIZED_CONSTANT = 22;

  private int numberOfConstNumberCanonicalization = 0;
  private int numberOfConstStringCanonicalization = 0;
  private int numberOfDexItemBasedConstStringCanonicalization = 0;
  private int numberOfConstClassCanonicalization = 0;
  private final Object2IntMap<Long> histogramOfCanonicalizationCandidatesPerMethod;

  public ConstantCanonicalizer() {
    if (Log.ENABLED) {
      histogramOfCanonicalizationCandidatesPerMethod = new Object2IntArrayMap<>();
    } else {
      histogramOfCanonicalizationCandidatesPerMethod = null;
    }
  }

  public void logResults() {
    assert Log.ENABLED;
    Log.info(getClass(),
        "# const-number canonicalization: %s", numberOfConstNumberCanonicalization);
    Log.info(getClass(),
        "# const-string canonicalization: %s", numberOfConstStringCanonicalization);
    Log.info(getClass(),
        "# item-based const-string canonicalization: %s",
        numberOfDexItemBasedConstStringCanonicalization);
    Log.info(getClass(),
        "# const-class canonicalization: %s", numberOfConstClassCanonicalization);
    assert histogramOfCanonicalizationCandidatesPerMethod != null;
    Log.info(getClass(), "------ histogram of constant canonicalization candidates ------");
    histogramOfCanonicalizationCandidatesPerMethod.forEach((length, count) -> {
      Log.info(getClass(),
          "%s: %s (%s)", length, StringUtils.times("*", Math.min(count, 53)), count);
    });
  }

  public void canonicalize(AppView<?> appView, IRCode code) {
    Object2ObjectLinkedOpenCustomHashMap<ConstInstruction, List<Value>> valuesDefinedByConstant =
        new Object2ObjectLinkedOpenCustomHashMap<>(
            new Strategy<ConstInstruction>() {
              @Override
              public int hashCode(ConstInstruction constInstruction) {
                assert constInstruction.isConstNumber()
                    || constInstruction.isConstString()
                    || constInstruction.isDexItemBasedConstString()
                    || constInstruction.isConstClass();
                if (constInstruction.isConstNumber()) {
                  return Long.hashCode(constInstruction.asConstNumber().getRawValue())
                      + 13 * constInstruction.outType().hashCode();
                }
                if (constInstruction.isConstString()) {
                  return constInstruction.asConstString().getValue().hashCode();
                }
                if (constInstruction.isDexItemBasedConstString()) {
                  return constInstruction.asDexItemBasedConstString().getItem().hashCode();
                }
                return constInstruction.asConstClass().getValue().hashCode();
              }

              @Override
              public boolean equals(ConstInstruction a, ConstInstruction b) {
                // Constants with local info must not be canonicalized and must be filtered.
                assert a == null || !a.outValue().hasLocalInfo();
                assert b == null || !b.outValue().hasLocalInfo();
                return a == b
                    || (a != null && b != null && a.identicalNonValueNonPositionParts(b));
              }
            });

    // Collect usages of constants that can be canonicalized.
    for (Instruction current : code.instructions()) {
      // Interested in ConstNumber, (DexItemBased)?ConstString, and ConstClass
      if (!current.isConstNumber()
          && !current.isConstString()
          && !current.isDexItemBasedConstString()
          && !current.isConstClass()) {
        continue;
      }
      // Do not canonicalize ConstClass that may have side effects. Its original instructions
      // will not be removed by dead code remover due to the side effects.
      if (current.isConstClass()
          && current.instructionMayHaveSideEffects(appView, code.method.method.holder)) {
        continue;
      }
      // Do not canonicalize ConstString instructions if there are monitor operations in the code.
      // That could lead to unbalanced locking and could lead to situations where OOM exceptions
      // could leave a synchronized method without unlocking the monitor.
      if ((current.isConstString() || current.isDexItemBasedConstString())
          && code.metadata().mayHaveMonitorInstruction()) {
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
      List<Value> oldValuesDefinedByConstant =
          valuesDefinedByConstant.computeIfAbsent(
              current.asConstInstruction(), k -> new ArrayList<>());
      oldValuesDefinedByConstant.add(current.outValue());
    }

    if (valuesDefinedByConstant.isEmpty()) {
      return;
    }

    // Double-check the entry block does not have catch handlers.
    // Otherwise, we need to split it before moving canonicalized const-string, which may throw.
    assert !code.entryBlock().hasCatchHandlers();
    final Position firstNonNonePosition = code.findFirstNonNonePosition();
    FastSortedEntrySet<ConstInstruction, List<Value>> entries =
        valuesDefinedByConstant.object2ObjectEntrySet();
    // Sort the most frequently used constant first and exclude constant use only one time, such
    // as the {@code MAX_CANONICALIZED_CONSTANT} will be canonicalized into the entry block.
    if (Log.ENABLED && Log.isLoggingEnabledFor(ConstantCanonicalizer.class)) {
      Long numOfCandidates = entries.stream().filter(a -> a.getValue().size() > 1).count();
      synchronized (histogramOfCanonicalizationCandidatesPerMethod) {
        int count = histogramOfCanonicalizationCandidatesPerMethod.getOrDefault(numOfCandidates, 0);
        histogramOfCanonicalizationCandidatesPerMethod.put(numOfCandidates, count + 1);
      }
    }
    entries.stream()
        .filter(a -> a.getValue().size() > 1)
        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
        .limit(MAX_CANONICALIZED_CONSTANT)
        .forEach((entry) -> {
          ConstInstruction canonicalizedConstant = entry.getKey().asConstInstruction();
          assert canonicalizedConstant.isConstNumber()
              || canonicalizedConstant.isConstString()
              || canonicalizedConstant.isDexItemBasedConstString()
              || canonicalizedConstant.isConstClass();
          ConstInstruction newConst;
          if (canonicalizedConstant.isConstNumber()) {
            if (Log.ENABLED) {
              numberOfConstNumberCanonicalization++;
            }
            newConst = ConstNumber.copyOf(code, canonicalizedConstant.asConstNumber());
          } else if (canonicalizedConstant.isConstString()) {
            if (Log.ENABLED) {
              numberOfConstStringCanonicalization++;
            }
            newConst = ConstString.copyOf(code, canonicalizedConstant.asConstString());
          } else if (canonicalizedConstant.isDexItemBasedConstString()) {
            if (Log.ENABLED) {
              numberOfDexItemBasedConstStringCanonicalization++;
            }
            newConst = DexItemBasedConstString.copyOf(
                code, canonicalizedConstant.asDexItemBasedConstString());
          } else {
            if (Log.ENABLED) {
              numberOfConstClassCanonicalization++;
            }
            newConst = ConstClass.copyOf(code, canonicalizedConstant.asConstClass());
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
    BasicBlock entryBlock = code.entryBlock();
    // Insert the constant instruction at the start of the block right after the argument
    // instructions. It is important that the const instruction is put before any instruction
    // that can throw exceptions (since the value could be used on the exceptional edge).
    InstructionListIterator it = entryBlock.listIterator(code);
    while (it.hasNext()) {
      if (!it.next().isArgument()) {
        it.previous();
        break;
      }
    }
    it.add(canonicalizedConstant);
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
