// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.CfRegisterAllocator;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DebugLocalInfo;
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
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class CfBuilder {

  private final DexItemFactory factory;
  private final DexEncodedMethod method;
  private final IRCode code;

  private Map<Value, DexType> types;
  private Map<BasicBlock, CfLabel> labels;
  private Set<CfLabel> emittedLabels;
  private List<CfInstruction> instructions;
  private CfRegisterAllocator registerAllocator;

  private Position currentPosition = Position.none();

  private final Int2ReferenceMap<DebugLocalInfo> emittedLocals = new Int2ReferenceOpenHashMap<>();
  private Int2ReferenceMap<DebugLocalInfo> pendingLocals = null;
  private boolean pendingLocalChanges = false;

  private final List<LocalVariableInfo> localVariablesTable = new ArrayList<>();
  private final Int2ReferenceMap<LocalVariableInfo> openLocalVariables =
      new Int2ReferenceOpenHashMap<>();

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

  public Code build(CodeRewriter rewriter, InternalOptions options, AppInfoWithSubtyping appInfo) {
    try {
      types = new TypeVerificationHelper(code, factory, appInfo).computeVerificationTypes();
      splitExceptionalBlocks();
      LoadStoreHelper loadStoreHelper = new LoadStoreHelper(code, types);
      loadStoreHelper.insertLoadsAndStores();
      DeadCodeRemover.removeDeadCode(code, rewriter, options);
      removeUnneededLoadsAndStores();
      registerAllocator = new CfRegisterAllocator(code, options);
      registerAllocator.allocateRegisters();
      loadStoreHelper.insertPhiMoves(registerAllocator);
      CodeRewriter.collapsTrivialGotos(method, code);
      int instructionTableCount =
          DexBuilder.instructionNumberToIndex(code.numberRemainingInstructions());
      DexBuilder.removeRedundantDebugPositions(code, instructionTableCount);
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

  private CfCode buildCfCode() {
    Stack stack = new Stack();
    List<CfTryCatch> tryCatchRanges = new ArrayList<>();
    labels = new HashMap<>(code.blocks.size());
    emittedLabels = new HashSet<>(code.blocks.size());
    instructions = new ArrayList<>();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    BasicBlock block = blockIterator.next();
    CfLabel tryCatchStart = null;
    CatchHandlers<BasicBlock> tryCatchHandlers = CatchHandlers.EMPTY_BASIC_BLOCK;
    BasicBlock pendingFrame = null;
    boolean previousFallthrough = false;
    do {
      CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
      if (!tryCatchHandlers.equals(handlers)) {
        if (!tryCatchHandlers.isEmpty()) {
          // Close try-catch and save the range.
          CfLabel tryCatchEnd = getLabel(block);
          tryCatchRanges.add(new CfTryCatch(tryCatchStart, tryCatchEnd, tryCatchHandlers, this));
          emitLabel(tryCatchEnd);
        }
        if (!handlers.isEmpty()) {
          // Open a try-catch.
          tryCatchStart = getLabel(block);
          emitLabel(tryCatchStart);
        }
        tryCatchHandlers = handlers;
      }
      BasicBlock nextBlock = blockIterator.hasNext() ? blockIterator.next() : null;
      // If previousBlock is fallthrough, then it is counted in getPredecessors().size(), but
      // we only want to set a pendingFrame if we have a predecessor which is not previousBlock.
      if (block.getPredecessors().size() > (previousFallthrough ? 1 : 0)) {
        assert stack.isEmpty();
        pendingFrame = block;
        emitLabel(getLabel(block));
      }
      if (pendingFrame != null) {
        boolean advancesPC = hasMaterializingInstructions(block, nextBlock);
        // If block has no materializing instructions, then we postpone emitting the frame
        // until the next block. In this case, nextBlock must be non-null
        // (or we would fall off the edge of the method).
        assert advancesPC || nextBlock != null;
        if (advancesPC) {
          addFrame(pendingFrame, Collections.emptyList());
          pendingFrame = null;
        }
      }
      JumpInstruction exit = block.exit();
      boolean fallthrough =
          (exit.isGoto() && exit.asGoto().getTarget() == nextBlock)
              || (exit.isIf() && exit.fallthroughBlock() == nextBlock);
      Int2ReferenceMap<DebugLocalInfo> locals = block.getLocalsAtEntry();
      if (locals == null) {
        assert pendingLocals == null;
      } else {
        pendingLocals = new Int2ReferenceOpenHashMap<>(locals);
        pendingLocalChanges = true;
      }
      buildCfInstructions(block, fallthrough, stack);
      block = nextBlock;
      previousFallthrough = fallthrough;
    } while (block != null);
    assert stack.isEmpty();
    CfLabel endLabel = ensureLabel();
    for (LocalVariableInfo info : openLocalVariables.values()) {
      info.setEnd(endLabel);
      localVariablesTable.add(info);
    }
    return new CfCode(
        method.method,
        stack.maxHeight,
        registerAllocator.registersUsed(),
        instructions,
        tryCatchRanges,
        localVariablesTable);
  }

  private static boolean isNopInstruction(Instruction instruction, BasicBlock nextBlock) {
    // From DexBuilder
    return instruction.isArgument()
        || instruction.isDebugLocalsChange()
        || (instruction.isGoto() && instruction.asGoto().getTarget() == nextBlock);
  }

  private boolean hasMaterializingInstructions(BasicBlock block, BasicBlock nextBlock) {
    if (block == null) {
      return false;
    }
    for (Instruction instruction : block.getInstructions()) {
      if (!isNopInstruction(instruction, nextBlock)) {
        return true;
      }
    }
    return false;
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
      if (instruction.isDebugLocalsChange()) {
        if (instruction.asDebugLocalsChange().apply(pendingLocals)) {
          pendingLocalChanges = true;
        }
      } else {
        updatePositionAndLocals(instruction);
        instruction.buildCf(this);
      }
    }
  }

  private void updatePositionAndLocals(Instruction instruction) {
    Position position = instruction.getPosition();
    boolean didLocalsChange = localsChanged();
    boolean didPositionChange = position.isSome() && position != currentPosition;
    if (!didLocalsChange && !didPositionChange) {
      return;
    }
    CfLabel label = ensureLabel();
    if (didLocalsChange) {
      Int2ReferenceSortedMap<DebugLocalInfo> ending =
          DebugLocalInfo.endingLocals(emittedLocals, pendingLocals);
      Int2ReferenceSortedMap<DebugLocalInfo> starting =
          DebugLocalInfo.startingLocals(emittedLocals, pendingLocals);
      assert !ending.isEmpty() || !starting.isEmpty();
      for (Entry<DebugLocalInfo> entry : ending.int2ReferenceEntrySet()) {
        int localIndex = entry.getIntKey();
        LocalVariableInfo info = openLocalVariables.remove(localIndex);
        info.setEnd(label);
        localVariablesTable.add(info);
        DebugLocalInfo removed = emittedLocals.remove(localIndex);
        assert removed == entry.getValue();
      }
      if (!starting.isEmpty()) {
        for (Entry<DebugLocalInfo> entry : starting.int2ReferenceEntrySet()) {
          int localIndex = entry.getIntKey();
          assert !emittedLocals.containsKey(localIndex);
          assert !openLocalVariables.containsKey(localIndex);
          openLocalVariables.put(
              localIndex, new LocalVariableInfo(localIndex, entry.getValue(), label));
          emittedLocals.put(localIndex, entry.getValue());
        }
      }
      pendingLocalChanges = false;
    }
    if (didPositionChange) {
      add(new CfPosition(label, position));
      currentPosition = position;
    }
  }

  private boolean localsChanged() {
    if (!pendingLocalChanges) {
      return false;
    }
    pendingLocalChanges = !DebugLocalInfo.localsInfoMapsEqual(emittedLocals, pendingLocals);
    return pendingLocalChanges;
  }

  private CfLabel ensureLabel() {
    CfInstruction last = getLastInstruction();
    if (last instanceof CfLabel) {
      return (CfLabel) last;
    }
    CfLabel label = new CfLabel();
    add(label);
    return label;
  }

  private CfInstruction getLastInstruction() {
    return instructions.isEmpty() ? null : instructions.get(instructions.size() - 1);
  }

  private void addFrame(BasicBlock block, Collection<StackValue> stack) {
    // TODO(zerny): Support having values on the stack on control-edges.
    assert stack.isEmpty();

    List<DexType> stackTypes;
    if (block.entry().isMoveException()) {
      StackValue exception = (StackValue) block.entry().outValue();
      stackTypes = Collections.singletonList(exception.getObjectType());
    } else {
      stackTypes = Collections.emptyList();
    }

    Collection<Value> locals = registerAllocator.getLocalsAtBlockEntry(block);
    Int2ReferenceSortedMap<DexType> mapping = new Int2ReferenceAVLTreeMap<>();

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
    instructions.add(new CfFrame(mapping, stackTypes));
  }

  private void emitLabel(CfLabel label) {
    if (!emittedLabels.contains(label)) {
      emittedLabels.add(label);
      instructions.add(label);
    }
  }

  // Callbacks

  public CfLabel getLabel(BasicBlock target) {
    return labels.computeIfAbsent(target, (block) -> new CfLabel());
  }

  public int getLocalRegister(Value value) {
    return registerAllocator.getRegisterForValue(value);
  }

  public void add(CfInstruction instruction) {
    instructions.add(instruction);
  }

  public void addArgument(Argument argument) {
    // Nothing so far.
  }
}
