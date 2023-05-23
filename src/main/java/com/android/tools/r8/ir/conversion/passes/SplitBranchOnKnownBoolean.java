// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SplitBranchOnKnownBoolean extends CodeRewriterPass<AppInfo> {

  private static final boolean ALLOW_PARTIAL_REWRITE = true;

  public SplitBranchOnKnownBoolean(AppView<?> appView) {
    super(appView);
  }

  @Override
  String getTimingId() {
    return "SplitBranchOnKnownBoolean";
  }

  @Override
  boolean shouldRewriteCode(ProgramMethod method, IRCode code) {
    return true;
  }

  /**
   * Simplify Boolean branches for example: <code>
   * boolean b = i == j; if (b) { ... } else { ... }
   * </code> ends up first creating a branch for the boolean b, then a second branch on b. D8/R8
   * rewrites to: <code>
   * if (i == j) { ... } else { ... }
   * </code> More complex control flow are also supported to some extent, including cases where the
   * input of the second branch comes from a set of dependent phis, and a subset of the inputs are
   * known boolean values.
   */
  @Override
  void rewriteCode(ProgramMethod method, IRCode code) {
    List<BasicBlock> candidates = computeCandidates(code);
    if (candidates.isEmpty()) {
      return;
    }
    Map<Goto, BasicBlock> newTargets = findGotosToRetarget(candidates);
    if (newTargets.isEmpty()) {
      return;
    }
    retargetGotos(newTargets);
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    affectedValues.addAll(code.removeUnreachableBlocks());
    code.removeAllDeadAndTrivialPhis(affectedValues);
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    if (ALLOW_PARTIAL_REWRITE) {
      code.splitCriticalEdges();
    }
    assert code.isConsistentSSA(appView);
  }

  private void retargetGotos(Map<Goto, BasicBlock> newTargets) {
    newTargets.forEach(
        (goTo, newTarget) -> {
          BasicBlock initialTarget = goTo.getTarget();
          for (Phi phi : initialTarget.getPhis()) {
            int index = initialTarget.getPredecessors().indexOf(goTo.getBlock());
            phi.removeOperand(index);
          }
          goTo.setTarget(newTarget);
        });
  }

  private Map<Goto, BasicBlock> findGotosToRetarget(List<BasicBlock> candidates) {
    Map<Goto, BasicBlock> newTargets = new LinkedHashMap<>();
    for (BasicBlock block : candidates) {
      // We need to verify any instruction in between the if and the chain of phis is empty (we
      // could duplicate instruction, but the common case is empty).
      // Then we can redirect any known value. This can lead to dead code.
      If theIf = block.exit().asIf();
      Set<Phi> allowedPhis = getAllowedPhis(theIf.lhs().asPhi());
      Set<Phi> foundPhis = Sets.newIdentityHashSet();
      WorkList.newIdentityWorkList(block)
          .process(
              (current, workList) -> {
                if (current.getInstructions().size() > 1) {
                  return;
                }
                if (current != block && !current.exit().isGoto()) {
                  return;
                }
                if (allowedPhis.containsAll(current.getPhis())) {
                  foundPhis.addAll(current.getPhis());
                } else {
                  return;
                }
                workList.addIfNotSeen(current.getPredecessors());
              });
      if (!ALLOW_PARTIAL_REWRITE) {
        for (Phi phi : foundPhis) {
          for (Value value : phi.getOperands()) {
            if (!value.isConstant() && !(value.isPhi() && foundPhis.contains(value.asPhi()))) {
              return newTargets;
            }
          }
        }
      }
      List<Phi> sortedFoundPhis = new ArrayList<>(foundPhis);
      sortedFoundPhis.sort(Phi::compareTo);
      for (Phi phi : sortedFoundPhis) {
        BasicBlock phiBlock = phi.getBlock();
        for (int i = 0; i < phi.getOperands().size(); i++) {
          Value value = phi.getOperand(i);
          if (value.isConstant()) {
            recordNewTargetForGoto(value, phiBlock.getPredecessors().get(i), theIf, newTargets);
          }
        }
      }
    }
    return newTargets;
  }

  private List<BasicBlock> computeCandidates(IRCode code) {
    List<BasicBlock> candidates = new ArrayList<>();
    for (BasicBlock block : ListUtils.filter(code.blocks, block -> block.entry().isIf())) {
      If theIf = block.exit().asIf();
      if (theIf.isZeroTest()
          && theIf.lhs().getType().isInt()
          && theIf.lhs().isPhi()
          && theIf.lhs().hasSingleUniqueUser()
          && !theIf.lhs().hasPhiUsers()) {
        candidates.add(block);
      }
    }
    return candidates;
  }

  private void recordNewTargetForGoto(
      Value value, BasicBlock basicBlock, If theIf, Map<Goto, BasicBlock> newTargets) {
    // The GoTo at the end of basicBlock should target the phiBlock, and should target instead
    // the correct if destination.
    assert basicBlock.exit().isGoto();
    assert value.isConstant();
    assert value.getType().isInt();
    assert theIf.isZeroTest();
    BasicBlock newTarget = theIf.targetFromCondition(value.getConstInstruction().asConstNumber());
    Goto aGoto = basicBlock.exit().asGoto();
    newTargets.put(aGoto, newTarget);
  }

  private Set<Phi> getAllowedPhis(Phi initialPhi) {
    WorkList<Phi> workList = WorkList.newIdentityWorkList(initialPhi);
    while (workList.hasNext()) {
      Phi phi = workList.next();
      for (Value operand : phi.getOperands()) {
        if (operand.isPhi()
            && (operand.uniqueUsers().isEmpty() || phi == initialPhi)
            && workList.getSeenSet().containsAll(operand.uniquePhiUsers())) {
          workList.addIfNotSeen(operand.asPhi());
        }
      }
    }
    return workList.getSeenSet();
  }
}
