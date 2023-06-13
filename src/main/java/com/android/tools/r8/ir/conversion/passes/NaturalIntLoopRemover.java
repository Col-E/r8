// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Set;

/**
 * The NaturalIntLoopRemover detects natural loops on an integer iterator and computes the exact
 * number of iterations if possible. If the number of iterations is known to be 1, it transforms the
 * loop into a straight-line single iteration of the loop body.
 *
 * <p>This relies on the CodeRewriter to rewrite known array length upfront. Generally this can
 * pattern match fori and for loops with any initial value and increment, but this should be
 * extended for while loop support.
 */
public class NaturalIntLoopRemover extends CodeRewriterPass<AppInfo> {

  public NaturalIntLoopRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getTimingId() {
    return "NaturalIntLoopRemover";
  }

  @Override
  protected void rewriteCode(IRCode code) {
    boolean loopRemoved = false;
    for (BasicBlock comparisonBlockCandidate : code.blocks) {
      if (isComparisonBlock(comparisonBlockCandidate)) {
        loopRemoved |= tryRemoveLoop(code, comparisonBlockCandidate.exit().asIf());
      }
    }
    if (loopRemoved) {
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();
      assert code.isConsistentSSA(appView);
    }
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return appView.options().enableLoopUnrolling;
  }

  private boolean isComparisonBlock(BasicBlock comparisonBlockCandidate) {
    if (!comparisonBlockCandidate.exit().isIf()
        || comparisonBlockCandidate.exit().asIf().isZeroTest()) {
      return false;
    }
    for (Instruction instruction : comparisonBlockCandidate.getInstructions()) {
      if (instruction.isIf()) {
        return true;
      }
      if (!(instruction.isConstNumber())) {
        return false;
      }
    }
    throw new Unreachable();
  }

  private boolean tryRemoveLoop(IRCode code, If comparison) {
    Phi loopPhi = computeLoopPhi(comparison);
    if (loopPhi == null) {
      return false;
    }

    NaturalIntLoopWithKnowIterations.Builder builder =
        NaturalIntLoopWithKnowIterations.builder(comparison);

    if (!analyzeLoopIterator(comparison, loopPhi, builder)) {
      return false;
    }

    Set<BasicBlock> loopBody = computeLoopBody(builder.getBackPredecessor(), comparison.getBlock());
    if (loopBody == null) {
      return false;
    }
    if (loopBody.contains(builder.getLoopEntry())) {
      assert false;
      return false;
    }
    builder.setLoopBody(loopBody);

    if (!analyzeLoopExit(loopBody, comparison, builder)) {
      return false;
    }
    if (!analyzePhiUses(loopBody, comparison, builder)) {
      return false;
    }

    NaturalIntLoopWithKnowIterations loop = builder.build();

    if (loop.has1Iteration()) {
      loop.remove1IterationLoop(code);
      return true;
    }
    return false;
  }

