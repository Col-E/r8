// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Pop;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class CfBuilder {

  private final DexEncodedMethod method;
  private final IRCode code;

  private int maxLocals = -1;
  private int maxStack = 0;
  private int currentStack = 0;
  private List<CfInstruction> instructions;
  private Reference2IntMap<Value> registers;
  private Map<BasicBlock, CfLabel> labels;

  /**
   * Value that represents a shared physical location defined by the phi value.
   *
   * This value is introduced to represent the store instructions used to unify the location of
   * in-flowing values to phi's. After introducing this fixed location the graph is no longer in
   * SSA since the fixed location signifies a place that can be written to from multiple places.
   */
  public static class FixedLocal extends Value {

    private final Phi phi;

    public FixedLocal(Phi phi) {
      super(phi.getNumber(), phi.outType(), phi.getLocalInfo());
      this.phi = phi;
    }

    @Override
    public boolean isConstant() {
      return false;
    }

    @Override
    public String toString() {
      return "fixed:v" + phi.getNumber();
    }
  }

  public static class StackHelper {

    private int currentStackHeight = 0;

    public void loadInValues(Instruction instruction, InstructionListIterator it) {
      int topOfStack = currentStackHeight;
      it.previous();
      for (Value value : instruction.inValues()) {
        StackValue stackValue = new StackValue(value.outType(), topOfStack++);
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
      StackValue newOutValue = new StackValue(instruction.outType(), currentStackHeight);
      Value oldOutValue = instruction.swapOutValue(newOutValue);
      add(new Store(oldOutValue, newOutValue), instruction, it);
    }

    public void popOutValue(ValueType type, Instruction instruction, InstructionListIterator it) {
      StackValue newOutValue = new StackValue(type, currentStackHeight);
      instruction.swapOutValue(newOutValue);
      add(new Pop(newOutValue), instruction, it);
    }

    public void movePhi(Phi phi, Value value, InstructionListIterator it) {
      StackValue tmp = new StackValue(phi.outType(), currentStackHeight);
      FixedLocal out = new FixedLocal(phi);
      add(load(tmp, value), phi.getBlock(), Position.none(), it);
      add(new Store(out, tmp), phi.getBlock(), Position.none(), it);
      value.removePhiUser(phi);
      phi.replaceUsers(out);
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
        Instruction newInstruction,
        BasicBlock block,
        Position position,
        InstructionListIterator it) {
      newInstruction.setBlock(block);
      newInstruction.setPosition(position);
      it.add(newInstruction);
    }

  }

  public CfBuilder(DexEncodedMethod method, IRCode code) {
    this.method = method;
    this.code = code;
  }

  public Code build(CodeRewriter rewriter, InternalOptions options) {
    try {
      splitExceptionalBlocks();
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

  // Split all blocks with throwing instructions and exceptional edges such that any non-throwing
  // instructions that might define values prior to the throwing exception are excluded from the
  // try-catch range. Failure to do so will result in code that does not verify on the JVM.
  private void splitExceptionalBlocks() {
    ListIterator<BasicBlock> it = code.listIterator();
    while (it.hasNext()) {
      BasicBlock block = it.next();
      if (!block.hasCatchHandlers()) {
        continue;
      }
      int size = block.getInstructions().size();
      boolean isThrow = block.exit().isThrow();
      if ((isThrow && size == 1) || (!isThrow && size == 2)) {
        // Fast-path to avoid processing blocks with just a single throwing instruction.
        continue;
      }
      InstructionListIterator instructions = block.listIterator();
      boolean hasOutValues = false;
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.instructionTypeCanThrow()) {
          break;
        }
        hasOutValues |= instruction.outValue() != null;
      }
      if (hasOutValues) {
        instructions.previous();
        instructions.split(code, it);
      }
    }
  }

  private void loadStoreInsertion() {
    StackHelper stack = new StackHelper();
    // Insert phi stores in all predecessors.
    for (BasicBlock block : code.blocks) {
      if (!block.getPhis().isEmpty()) {
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          for (Phi phi : block.getPhis()) {
            Value value = phi.getOperand(predIndex);
            InstructionListIterator it = pred.listIterator(pred.getInstructions().size());
            it.previous();
            stack.movePhi(phi, value, it);
          }
        }
      }
    }
    // Insert per-instruction loads and stores.
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
        // Eliminate unneeded loads of stores:
        //  v <- store si
        //  si <- load v
        // where |users(v)| == 1 (ie, the load is the only user)
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
    registers = new Reference2IntOpenHashMap<>();
    int nextFreeRegister = 0;
    while (it.hasNext()) {
      Instruction instruction = it.next();
      Value outValue = instruction.outValue();
      if (outValue instanceof FixedLocal) {
        // Phi stores are marked by a "fixed-local" value which share the same local index.
        FixedLocal fixed = (FixedLocal) outValue;
        if (!registers.containsKey(fixed.phi)) {
          registers.put(fixed.phi, nextFreeRegister);
          nextFreeRegister += fixed.requiredRegisters();
        }
      } else if (outValue != null && !(outValue instanceof StackValue)) {
        registers.put(instruction.outValue(), nextFreeRegister);
        nextFreeRegister += instruction.outValue().requiredRegisters();
      }
    }
    maxLocals = nextFreeRegister;
  }

  private void push(Value value) {
    assert value instanceof StackValue;
    currentStack += value.requiredRegisters();
    maxStack = Math.max(maxStack, currentStack);
  }

  private void pop(Value value) {
    assert value instanceof StackValue;
    currentStack -= value.requiredRegisters();
  }

  private CfCode buildCfCode() {
    List<CfTryCatch> tryCatchRanges = new ArrayList<>();
    labels = new HashMap<>(code.blocks.size());
    instructions = new ArrayList<>();
    Iterator<BasicBlock> blockIterator = code.listIterator();
    BasicBlock block = blockIterator.next();
    CfLabel tryCatchStart = null;
    CatchHandlers<BasicBlock> tryCatchHandlers = CatchHandlers.EMPTY_BASIC_BLOCK;
    do {
      CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
      if (!tryCatchHandlers.equals(handlers)) {
        if (!tryCatchHandlers.isEmpty()) {
          // Close try-catch and save the range.
          CfLabel tryCatchEnd = getLabel(block);
          tryCatchRanges.add(new CfTryCatch(tryCatchStart, tryCatchEnd, tryCatchHandlers, this));
          if (instructions.get(instructions.size() - 1) != tryCatchEnd) {
            instructions.add(tryCatchEnd);
          }
        }
        if (!handlers.isEmpty()) {
          // Open a try-catch.
          tryCatchStart = getLabel(block);
          if (instructions.isEmpty()
              || instructions.get(instructions.size() - 1) != tryCatchStart) {
            instructions.add(tryCatchStart);
          }
        }
        tryCatchHandlers = handlers;
      }
      BasicBlock nextBlock = blockIterator.hasNext() ? blockIterator.next() : null;
      buildCfInstructions(block, nextBlock);
      block = nextBlock;
    } while (block != null);
    assert currentStack == 0;
    return new CfCode(maxStack, maxLocals, instructions, tryCatchRanges);
  }

  private void buildCfInstructions(BasicBlock block, BasicBlock nextBlock) {
    boolean fallthrough = false;
    InstructionIterator it = block.iterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.isGoto() && instruction.asGoto().getTarget() == nextBlock) {
        fallthrough = true;
        continue;
      }
      for (Value inValue : instruction.inValues()) {
        if (inValue instanceof StackValue) {
          pop(inValue);
        }
      }
      if (instruction.outValue() != null) {
        Value outValue = instruction.outValue();
        if (outValue instanceof StackValue) {
          push(outValue);
        }
      }
      instruction.buildCf(this);
    }
    if (nextBlock == null || (fallthrough && nextBlock.getPredecessors().size() == 1)) {
      return;
    }
    instructions.add(getLabel(nextBlock));
  }

  // Callbacks

  public CfLabel getLabel(BasicBlock target){
    return labels.computeIfAbsent(target, (block) -> new CfLabel());
  }

  public int getLocalRegister(Value value) {
    if (value instanceof FixedLocal) {
      // Phi stores are marked by a "fixed-local" value which share the same local index.
      FixedLocal fixed = (FixedLocal) value;
      return registers.getInt(fixed.phi);
    }
    return registers.getInt(value);
  }

  public void add(CfInstruction instruction) {
    instructions.add(instruction);
  }

  public void addArgument(Argument argument) {
    // Nothing so far.
  }
}
