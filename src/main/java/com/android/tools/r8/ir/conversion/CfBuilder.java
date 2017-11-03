// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Pop;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.objects.Reference2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CfBuilder {

  private final DexEncodedMethod method;
  private final IRCode code;
  private List<CfInstruction> instructions;
  private Reference2IntMap<Value> argumentRegisters;
  private int maxLocals;

  public static class StackHelper {

    public void loadInValues(Instruction instruction, InstructionListIterator it) {
      it.previous();
      for (int i = 0; i < instruction.inValues().size(); i++) {
        Value value = instruction.inValues().get(i);
        StackValue stackValue = new StackValue(value.outType());
        Instruction load;
        if (value.isConstant()) {
          ConstInstruction constant = value.getConstInstruction();
          if (constant.isConstNumber()) {
            load = new ConstNumber(stackValue, constant.asConstNumber().getRawValue());
          } else if (constant.isConstString()) {
            load = new ConstString(stackValue, constant.asConstString().getValue());
          } else if (constant.isConstClass()) {
            load = new ConstClass(stackValue, constant.asConstClass().getValue());
          } else {
            throw new Unreachable("Unexpected constant value: " + value);
          }
        } else {
          load = new Load(stackValue, value);
        }
        add(load, instruction, it);
        value.removeUser(instruction);
        instruction.replaceValue(value, stackValue);
      }
      it.next();
    }

    public void storeOutValue(Instruction instruction, InstructionListIterator it) {
      if (instruction.isOutConstant()) {
        return;
      }
      StackValue newOutValue = new StackValue(instruction.outType());
      Value oldOutValue = instruction.swapOutValue(newOutValue);
      add(new Store(oldOutValue, newOutValue), instruction, it);
    }

    public void popOutValue(ValueType type, Instruction instruction, InstructionListIterator it) {
      StackValue newOutValue = new StackValue(type);
      instruction.swapOutValue(newOutValue);
      add(new Pop(newOutValue), instruction, it);
    }

    private static void add(
        Instruction newInstruction, Instruction existingInstruction, InstructionListIterator it) {
      newInstruction.setBlock(existingInstruction.getBlock());
      newInstruction.setPosition(existingInstruction.getPosition());
      it.add(newInstruction);
    }
  }

  public CfBuilder(DexEncodedMethod method, IRCode code) {
    this.method = method;
    this.code = code;
  }

  public Code build(CodeRewriter rewriter, InternalOptions options) {
    try {
      loadStoreInsertion();
      DeadCodeRemover.removeDeadCode(code, rewriter, options);
      removeUnneededLoadsAndStores();
      allocateLocalRegisters();
      // TODO(zerny): Compute debug info.
      return buildCfCode();
    } catch (Unimplemented e) {
      System.out.println("Incomplete CF construction: " + e.getMessage());
      return method.getCode().asJarCode();
    }
  }

  private void loadStoreInsertion() {
    StackHelper stack = new StackHelper();
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction current = it.next();
        current.insertLoadAndStores(it, stack);
      }
    }
  }

  private void removeUnneededLoadsAndStores() {
    Iterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction store = it.next();
        if (store instanceof Store && store.outValue().numberOfAllUsers() == 1) {
          Instruction load = it.peekNext();
          if (load instanceof Load && load.inValues().get(0) == store.outValue()) {
            Value storeIn = store.inValues().get(0);
            Value loadOut = load.outValue();
            loadOut.replaceUsers(storeIn);
            storeIn.removeUser(store);
            store.outValue().removeUser(load);
            // Remove the store.
            it.previous();
            it.remove();
            // Remove the load.
            it.next();
            it.remove();
          }
        }
      }
    }
  }

  private void allocateLocalRegisters() {
    // TODO(zerny): Allocate locals based on live ranges.
    InstructionIterator it = code.instructionIterator();
    argumentRegisters = new Reference2IntArrayMap<>(
        method.method.proto.parameters.values.length + (method.accessFlags.isStrict() ? 0 : 1));
    int argumentRegister = 0;
    int maxRegister = -1;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.isArgument()) {
        argumentRegisters.put(instruction.outValue(), argumentRegister);
        argumentRegister += instruction.outValue().requiredRegisters();
        maxRegister = argumentRegister - 1;
      } else if (instruction.outValue() != null
          && !(instruction.outValue() instanceof StackValue)) {
        maxRegister = Math.max(maxRegister, 2 * instruction.outValue().getNumber());
      }
    }
    maxLocals = maxRegister + 1;
  }

  private CfCode buildCfCode() {
    int maxStack = 0;
    int currentStack = 0;
    instructions = new ArrayList<>();
    Iterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionIterator it = block.iterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.outValue() != null) {
          Value outValue = instruction.outValue();
          if (outValue instanceof StackValue) {
            currentStack += outValue.requiredRegisters();
            maxStack = Math.max(maxStack, currentStack);
          }
        }
        for (Value inValue : instruction.inValues()) {
          if (inValue instanceof StackValue) {
            currentStack -= inValue.requiredRegisters();
          }
        }
        instruction.buildCf(this);
      }
    }
    assert currentStack == 0;
    return new CfCode(maxStack, maxLocals, instructions);
  }

  // Callbacks

  public int getLocalRegister(Value value) {
    if (value.isArgument()) {
      return argumentRegisters.getInt(value);
    }
    return 2 * value.getNumber();
  }

  public void add(CfInstruction instruction) {
    instructions.add(instruction);
  }

  public void addArgument(Argument argument) {
    // Nothing so far.
  }
}