  /**
   * The loop unroller removes phis corresponding to the loop backjump. There are three scenarios:
   * (1) The loop has a single exit point analyzed, phis used outside the loop are replaced by the
   *     value at the end of the loop body.
   * (2) The phis are unused outside the loop, and they are simply removed.
   * (3) The loop has multiple exits and the phis are used outside the loop, this would require
   *     dealing with complex merge point and postponing phis after the loop, we bail out.
   */
  private boolean analyzePhiUses(
      Set<BasicBlock> loopBody, If comparison, NaturalIntLoopWithKnowIterations.Builder builder) {
    // Check for single exit scenario.
    Set<BasicBlock> successors = Sets.newIdentityHashSet();
    for (BasicBlock basicBlock : loopBody) {
      successors.addAll(basicBlock.getSuccessors());
    }
    successors.removeAll(loopBody);
    if (successors.size() == 1) {
      assert successors.iterator().next() == builder.getLoopExit();
      return true;
    }
    // Check phis are unused outside the loop.
    for (Phi phi : comparison.getBlock().getPhis()) {
      for (Instruction use : phi.uniqueUsers()) {
        if (!loopBody.contains(use.getBlock())) {
          return false;
        }
      }
      for (Phi phiUse : phi.uniquePhiUsers()) {
        if (!loopBody.contains(phiUse.getBlock())) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * Verifies the loop is well formed: the comparison on the int iterator should jump to a loop exit
   * on one side and to the loop body on the other side.
   */
  private boolean analyzeLoopExit(
      Set<BasicBlock> loopBody, If comparison, NaturalIntLoopWithKnowIterations.Builder builder) {
    if (loopBody.contains(comparison.getTrueTarget())) {
      if (loopBody.contains(comparison.fallthroughBlock())) {
        return false;
      }
      builder.setLoop(comparison.fallthroughBlock(), comparison.getTrueTarget());
    } else {
      if (!loopBody.contains(comparison.fallthroughBlock())) {
        return false;
      }
      builder.setLoop(comparison.getTrueTarget(), comparison.fallthroughBlock());
    }
    return true;
  }

  /**
   * Analyze the int iterator so that it is initialized with a constant int value, and each
   * iteration of the loop increment the iterator by one of the following: i + cst, cst + i or i -
   * cst.
   */
  private boolean analyzeLoopIterator(
      If comparison, Phi loopPhi, NaturalIntLoopWithKnowIterations.Builder builder) {
    for (int i = 0; i < loopPhi.getOperands().size(); i++) {
      Value operand = loopPhi.getOperand(i);
      if (operand.isPhi()) {
        return false;
      }
      BasicBlock predecessor = comparison.getBlock().getPredecessors().get(i);
      if (operand.isConstNumber()) {
        // Initial value of the int iterator.
        if (!operand.getType().isInt() || builder.getLoopEntry() != null) {
          return false;
        }
        builder.setLoopEntry(predecessor);
        builder.setInitCounter(operand.definition.asConstNumber().getIntValue());
      } else if (operand.definition.isAdd()) {
        // Increment of the int iterator of type i + cst or cst + i.
        if (builder.getBackPredecessor() != null) {
          return false;
        }
        builder.setBackPredecessor(predecessor);
        boolean metPhiOperand = false;
        for (Value inValue : operand.definition.inValues()) {
          if (inValue.isConstNumber() && inValue.getType().isInt()) {
            int counterIncrement = inValue.definition.asConstNumber().getIntValue();
            if (counterIncrement == 0 || builder.getCounterIncrement() != 0) {
              return false;
            }
            builder.setCounterIncrement(counterIncrement);
          } else if (inValue == loopPhi) {
            if (metPhiOperand) {
              return false;
            }
            metPhiOperand = true;
          } else {
            return false;
          }
        }
      } else if (operand.definition.isSub()) {
        // Increment of the int iterator of type i - cst.
        if (builder.getBackPredecessor() != null) {
          return false;
        }
        builder.setBackPredecessor(predecessor);
        Sub sub = operand.definition.asSub();
        if (sub.leftValue() != loopPhi) {
          return false;
        }
        Value subValue = sub.rightValue();
        if (subValue.isConstNumber() && subValue.getType().isInt()) {
          assert builder.getCounterIncrement() == 0;
          int counterIncrement = -subValue.definition.asConstNumber().getIntValue();
          if (counterIncrement == 0) {
            return false;
          }
          builder.setCounterIncrement(counterIncrement);
        } else {
          return false;
        }
      } else {
        return false;
      }
    }
    assert builder.getLoopEntry() != null;
    assert builder.getLoopEntry().exit().isGoto();
    assert builder.getBackPredecessor() != null;
    assert builder.getBackPredecessor().exit().isGoto();
    assert builder.getCounterIncrement() != 0;
    return true;
  }

  /**
   * Analyze the loop comparison so that it compares a loopPhi with a constant, else answers null.
   */
  private Phi computeLoopPhi(If comparison) {
    Phi loopPhi = null;
    if (comparison.rhs().isConstant() && comparison.lhs().isPhi()) {
      loopPhi = comparison.lhs().asPhi();
    } else if (comparison.lhs().isConstant() && comparison.rhs().isPhi()) {
      loopPhi = comparison.rhs().asPhi();
    }
    if (loopPhi == null) {
      return null;
    }
    if (loopPhi.getOperands().size() != 2) {
      return null;
    }
    if (loopPhi.getBlock() != comparison.getBlock()) {
      return null;
    }
    return loopPhi;
  }

  /**
   * Natural int loop structure and terminology. <code>
   *         v
   *     Loop Entry
   *     int i = 0;    v < < < < < < < < < < < <
   *         v         v                       ^
   *       Comparison Block                    ^
   *       if (i < constant)                   ^
   *       v               v                   ^
   *   Loop Exit         Loop Body Entry       ^
   *       v             i++;                  ^
   *   Method Exit         v                   ^
   *       v               > > > > > > > > > > ^
   * </code>
   */
  static class NaturalIntLoopWithKnowIterations {

    private final int initCounter;
    private final int counterIncrement;
    private final If comparison;
    private final BasicBlock loopExit;
    private final BasicBlock loopBodyEntry;
    private final BasicBlock backPredecessor;
    private final Set<BasicBlock> loopBody;

    NaturalIntLoopWithKnowIterations(
        int initCounter,
        int counterIncrement,
        If comparison,
        BasicBlock loopExit,
        BasicBlock loopBodyEntry,
        BasicBlock backPredecessor,
        Set<BasicBlock> loopBody) {
      this.initCounter = initCounter;
      this.counterIncrement = counterIncrement;
      this.comparison = comparison;
      this.loopExit = loopExit;
      this.loopBodyEntry = loopBodyEntry;
      this.backPredecessor = backPredecessor;
      this.loopBody = loopBody;
    }

    static class Builder {

      private int initCounter;
      private int counterIncrement;
      private final If comparison;
      private BasicBlock loopExit;
      private BasicBlock loopBodyEntry;
      private BasicBlock loopEntry;
      private BasicBlock backPredecessor;
      private Set<BasicBlock> loopBody;

      Builder(If comparison) {
        this.comparison = comparison;
      }

      public void setInitCounter(int initCounter) {
        this.initCounter = initCounter;
      }

      public int getCounterIncrement() {
        return counterIncrement;
      }

      public void setCounterIncrement(int counterIncrement) {
        this.counterIncrement = counterIncrement;
      }

      public BasicBlock getLoopEntry() {
        return loopEntry;
      }

      public void setLoopEntry(BasicBlock loopEntry) {
        this.loopEntry = loopEntry;
      }

      public BasicBlock getBackPredecessor() {
        return backPredecessor;
      }

      public void setBackPredecessor(BasicBlock backPredecessor) {
        this.backPredecessor = backPredecessor;
      }

      public void setLoop(BasicBlock loopExit, BasicBlock loopBodyEntry) {
        this.loopExit = loopExit;
        this.loopBodyEntry = loopBodyEntry;
      }

      public BasicBlock getLoopExit() {
        return loopExit;
      }

      public void setLoopBody(Set<BasicBlock> loopBody) {
        this.loopBody = loopBody;
      }

      public NaturalIntLoopWithKnowIterations build() {
        return new NaturalIntLoopWithKnowIterations(
            initCounter,
            counterIncrement,
            comparison,
            loopExit,
            loopBodyEntry,
            backPredecessor,
            loopBody);
      }
    }

    static Builder builder(If comparison) {
      return new Builder(comparison);
    }

    private BasicBlock target(int phiValue) {
      if (comparison.rhs().isConstNumber()) {
        int comp = comparison.rhs().getDefinition().asConstNumber().getIntValue();
        return comparison.targetFromCondition(Integer.signum(phiValue - comp));
      }
      int comp = comparison.lhs().getDefinition().asConstNumber().getIntValue();
      return comparison.targetFromCondition(Integer.signum(comp - phiValue));
    }

    public boolean has1Iteration() {
      return target(initCounter) == loopBodyEntry
          && target(initCounter + counterIncrement) == loopExit;
    }

    private void remove1IterationLoop(IRCode code) {
      BasicBlock comparisonBlock = comparison.getBlock();
      updatePhis(comparisonBlock);
      patchControlFlow(code, comparisonBlock);
    }

    private void patchControlFlow(IRCode code, BasicBlock comparisonBlock) {
      assert loopExit.getPhis().isEmpty(); // Edges should be split.
      comparisonBlock.replaceLastInstruction(new Goto(loopBodyEntry), code);
      comparisonBlock.removeSuccessor(loopExit);

      backPredecessor.replaceSuccessor(comparisonBlock, loopExit);
      backPredecessor.replaceLastInstruction(new Goto(loopExit), code);
      comparisonBlock.removePredecessor(backPredecessor, Sets.newIdentityHashSet());
      loopExit.replacePredecessor(comparisonBlock, backPredecessor);
    }

    private void updatePhis(BasicBlock comparisonBlock) {
      int backIndex = comparisonBlock.getPredecessors().indexOf(backPredecessor);
      for (Phi phi : comparisonBlock.getPhis()) {
        Value loopEntryValue = phi.getOperand(1 - backIndex);
        Value loopExitValue = phi.getOperand(backIndex);
        for (Instruction uniqueUser : phi.uniqueUsers()) {
          if (loopBody.contains(uniqueUser.getBlock())) {
            uniqueUser.replaceValue(phi, loopEntryValue);
          } else {
            uniqueUser.replaceValue(phi, loopExitValue);
          }
        }
        for (Phi phiUser : phi.uniquePhiUsers()) {
          if (loopBody.contains(phiUser.getBlock())) {
            phiUser.replaceOperand(phi, loopEntryValue);
          } else {
            phiUser.replaceOperand(phi, loopExitValue);
          }
        }
      }
    }
  }

  private Set<BasicBlock> computeLoopBody(BasicBlock backPredecessor, BasicBlock comparisonBlock) {
    WorkList<BasicBlock> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(backPredecessor);
    workList.markAsSeen(comparisonBlock);
    while (!workList.isEmpty()) {
      BasicBlock basicBlock = workList.next();
      if (basicBlock.isEntry()) {
        // This can happen in loops with multiple entries (Duff device, etc.).
        // Such loops are not generated by javac so we assume they are uncommon.
        return null;
      }
      for (BasicBlock predecessor : basicBlock.getPredecessors()) {
        workList.addIfNotSeen(predecessor);
      }
    }
    return workList.getSeenSet();
  }
}
