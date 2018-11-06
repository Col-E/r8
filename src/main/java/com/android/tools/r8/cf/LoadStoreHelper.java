// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
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
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class LoadStoreHelper {

  private final IRCode code;
  private final TypeVerificationHelper typesHelper;
  private final AppInfo appInfo;

  private Map<Value, ConstInstruction> clonableConstants = null;
  private BasicBlock block = null;

  public LoadStoreHelper(IRCode code, TypeVerificationHelper typesHelper, AppInfo appInfo) {
    this.code = code;
    this.typesHelper = typesHelper;
    this.appInfo = appInfo;
  }

  private static boolean hasLocalInfoOrUsersOutsideThisBlock(Value value, BasicBlock block) {
    if (value.hasLocalInfo()) {
      return true;
    }
    if (value.numberOfPhiUsers() > 0) {
      return true;
    }
    for (Instruction instruction : value.uniqueUsers()) {
      if (instruction.getBlock() != block) {
        return true;
      }
    }
    return false;
  }

  private static boolean isConstInstructionAlwaysThreeBytes(ConstInstruction instr) {
    if (instr.isConstNumber()) {
      ConstNumber constNumber = instr.asConstNumber();
      switch (instr.outType()) {
        case OBJECT:
        case INT:
        case FLOAT:
          return false;
        case LONG:
          {
            long number = constNumber.getLongValue();
            return number != 0 && number != 1;
          }
        case DOUBLE:
          {
            double number = constNumber.getDoubleValue();
            return number != 0.0f && number != 1.0f;
          }
        default:
          throw new Unreachable();
      }
    }
    assert instr.isConstClass()
        || instr.isConstMethodHandle()
        || instr.isConstMethodType()
        || instr.isConstString()
        || instr.isDexItemBasedConstString();
    return false;
  }

  private static boolean canRemoveConstInstruction(ConstInstruction instr, BasicBlock block) {
    Value value = instr.outValue();
    return !hasLocalInfoOrUsersOutsideThisBlock(value, block)
        && (value.numberOfUsers() <= 1 || !isConstInstructionAlwaysThreeBytes(instr));
  }

  public void insertLoadsAndStores() {
    clonableConstants = new IdentityHashMap<>();
    for (BasicBlock block : code.blocks) {
      this.block = block;
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        it.next().insertLoadAndStores(it, this);
      }
      clonableConstants.clear();
    }
    clonableConstants = null;
    block = null;
  }

  public void insertPhiMoves(CfRegisterAllocator allocator) {
    // Insert phi stores in all predecessors.
    for (BasicBlock block : code.blocks) {
      if (!block.getPhis().isEmpty()) {
        // TODO(zerny): Phi's at an exception block must be dealt with at block entry.
        assert !block.entry().isMoveException();
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          List<Phi> phis = block.getPhis();
          List<PhiMove> moves = new ArrayList<>(phis.size());
          for (Phi phi : phis) {
            if (!phi.needsRegister()) {
              continue;
            }
            Value value = phi.getOperand(predIndex);
            if (allocator.getRegisterForValue(phi) != allocator.getRegisterForValue(value)) {
              moves.add(new PhiMove(phi, value));
            }
          }
          InstructionListIterator it = pred.listIterator(pred.getInstructions().size());
          Instruction exit = it.previous();
          assert pred.exit() == exit;
          movePhis(moves, it, exit.getPosition());
        }
        allocator.addToLiveAtEntrySet(block, block.getPhis());
      }
    }
    code.blocks.forEach(BasicBlock::clearUserInfo);
  }

  private StackValue createStackValue(Value value, int height) {
    return StackValue.create(typesHelper.getTypeInfo(value), height, appInfo);
  }

  private StackValue createStackValue(DexType type, int height) {
    return StackValue.create(typesHelper.createInitializedType(type), height, appInfo);
  }

  public void loadInValues(Instruction instruction, InstructionListIterator it) {
    int topOfStack = 0;
    it.previous();
    for (int i = 0; i < instruction.inValues().size(); i++) {
      Value value = instruction.inValues().get(i);
      StackValue stackValue = createStackValue(value, topOfStack++);
      assert clonableConstants != null;
      ConstInstruction constInstruction = clonableConstants.get(value);
      if (constInstruction != null) {
        ConstInstruction clonedConstInstruction =
            ConstInstruction.copyOf(stackValue, constInstruction);
        add(clonedConstInstruction, instruction, it);
      } else {
        add(load(stackValue, value), instruction, it);
      }
      instruction.replaceValue(i, stackValue);
    }
    it.next();
  }

  public void storeOutValue(Instruction instruction, InstructionListIterator it) {
    assert !(instruction.outValue() instanceof StackValue);
    if (instruction.isConstInstruction()) {
      ConstInstruction constInstruction = instruction.asConstInstruction();
      assert block != null;
      if (canRemoveConstInstruction(constInstruction, block)) {
        assert !constInstruction.isDexItemBasedConstString()
            || constInstruction.outValue().numberOfUsers() == 1;
        clonableConstants.put(instruction.outValue(), constInstruction);
        instruction.outValue().clearUsers();
        it.removeOrReplaceByDebugLocalRead();
        return;
      }
      assert instruction.outValue().isUsed(); // Should have removed it above.
    } else if (!instruction.outValue().isUsed()) {
      popOutValue(instruction.outValue(), instruction, it);
      return;
    }
    StackValue newOutValue = createStackValue(instruction.outValue(), 0);
    Value oldOutValue = instruction.swapOutValue(newOutValue);
    Store store = new Store(oldOutValue, newOutValue);
    // Move the debugging-locals liveness pertaining to the instruction to the store.
    instruction.moveDebugValues(store);
    add(store, instruction, it);
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

  private void movePhis(List<PhiMove> moves, InstructionListIterator it, Position position) {
    // TODO(zerny): Accounting for non-interfering phis would lower the max stack size.
    int topOfStack = 0;
    List<StackValue> temps = new ArrayList<>(moves.size());
    for (PhiMove move : moves) {
      StackValue tmp = createStackValue(move.phi, topOfStack++);
      add(load(tmp, move.operand), move.phi.getBlock(), position, it);
      temps.add(tmp);
      move.operand.removePhiUser(move.phi);
    }
    for (int i = moves.size() - 1; i >= 0; i--) {
      PhiMove move = moves.get(i);
      StackValue tmp = temps.get(i);
      FixedLocalValue out = new FixedLocalValue(move.phi);
      add(new Store(out, tmp), move.phi.getBlock(), position, it);
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
      } else if (constant.isDexItemBasedConstString()) {
        return new DexItemBasedConstString(
            stackValue, constant.asDexItemBasedConstString().getItem());
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
