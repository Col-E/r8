// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SplitBranch extends CodeRewriterPass<AppInfo> {

  private static final boolean ALLOW_PARTIAL_REWRITE = true;

  public SplitBranch(AppView<?> appView) {
    super(appView);
  }

  @Override
  String getTimingId() {
    return "SplitBranch";
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
      // We need to verify any instruction in between the if and the chain of phis is empty or just
      // a constant used in the If instruction (we could duplicate instruction, but the common case
      // is empty).
      // Then we can redirect any known value. This can lead to dead code.
      If theIf = block.exit().asIf();
      Set<Phi> allowedPhis = getAllowedPhis(nonConstNumberOperand(theIf).asPhi());
      Set<Phi> foundPhis = new LinkedHashSet<>();
      WorkList.newIdentityWorkList(block)
          .process(
              (current, workList) -> {
                if (current.getInstructions().size() > 1) {
                  // We allow a single instruction, which is the constant used exclusively in the
                  // if. This is run before constant canonicalization.
                  if (theIf.isZeroTest()
                      || current.getInstructions().size() != 2
                      || !current.entry().isConstNumber()) {
                    return;
                  }
                  Value value = current.entry().outValue();
                  if (value.hasPhiUsers()
                      || value.uniqueUsers().size() > 1
                      || (value.uniqueUsers().size() == 1
                          && value.uniqueUsers().iterator().next() != theIf)) {
                    return;
                  }
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
      for (Phi phi : foundPhis) {
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

  private boolean isNumberAgainstConstNumberIf(If theIf) {
    if (!(theIf.lhs().getType().isInt() || theIf.lhs().getType().isFloat())) {
      return false;
    }
    if (theIf.isZeroTest()) {
      return true;
    }
    assert theIf.lhs().getType() == theIf.rhs().getType();
    return theIf.lhs().isConstNumber() || theIf.rhs().isConstNumber();
  }

  private Value nonConstNumberOperand(If theIf) {
    return theIf.isZeroTest()
        ? theIf.lhs()
        : (theIf.lhs().isConstNumber() ? theIf.rhs() : theIf.lhs());
  }

  private List<BasicBlock> computeCandidates(IRCode code) {
    List<BasicBlock> candidates = new ArrayList<>();
    for (BasicBlock block : ListUtils.filter(code.blocks, block -> block.exit().isIf())) {
      If theIf = block.exit().asIf();
      if (!isNumberAgainstConstNumberIf(theIf)) {
        continue;
      }
      Value nonConstNumberOperand = nonConstNumberOperand(theIf);
      if (isNumberAgainstConstNumberIf(theIf)
          && nonConstNumberOperand.isPhi()
          && nonConstNumberOperand.hasSingleUniqueUser()
          && !nonConstNumberOperand.hasPhiUsers()) {
        candidates.add(block);
      }
    }
    return candidates;
  }

  private BasicBlock targetFromCondition(If theIf, ConstNumber constForPhi) {
    if (theIf.isZeroTest()) {
      return theIf.targetFromCondition(constForPhi);
    }
    if (theIf.lhs().isConstNumber()) {
      return theIf.targetFromCondition(
          theIf.lhs().getConstInstruction().asConstNumber(), constForPhi);
    }
    assert theIf.rhs().isConstNumber();
    return theIf.targetFromCondition(
        constForPhi, theIf.rhs().getConstInstruction().asConstNumber());
  }

  private void recordNewTargetForGoto(
      Value value, BasicBlock basicBlock, If theIf, Map<Goto, BasicBlock> newTargets) {
    // The GoTo at the end of basicBlock should target the phiBlock, and should target instead
    // the correct if destination.
    assert basicBlock.exit().isGoto();
    assert value.isConstant();
    BasicBlock newTarget = targetFromCondition(theIf, value.getConstInstruction().asConstNumber());
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
