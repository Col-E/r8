// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

public class CfBuilder {

  private final DexItemFactory factory;
  private final DexEncodedMethod method;
  private final IRCode code;

  private int maxLocals = -1;

  private Map<Value, DexType> types;
  private Reference2IntMap<Value> registers;
  private Map<BasicBlock, CfLabel> labels;
  private List<CfInstruction> instructions;

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

  // Internal abstraction of the stack values and height.
  private static class Stack {
    int maxHeight = 0;
    int height = 0;

    boolean isEmpty() {
      return height == 0;
    }

    void push(Value value) {
      assert value instanceof StackValue;
      height += value.requiredRegisters();
      maxHeight = Math.max(maxHeight, height);
    }

    void pop(Value value) {
      assert value instanceof StackValue;
      height -= value.requiredRegisters();
    }
  }

  public CfBuilder(DexEncodedMethod method, IRCode code, DexItemFactory factory) {
    this.method = method;
    this.code = code;
    this.factory = factory;
  }

  public Code build(CodeRewriter rewriter, InternalOptions options) {
    try {
      types = new TypeVerificationHelper(code, factory).computeVerificationTypes();
      splitExceptionalBlocks();
      new LoadStoreHelper(code, types).insertLoadsAndStores();
      DeadCodeRemover.removeDeadCode(code, rewriter, options);
      removeUnneededLoadsAndStores();
      allocateLocalRegisters();
      // TODO(zerny): Compute debug info.
      CfCode code = buildCfCode();
      return code;
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
            // Rewind to the instruction before the store so we can identify new patterns.
            it.previous();
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

  private CfCode buildCfCode() {
    Stack stack = new Stack();
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
      boolean fallthrough = block.exit().isGoto() && block.exit().asGoto().getTarget() == nextBlock;
      buildCfInstructions(block, fallthrough, stack);
      if (nextBlock != null && (!fallthrough || nextBlock.getPredecessors().size() > 1)) {
        assert stack.isEmpty();
        instructions.add(getLabel(nextBlock));
        if (nextBlock.getPredecessors().size() > 1) {
          addFrame(Collections.emptyList(), types.keySet());
        }
      }
      block = nextBlock;
    } while (block != null);
    assert stack.isEmpty();
    return new CfCode(stack.maxHeight, maxLocals, instructions, tryCatchRanges);
  }

  private void buildCfInstructions(BasicBlock block, boolean fallthrough, Stack stack) {
    InstructionIterator it = block.iterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (fallthrough && instruction.isGoto()) {
        assert block.exit() == instruction;
        return;
      }
      for (int i = instruction.inValues().size() - 1; i >= 0; i--) {
        if (instruction.inValues().get(i) instanceof StackValue) {
          stack.pop(instruction.inValues().get(i));
        }
      }
      if (instruction.outValue() != null) {
        Value outValue = instruction.outValue();
        if (outValue instanceof StackValue) {
          stack.push(outValue);
        }
      }
      instruction.buildCf(this);
    }
  }

  private void addFrame(Collection<StackValue> stack, Collection<Value> locals) {
    // TODO(zerny): Support having values on the stack on control-edges.
    assert stack.isEmpty();
    Int2ReferenceSortedMap<DexType> mapping = new Int2ReferenceAVLTreeMap();
    for (Value local : locals) {
      DexType type;
      switch (local.outType()) {
        case INT:
          type = factory.intType;
          break;
        case FLOAT:
          type = factory.floatType;
          break;
        case LONG:
          type = factory.longType;
          break;
        case DOUBLE:
          type = factory.doubleType;
          break;
        case OBJECT:
          type = types.get(local);
          break;
        default:
          throw new Unreachable(
              "Unexpected local type: " + local.outType() + " for local: " + local);
      }
      mapping.put(getLocalRegister(local), type);
    }
    instructions.add(new CfFrame(mapping));
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
