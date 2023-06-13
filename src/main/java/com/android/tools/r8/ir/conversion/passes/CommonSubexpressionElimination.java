// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import java.util.List;

public class CommonSubexpressionElimination extends CodeRewriterPass<AppInfo> {

  public CommonSubexpressionElimination(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "CommonSubexpressionElimination";
  }

  @Override
  protected boolean shouldRewriteCode(ProgramMethod method, IRCode code) {
    return true;
  }

  @Override
  protected void rewriteCode(ProgramMethod method, IRCode code) {
    int noCandidate = code.reserveMarkingColor();
    if (hasCSECandidate(code, noCandidate)) {
      final ListMultimap<Wrapper<Instruction>, Value> instructionToValue =
          ArrayListMultimap.create();
      final CSEExpressionEquivalence equivalence = new CSEExpressionEquivalence(options);
      final DominatorTree dominatorTree = new DominatorTree(code);
      for (int i = 0; i < dominatorTree.getSortedBlocks().length; i++) {
        BasicBlock block = dominatorTree.getSortedBlocks()[i];
        if (block.isMarked(noCandidate)) {
          continue;
        }
        InstructionListIterator iterator = block.listIterator(code);
        while (iterator.hasNext()) {
          Instruction instruction = iterator.next();
          if (isCSEInstructionCandidate(instruction)) {
            List<Value> candidates = instructionToValue.get(equivalence.wrap(instruction));
            boolean eliminated = false;
            if (candidates.size() > 0) {
              for (Value candidate : candidates) {
                if (dominatorTree.dominatedBy(block, candidate.definition.getBlock())
                    && shareCatchHandlers(instruction, candidate.definition)) {
                  instruction.outValue().replaceUsers(candidate);
                  candidate.uniquePhiUsers().forEach(Phi::removeTrivialPhi);
                  eliminated = true;
                  iterator.removeOrReplaceByDebugLocalRead();
                  break; // Don't try any more candidates.
                }
              }
            }
            if (!eliminated) {
              instructionToValue.put(equivalence.wrap(instruction), instruction.outValue());
            }
          }
        }
      }
    }
    code.returnMarkingColor(noCandidate);
    code.removeRedundantBlocks();
    assert code.isConsistentSSA(appView);
  }

  private static class CSEExpressionEquivalence extends Equivalence<Instruction> {

    private final InternalOptions options;

    private CSEExpressionEquivalence(InternalOptions options) {
      this.options = options;
    }

    @Override
    protected boolean doEquivalent(Instruction a, Instruction b) {
      // Some Dalvik VMs incorrectly handle Cmp instructions which leads to a requirement
      // that we do not perform common subexpression elimination for them. See comment on
      // canHaveCmpLongBug for details.
      if (a.isCmp() && options.canHaveCmpLongBug()) {
        return false;
      }
      // Note that we don't consider positions because CSE can at most remove an instruction.
      if (!a.identicalNonValueNonPositionParts(b)) {
        return false;
      }
      // For commutative binary operations any order of in-values are equal.
      if (a.isBinop() && a.asBinop().isCommutative()) {
        Value a0 = a.inValues().get(0);
        Value a1 = a.inValues().get(1);
        Value b0 = b.inValues().get(0);
        Value b1 = b.inValues().get(1);
        return (identicalValue(a0, b0) && identicalValue(a1, b1))
            || (identicalValue(a0, b1) && identicalValue(a1, b0));
      } else {
        // Compare all in-values.
        assert a.inValues().size() == b.inValues().size();
        for (int i = 0; i < a.inValues().size(); i++) {
          if (!identicalValue(a.inValues().get(i), b.inValues().get(i))) {
            return false;
          }
        }
        return true;
      }
    }

    @Override
    protected int doHash(Instruction instruction) {
      final int prime = 29;
      int hash = instruction.getClass().hashCode();
      if (instruction.isBinop()) {
        Binop binop = instruction.asBinop();
        Value in0 = instruction.inValues().get(0);
        Value in1 = instruction.inValues().get(1);
        if (binop.isCommutative()) {
          hash += hash * prime + getHashCode(in0) * getHashCode(in1);
        } else {
          hash += hash * prime + getHashCode(in0);
          hash += hash * prime + getHashCode(in1);
        }
        return hash;
      } else {
        for (Value value : instruction.inValues()) {
          hash += hash * prime + getHashCode(value);
        }
      }
      return hash;
    }

    private static boolean identicalValue(Value a, Value b) {
      if (a.equals(b)) {
        return true;
      }
      if (a.isConstNumber() && b.isConstNumber()) {
        // Do not take assumption that constants are canonicalized.
        return a.definition.identicalNonValueNonPositionParts(b.definition);
      }
      return false;
    }

    private static int getHashCode(Value a) {
      if (a.isConstNumber()) {
        // Do not take assumption that constants are canonicalized.
        return Long.hashCode(a.definition.asConstNumber().getRawValue());
      }
      return a.hashCode();
    }
  }

  private boolean shareCatchHandlers(Instruction i0, Instruction i1) {
    if (!i0.instructionTypeCanThrow()) {
      assert !i1.instructionTypeCanThrow();
      return true;
    }
    assert i1.instructionTypeCanThrow();
    // TODO(sgjesse): This could be even better by checking for the exceptions thrown, e.g. div
    // and rem only ever throw ArithmeticException.
    CatchHandlers<BasicBlock> ch0 = i0.getBlock().getCatchHandlers();
    CatchHandlers<BasicBlock> ch1 = i1.getBlock().getCatchHandlers();
    return ch0.equals(ch1);
  }

  private boolean isCSEInstructionCandidate(Instruction instruction) {
    return (instruction.isBinop()
            || instruction.isUnop()
            || instruction.isInstanceOf()
            || instruction.isCheckCast())
        && instruction.getLocalInfo() == null
        && !instruction.hasInValueWithLocalInfo();
  }

  private boolean hasCSECandidate(IRCode code, int noCandidate) {
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        if (isCSEInstructionCandidate(instruction)) {
          return true;
        }
      }
      block.mark(noCandidate);
    }
    return false;
  }
}
