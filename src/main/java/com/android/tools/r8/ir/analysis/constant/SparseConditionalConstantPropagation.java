// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of Sparse Conditional Constant Propagation from the paper of Wegman and Zadeck
 * "Constant Propagation with Conditional Branches".
 * https://www.cs.utexas.edu/users/lin/cs380c/wegman.pdf
 */
public class SparseConditionalConstantPropagation extends CodeRewriterPass<AppInfo> {

  private final IRCode code;
  private final Map<Value, LatticeElement> mapping = new HashMap<>();
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private final Deque<Value> ssaEdges = new LinkedList<>();
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private final Deque<BasicBlock> flowEdges = new LinkedList<>();

  private final BitSet[] executableFlowEdges;
  private final BitSet visitedBlocks;

  public SparseConditionalConstantPropagation(AppView<?> appView, IRCode code) {
    super(appView);
    this.code = code;
    int maxBlockNumber = code.getCurrentBlockNumber() + 1;
    executableFlowEdges = new BitSet[maxBlockNumber];
    visitedBlocks = new BitSet(maxBlockNumber);
  }

  @Override
  protected String getTimingId() {
    return "SparseConditionalConstantPropagation";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    BasicBlock firstBlock = code.entryBlock();
    visitInstructions(firstBlock);

    while (!flowEdges.isEmpty() || !ssaEdges.isEmpty()) {
      while (!flowEdges.isEmpty()) {
        BasicBlock block = flowEdges.poll();
        for (Phi phi : block.getPhis()) {
          visitPhi(phi);
        }
        if (!visitedBlocks.get(block.getNumber())) {
          visitInstructions(block);
        }
      }
      while (!ssaEdges.isEmpty()) {
        Value value = ssaEdges.poll();
        for (Phi phi : value.uniquePhiUsers()) {
          visitPhi(phi);
        }
        for (Instruction user : value.uniqueUsers()) {
          BasicBlock userBlock = user.getBlock();
          if (visitedBlocks.get(userBlock.getNumber())) {
            visitInstruction(user);
          }
        }
      }
    }
    rewriteConstants();
    assert code.isConsistentSSA(appView);
    return CodeRewriterResult.NONE;
  }

