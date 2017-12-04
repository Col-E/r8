// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Value;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.BitSet;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

/**
 * Implementation of Sparse Conditional Constant Propagation from the paper of Wegman and Zadeck
 * "Constant Propagation with Conditional Branches".
 * https://www.cs.utexas.edu/users/lin/cs380c/wegman.pdf
 */
public class SparseConditionalConstantPropagation {

  private IRCode code;
  private Map<Value, LatticeElement> mapping = new HashMap<>();
  private Deque<Value> ssaEdges = new LinkedList<>();
  private Deque<BasicBlock> flowEdges = new LinkedList<>();
  private final int nextBlockNumber;
  private final BitSet[] executableFlowEdges;
  private final BitSet visitedBlocks;

  public SparseConditionalConstantPropagation(IRCode code) {
    this.code = code;
    nextBlockNumber = code.getHighestBlockNumber() + 1;
    executableFlowEdges = new BitSet[nextBlockNumber];
    visitedBlocks = new BitSet(nextBlockNumber);
  }

  public void run() {

    BasicBlock firstBlock = code.blocks.get(0);
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
    rewriteCode();
    assert code.isConsistentSSA();
  }

  private static class PhiEquivalence extends Equivalence<Phi> {

    @Override
    protected boolean doEquivalent(Phi a, Phi b) {
      assert a.getBlock() == b.getBlock();
      for (int i = 0; i < a.getOperands().size(); i++) {
        if (a.getOperand(i) != b.getOperand(i)) {
          return false;
        }
      }
      return true;
    }

    @Override
    protected int doHash(Phi phi) {
      int hash = 0;
      for (Value value : phi.getOperands()) {
        hash = hash * 13 + value.hashCode();
      }
      return hash;
    }
  }

  private void rewriteCode() {
    mapping.entrySet().stream()
        .filter((entry) -> entry.getValue().isConst())
        .forEach((entry) -> {
          Value value = entry.getKey();
          ConstNumber evaluatedConst = entry.getValue().asConst().getConstNumber();
          if (value.definition != evaluatedConst) {
            if (value.isPhi()) {
              BasicBlock block = value.asPhi().getBlock();
              // Create a new constant, because it can be an existing constant that flow directly
              // into the phi.
              ConstNumber newConst = ConstNumber.copyOf(code, evaluatedConst);
              InstructionListIterator iterator = block.listIterator();
              Instruction inst = iterator.nextUntil((i) -> !i.isMoveException());
              newConst.setPosition(inst.getPosition());
              if (!inst.isDebugPosition()) {
                iterator.previous();
              }
              iterator.add(newConst);
              value.replaceUsers(newConst.outValue());
            } else {
              BasicBlock block = value.definition.getBlock();
              InstructionListIterator iterator = block.listIterator();
              Instruction toReplace = iterator.nextUntil((i) -> i == value.definition);
              iterator.replaceCurrentInstruction(evaluatedConst);
            }
          }
        });

    for (BasicBlock block : code.blocks) {
      PhiEquivalence equivalence = new PhiEquivalence();
      HashMap<Wrapper<Phi>, Phi> phis = new HashMap<>();
      for (Phi phi : block.getPhis()) {
        Wrapper<Phi> key = equivalence.wrap(phi);
        Phi replacement = phis.get(key);
        if (replacement != null) {
          phi.replaceUsers(replacement);
        } else {
          phis.put(key, phi);
        }
      }
    }
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
    for (int i = 0; i < phiBlock.getPredecessors().size(); i++) {
      BasicBlock predecessor = phiBlock.getPredecessors().get(i);
      if (isExecutableEdge(predecessor.getNumber(), phiBlockNumber)) {
        element = element.meet(getLatticeElement(phi.getOperand(i)));
      }
    }
    LatticeElement currentPhiElement = getLatticeElement(phi);
    if (!element.isTop() && currentPhiElement.meet(element) != currentPhiElement) {
      ssaEdges.add(phi);
      setLatticeElement(phi, element);
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
      LatticeElement element = instruction.evaluate(code, mapping);
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
          BasicBlock target = theIf.targetFromCondition(element.asConst().getBranchCondition());
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
          long leftValue = leftElement.asConst().getConstNumber().getIntValue();
          long rightValue = rightElement.asConst().getConstNumber().getIntValue();
          BasicBlock target = theIf.targetFromCondition(leftValue - rightValue);
          if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
            setExecutableEdge(jumpInstBlockNumber, target.getNumber());
            flowEdges.add(target);
          }
          return;
        }
        assert !leftElement.isTop();
        assert !rightElement.isTop();
      }
    } else if (jumpInstruction.isSwitch()) {
      Switch switchInst = jumpInstruction.asSwitch();
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
      previousExecutable = new BitSet(nextBlockNumber);
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
