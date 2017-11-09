// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Pop;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LoadStoreHelper {

  private final IRCode code;
  private final Map<Value, DexType> types;

  public LoadStoreHelper(IRCode code, Map<Value, DexType> types) {
    this.code = code;
    this.types = types;
  }

  public void insertLoadsAndStores() {
    // Insert per-instruction loads and stores.
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction current = it.next();
        current.insertLoadAndStores(it, this);
      }
    }
  }

  public void insertPhiMoves(CfRegisterAllocator allocator) {
    // Insert phi stores in all predecessors.
    for (BasicBlock block : code.blocks) {
      if (!block.getPhis().isEmpty()) {
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          List<Phi> phis = block.getPhis();
          List<PhiMove> moves = new ArrayList<>(phis.size());
          for (Phi phi : phis) {
            Value value = phi.getOperand(predIndex);
            if (allocator.getRegisterForValue(phi) != allocator.getRegisterForValue(value)) {
              moves.add(new PhiMove(phi, value));
            }
          }
          InstructionListIterator it = pred.listIterator(pred.getInstructions().size());
          it.previous();
          movePhis(moves, it);
        }
      }
    }
    code.blocks.forEach(BasicBlock::clearUserInfo);
  }

  private StackValue createStackValue(Value value, int height) {
    if (value.outType().isObject()) {
      return StackValue.forObjectType(types.get(value), height);
    }
    return StackValue.forNonObjectType(value.outType(), height);
  }

  private StackValue createStackValue(DexType type, int height) {
    if (type.isPrimitiveType()) {
      return StackValue.forNonObjectType(ValueType.fromDexType(type), height);
    }
    return StackValue.forObjectType(type, height);
  }

  public void loadInValues(Instruction instruction, InstructionListIterator it) {
    int topOfStack = 0;
    it.previous();
    for (Value value : instruction.inValues()) {
      StackValue stackValue = createStackValue(value, topOfStack++);
      add(load(stackValue, value), instruction, it);
      value.removeUser(instruction);
      instruction.replaceValue(value, stackValue);
    }
    it.next();
  }

  public void storeOutValue(Instruction instruction, InstructionListIterator it) {
    if (instruction.outValue() instanceof StackValue) {
      assert instruction.isConstInstruction();
      return;
    }
    StackValue newOutValue = createStackValue(instruction.outValue(), 0);
    Value oldOutValue = instruction.swapOutValue(newOutValue);
    add(new Store(oldOutValue, newOutValue), instruction, it);
  }

  public void popOutValue(Value value, Instruction instruction, InstructionListIterator it) {
    StackValue newOutValue = createStackValue(value, 0);
    instruction.swapOutValue(newOutValue);
    add(new Pop(newOutValue), instruction, it);
  }

  public void popOutType(DexType type, Instruction instruction, InstructionListIterator it) {
    StackValue newOutValue = createStackValue(type, 0);
    instruction.swapOutValue(newOutValue);
    add(new Pop(newOutValue), instruction, it);
  }

  private static class PhiMove {
    final Phi phi;
    final Value operand;

    public PhiMove(Phi phi, Value operand) {
      this.phi = phi;
      this.operand = operand;
    }
  }

  private void movePhis(List<PhiMove> moves, InstructionListIterator it) {
    // TODO(zerny): Accounting for non-interfering phis would lower the max stack size.
    int topOfStack = 0;
    List<StackValue> temps = new ArrayList<>(moves.size());
    for (PhiMove move : moves) {
      StackValue tmp = createStackValue(move.phi, topOfStack++);
      add(load(tmp, move.operand), move.phi.getBlock(), Position.none(), it);
      temps.add(tmp);
      move.operand.removePhiUser(move.phi);
    }
    for (int i = moves.size() - 1; i >= 0; i--) {
      PhiMove move = moves.get(i);
      StackValue tmp = temps.get(i);
      FixedLocalValue out = new FixedLocalValue(move.phi);
      add(new Store(out, tmp), move.phi.getBlock(), Position.none(), it);
      move.phi.replaceUsers(out);
    }
  }

  private Instruction load(StackValue stackValue, Value value) {
    if (value.isConstant()) {
      ConstInstruction constant = value.getConstInstruction();
      if (constant.isConstNumber()) {
        return new ConstNumber(stackValue, constant.asConstNumber().getRawValue());
      } else if (constant.isConstString()) {
        return new ConstString(stackValue, constant.asConstString().getValue());
      } else if (constant.isConstClass()) {
        return new ConstClass(stackValue, constant.asConstClass().getValue());
      } else {
        throw new Unreachable("Unexpected constant value: " + value);
      }
    }
    return new Load(stackValue, value);
  }

  private static void add(
      Instruction newInstruction, Instruction existingInstruction, InstructionListIterator it) {
    add(newInstruction, existingInstruction.getBlock(), existingInstruction.getPosition(), it);
  }

  private static void add(
      Instruction newInstruction, BasicBlock block, Position position, InstructionListIterator it) {
    newInstruction.setBlock(block);
    newInstruction.setPosition(position);
    it.add(newInstruction);
  }
}
