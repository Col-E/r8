// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.LazyBox;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * This optimization exploits that we can sometimes learn the constant value of an SSA value that
 * flows into an if-eq of if-neq instruction.
 *
 * <p>Consider the following example:
 *
 * <pre>
 * 1. if (obj != null) {
 * 2.  return doStuff();
 * 3. }
 * 4. return null;
 * </pre>
 *
 * <p>Since we know that `obj` is null in all blocks that are dominated by the false-target of the
 * if-instruction in line 1, we can safely replace the null-constant in line 4 by `obj`, and thereby
 * save a const-number instruction.
 */
public class RedundantConstNumberRemover extends CodeRewriterPass<AppInfo> {

  public RedundantConstNumberRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "RedundantConstNumberRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    if (appView.options().canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug()
        && !appView.options().testing.forceRedundantConstNumberRemoval) {
      // See also b/124152497.
      return false;
    }
    return options.enableRedundantConstNumberOptimization && code.metadata().mayHaveConstNumber();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    LazyBox<Long2ReferenceMap<List<ConstNumber>>> constantsByValue =
        new LazyBox<>(() -> getConstantsByValue(code));
    LazyBox<DominatorTree> dominatorTree = new LazyBox<>(() -> new DominatorTree(code));

    boolean changed = false;
    for (BasicBlock block : code.blocks) {
      Instruction lastInstruction = block.getInstructions().getLast();
      if (!lastInstruction.isIf()) {
        continue;
      }

      If ifInstruction = lastInstruction.asIf();
      IfType type = ifInstruction.getType();

      Value lhs = ifInstruction.inValues().get(0);
      Value rhs = !ifInstruction.isZeroTest() ? ifInstruction.inValues().get(1) : null;

      if (!ifInstruction.isZeroTest() && !lhs.isConstNumber() && !rhs.isConstNumber()) {
        // We can only conclude anything from an if-instruction if it is a zero-test or if one of
        // the two operands is a constant.
        continue;
      }

      // If the type is neither EQ nor NE, we cannot conclude anything about any of the in-values
      // of the if-instruction from the outcome of the if-instruction.
      if (type != IfType.EQ && type != IfType.NE) {
        continue;
      }

      BasicBlock trueTarget, falseTarget;
      if (type == IfType.EQ) {
        trueTarget = ifInstruction.getTrueTarget();
        falseTarget = ifInstruction.fallthroughBlock();
      } else {
        falseTarget = ifInstruction.getTrueTarget();
        trueTarget = ifInstruction.fallthroughBlock();
      }

      if (ifInstruction.isZeroTest()) {
        changed |=
            replaceDominatedConstNumbers(0, lhs, trueTarget, constantsByValue, code, dominatorTree);
        if (lhs.knownToBeBoolean()) {
          changed |=
              replaceDominatedConstNumbers(
                  1, lhs, falseTarget, constantsByValue, code, dominatorTree);
        }
      } else {
        assert rhs != null;
        if (lhs.isConstNumber()) {
          ConstNumber lhsAsNumber = lhs.getConstInstruction().asConstNumber();
          changed |=
              replaceDominatedConstNumbers(
                  lhsAsNumber.getRawValue(),
                  rhs,
                  trueTarget,
                  constantsByValue,
                  code,
                  dominatorTree);
          if (lhs.knownToBeBoolean() && rhs.knownToBeBoolean()) {
            changed |=
                replaceDominatedConstNumbers(
                    negateBoolean(lhsAsNumber),
                    rhs,
                    falseTarget,
                    constantsByValue,
                    code,
                    dominatorTree);
          }
        } else {
          assert rhs.isConstNumber();
          ConstNumber rhsAsNumber = rhs.getConstInstruction().asConstNumber();
          changed |=
              replaceDominatedConstNumbers(
                  rhsAsNumber.getRawValue(),
                  lhs,
                  trueTarget,
                  constantsByValue,
                  code,
                  dominatorTree);
          if (lhs.knownToBeBoolean() && rhs.knownToBeBoolean()) {
            changed |=
                replaceDominatedConstNumbers(
                    negateBoolean(rhsAsNumber),
                    lhs,
                    falseTarget,
                    constantsByValue,
                    code,
                    dominatorTree);
          }
        }
      }

      if (constantsByValue.computeIfAbsent().isEmpty()) {
        break;
      }
    }

    if (changed) {
      code.removeAllDeadAndTrivialPhis();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private static Long2ReferenceMap<List<ConstNumber>> getConstantsByValue(IRCode code) {
    // A map from the raw value of constants in `code` to the const number instructions that define
    // the given raw value (irrespective of the type of the raw value).
    Long2ReferenceMap<List<ConstNumber>> constantsByValue = new Long2ReferenceOpenHashMap<>();

    // Initialize `constantsByValue`.
    for (Instruction instruction : code.instructions()) {
      if (instruction.isConstNumber()) {
        ConstNumber constNumber = instruction.asConstNumber();
        if (constNumber.outValue().hasLocalInfo()) {
          // Not necessarily constant, because it could be changed in the debugger.
          continue;
        }
        long rawValue = constNumber.getRawValue();
        if (constantsByValue.containsKey(rawValue)) {
          constantsByValue.get(rawValue).add(constNumber);
        } else {
          List<ConstNumber> list = new ArrayList<>();
          list.add(constNumber);
          constantsByValue.put(rawValue, list);
        }
      }
    }
    return constantsByValue;
  }

  private static int negateBoolean(ConstNumber number) {
    assert number.outValue().knownToBeBoolean();
    return number.getRawValue() == 0 ? 1 : 0;
  }

  private boolean replaceDominatedConstNumbers(
      long withValue,
      Value newValue,
      BasicBlock dominator,
      LazyBox<Long2ReferenceMap<List<ConstNumber>>> constantsByValueSupplier,
      IRCode code,
      LazyBox<DominatorTree> dominatorTree) {
    if (newValue.hasLocalInfo()) {
      // We cannot replace a constant with a value that has local info, because that could change
      // debugging behavior.
      return false;
    }

    Long2ReferenceMap<List<ConstNumber>> constantsByValue =
        constantsByValueSupplier.computeIfAbsent();
    List<ConstNumber> constantsWithValue = constantsByValue.get(withValue);
    if (constantsWithValue == null || constantsWithValue.isEmpty()) {
      return false;
    }

    boolean changed = false;

    ListIterator<ConstNumber> constantWithValueIterator = constantsWithValue.listIterator();
    while (constantWithValueIterator.hasNext()) {
      ConstNumber constNumber = constantWithValueIterator.next();
      Value value = constNumber.outValue();
      assert !value.hasLocalInfo();
      assert constNumber.getRawValue() == withValue;

      BasicBlock block = constNumber.getBlock();

      // If the following condition does not hold, then the if-instruction does not dominate the
      // block containing the constant, although the true or false target does.
      if (block == dominator && block.getPredecessors().size() != 1) {
        // This should generally not happen, but it is possible to write bytecode where it does.
        assert false;
        continue;
      }

      if (value.knownToBeBoolean() && !newValue.knownToBeBoolean()) {
        // We cannot replace a boolean by a none-boolean since that can lead to verification
        // errors. For example, the following code fails with "register v1 has type Imprecise
        // Constant: 127 but expected Boolean return-1nr".
        //
        //   public boolean convertIntToBoolean(int v1) {
        //       const/4 v0, 0x1
        //       if-eq v1, v0, :eq_true
        //       const/4 v1, 0x0
        //     :eq_true
        //       return v1
        //   }
        continue;
      }

      if (dominatorTree.computeIfAbsent().dominatedBy(block, dominator)) {
        if (newValue.getType().lessThanOrEqual(value.getType(), appView)) {
          value.replaceUsers(newValue);
          block.listIterator(code, constNumber).removeOrReplaceByDebugLocalRead();
          constantWithValueIterator.remove();
          changed = true;
        } else if (value.getType().isNullType()) {
          // TODO(b/120257211): Need a mechanism to determine if `newValue` can be used at all of
          // the use sites of `value` without introducing a type error.
        }
      }
    }

    if (constantsWithValue.isEmpty()) {
      constantsByValue.remove(withValue);
    }

    return changed;
  }
}