  private void rewriteConstants() {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    List<BasicBlock> blockToAnalyze = new ArrayList<>();
    mapping.entrySet().stream()
        .filter(entry -> entry.getValue().isConst())
        .forEach(
            entry -> {
              Value value = entry.getKey();
              ConstNumber evaluatedConst = entry.getValue().asConst().getConstNumber();
              if (value.definition != evaluatedConst) {
                value.addAffectedValuesTo(affectedValues);
                if (value.isPhi()) {
                  // D8 relies on dead code removal to get rid of the dead phi itself.
                  if (value.hasAnyUsers()) {
                    BasicBlock block = value.asPhi().getBlock();
                    blockToAnalyze.add(block);
                    // Create a new constant, because it can be an existing constant that flow
                    // directly
                    // into the phi.
                    ConstNumber newConst = ConstNumber.copyOf(code, evaluatedConst);
                    InstructionListIterator iterator = block.listIterator(code);
                    Instruction inst = iterator.nextUntil(i -> !i.isMoveException());
                    newConst.setPosition(inst.getPosition());
                    if (!inst.isDebugPosition()) {
                      iterator.previous();
                    }
                    iterator.add(newConst);
                    value.replaceUsers(newConst.outValue());
                  }
                } else {
                  BasicBlock block = value.definition.getBlock();
                  InstructionListIterator iterator = block.listIterator(code);
                  iterator.nextUntil(i -> i == value.definition);
                  iterator.replaceCurrentInstruction(evaluatedConst);
                }
              }
            });
    for (BasicBlock block : blockToAnalyze) {
      block.deduplicatePhis();
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    code.removeAllDeadAndTrivialPhis();
    code.removeRedundantBlocks();
  }

  private LatticeElement getLatticeElement(Value value) {
    return mapping.getOrDefault(value, Top.getInstance());
  }

  private void setLatticeElement(Value value, LatticeElement element) {
    mapping.put(value, element);
  }

  private void visitPhi(Phi phi) {
    BasicBlock phiBlock = phi.getBlock();
    int phiBlockNumber = phiBlock.getNumber();
    LatticeElement element = Top.getInstance();
    List<BasicBlock> predecessors = phiBlock.getPredecessors();
    int size = predecessors.size();
    for (int i = 0; i < size; i++) {
      BasicBlock predecessor = predecessors.get(i);
      if (isExecutableEdge(predecessor.getNumber(), phiBlockNumber)) {
        element = element.meet(getLatticeElement(phi.getOperand(i)));
        // bottom lattice can no longer be changed, thus no need to continue
        if (element.isBottom()) {
          break;
        }
      }
    }
    if (!element.isTop()) {
      LatticeElement currentPhiElement = getLatticeElement(phi);
      if (currentPhiElement.meet(element) != currentPhiElement) {
        ssaEdges.add(phi);
        setLatticeElement(phi, element);
      }
    }
  }

  private void visitInstructions(BasicBlock block) {
    for (Instruction instruction : block.getInstructions()) {
      visitInstruction(instruction);
    }
    visitedBlocks.set(block.getNumber());
  }

  private void visitInstruction(Instruction instruction) {
    if (instruction.outValue() != null && !instruction.isDebugLocalUninitialized()) {
      LatticeElement element = instruction.evaluate(code, this::getLatticeElement);
      LatticeElement currentLattice = getLatticeElement(instruction.outValue());
      if (currentLattice.meet(element) != currentLattice) {
        setLatticeElement(instruction.outValue(), element);
        ssaEdges.add(instruction.outValue());
      }
    }
    if (instruction.isJumpInstruction()) {
      addFlowEdgesForJumpInstruction(instruction.asJumpInstruction());
    }
  }

  private void addFlowEdgesForJumpInstruction(JumpInstruction jumpInstruction) {
    BasicBlock jumpInstBlock = jumpInstruction.getBlock();
    int jumpInstBlockNumber = jumpInstBlock.getNumber();
    if (jumpInstruction.isIf()) {
      If theIf = jumpInstruction.asIf();
      if (theIf.isZeroTest()) {
        LatticeElement element = getLatticeElement(theIf.inValues().get(0));
        if (element.isConst()) {
          BasicBlock target = theIf.targetFromCondition(element.asConst().getConstNumber());
          if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
            setExecutableEdge(jumpInstBlockNumber, target.getNumber());
            flowEdges.add(target);
          }
          return;
        }
      } else {
        LatticeElement leftElement = getLatticeElement(theIf.inValues().get(0));
        LatticeElement rightElement = getLatticeElement(theIf.inValues().get(1));
        if (leftElement.isConst() && rightElement.isConst()) {
          ConstNumber leftNumber = leftElement.asConst().getConstNumber();
          ConstNumber rightNumber = rightElement.asConst().getConstNumber();
          BasicBlock target = theIf.targetFromCondition(leftNumber, rightNumber);
          if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
            setExecutableEdge(jumpInstBlockNumber, target.getNumber());
            flowEdges.add(target);
          }
          return;
        }
        assert !leftElement.isTop();
        assert !rightElement.isTop();
      }
    } else if (jumpInstruction.isIntSwitch()) {
      IntSwitch switchInst = jumpInstruction.asIntSwitch();
      LatticeElement switchElement = getLatticeElement(switchInst.value());
      if (switchElement.isConst()) {
        BasicBlock target = switchInst.getKeyToTargetMap()
            .get(switchElement.asConst().getIntValue());
        if (target == null) {
          target = switchInst.fallthroughBlock();
        }
        assert target != null;
        setExecutableEdge(jumpInstBlockNumber, target.getNumber());
        flowEdges.add(target);
        return;
      }
    } else if (jumpInstruction.isStringSwitch()) {
      StringSwitch switchInst = jumpInstruction.asStringSwitch();
      LatticeElement switchElement = getLatticeElement(switchInst.value());
      if (switchElement.isConst()) {
        // There is currently no constant propagation for strings, so it must be null.
        assert switchElement.asConst().getConstNumber().isZero();
        BasicBlock target = switchInst.fallthroughBlock();
        setExecutableEdge(jumpInstBlockNumber, target.getNumber());
        flowEdges.add(target);
        return;
      }
    } else {
      assert jumpInstruction.isGoto() || jumpInstruction.isReturn() || jumpInstruction.isThrow();
    }

    for (BasicBlock dst : jumpInstBlock.getSuccessors()) {
      if (!isExecutableEdge(jumpInstBlockNumber, dst.getNumber())) {
        setExecutableEdge(jumpInstBlockNumber, dst.getNumber());
        flowEdges.add(dst);
      }
    }
  }

  private void setExecutableEdge(int from, int to) {
    BitSet previousExecutable = executableFlowEdges[to];
    if (previousExecutable == null) {
      previousExecutable = new BitSet(executableFlowEdges.length);
      executableFlowEdges[to] = previousExecutable;
    }
    previousExecutable.set(from);
  }

  private boolean isExecutableEdge(int from, int to) {
    BitSet previousExecutable = executableFlowEdges[to];
    if (previousExecutable == null) {
      return false;
    }
    return previousExecutable.get(from);
  }
}
