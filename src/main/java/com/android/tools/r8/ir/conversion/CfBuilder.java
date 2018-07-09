// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.CfRegisterAllocator;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.NewInstance;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
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

  private AppInfoWithSubtyping appInfo;

  private Map<NewInstance, List<InvokeDirect>> initializers;
  private List<InvokeDirect> thisInitializers;
  private Map<NewInstance, CfLabel> newInstanceLabels;

  private InternalOptions options;

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

  public DexItemFactory getFactory() {
    return factory;
  }

  public CfCode build(
      CodeRewriter rewriter,
      GraphLense graphLense,
      InternalOptions options,
      AppInfoWithSubtyping appInfo) {
    this.options = options;
    computeInitializers();
    types = new TypeVerificationHelper(code, factory, appInfo).computeVerificationTypes();
    splitExceptionalBlocks();
    DeadCodeRemover.removeDeadCode(code, rewriter, graphLense, options);
    LoadStoreHelper loadStoreHelper = new LoadStoreHelper(code, types);
    loadStoreHelper.insertLoadsAndStores();
    removeUnneededLoadsAndStores();
    registerAllocator = new CfRegisterAllocator(code, options);
    registerAllocator.allocateRegisters();
    loadStoreHelper.insertPhiMoves(registerAllocator);
    CodeRewriter.collapsTrivialGotos(method, code);
    int instructionTableCount =
        DexBuilder.instructionNumberToIndex(code.numberRemainingInstructions());
    DexBuilder.removeRedundantDebugPositions(code, instructionTableCount);
    this.appInfo = appInfo;
    CfCode code = buildCfCode();
    return code;
  }

  public DexField resolveField(DexField field) {
    DexEncodedField resolvedField = appInfo.resolveFieldOn(field.clazz, field);
    return resolvedField == null ? field : resolvedField.field;
  }

  private void computeInitializers() {
    assert initializers == null;
    assert thisInitializers == null;
    initializers = new HashMap<>();
    for (BasicBlock block : code.blocks) {
      for (Instruction insn : block.getInstructions()) {
        if (insn.isNewInstance()) {
          initializers.put(insn.asNewInstance(), computeInitializers(insn.outValue()));
        } else if (insn.isArgument() && method.isInstanceInitializer()) {
          if (insn.outValue().isThis()) {
            // By JVM8 §4.10.1.9 (invokespecial), a this() or super() call in a constructor
            // changes the type of `this` from uninitializedThis
            // to the type of the class of the <init> method.
            thisInitializers = computeInitializers(insn.outValue());
          }
        }
      }
    }
    assert !(method.isInstanceInitializer() && thisInitializers == null);
  }

  private List<InvokeDirect> computeInitializers(Value value) {
    List<InvokeDirect> initializers = new ArrayList<>();
    for (Instruction user : value.uniqueUsers()) {
      if (user instanceof InvokeDirect
          && user.inValues().get(0) == value
          && user.asInvokeDirect().getInvokedMethod().name == factory.constructorMethodName) {
        initializers.add(user.asInvokeDirect());
      }
    }
    return initializers;
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
            it.removeOrReplaceByDebugLocalRead();
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
    newInstanceLabels = new HashMap<>(initializers.size());
    instructions = new ArrayList<>();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    BasicBlock block = blockIterator.next();
    CfLabel tryCatchStart = null;
    CatchHandlers<BasicBlock> tryCatchHandlers = CatchHandlers.EMPTY_BASIC_BLOCK;
    BasicBlock pendingFrame = null;
    boolean previousFallthrough = false;
    do {
      assert stack.isEmpty();
      CatchHandlers<BasicBlock> handlers = block.getCatchHandlers();
      if (!tryCatchHandlers.equals(handlers)) {
        if (!tryCatchHandlers.isEmpty()) {
          // Close try-catch and save the range.
          CfLabel tryCatchEnd = getLabel(block);
          tryCatchRanges.add(
              CfTryCatch.fromBuilder(tryCatchStart, tryCatchEnd, tryCatchHandlers, this));
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
        if (instruction.isNewInstance()) {
          newInstanceLabels.put(instruction.asNewInstance(), ensureLabel());
        }
        updatePositionAndLocals(instruction);
        instruction.buildCf(this);
      }
    }
  }

  private void updatePositionAndLocals(Instruction instruction) {
    Position position = instruction.getPosition();
    boolean didLocalsChange = localsChanged();
    boolean didPositionChange =
        position.isSome()
            && position != currentPosition
            && (options.debug || instruction.instructionTypeCanThrow());
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

    List<FrameType> stackTypes;
    if (block.entry().isMoveException()) {
      StackValue exception = (StackValue) block.entry().outValue();
      stackTypes = Collections.singletonList(FrameType.initialized(exception.getObjectType()));
    } else {
      stackTypes = Collections.emptyList();
    }

    Collection<Value> locals = registerAllocator.getLocalsAtBlockEntry(block);
    Int2ReferenceSortedMap<FrameType> mapping = new Int2ReferenceAVLTreeMap<>();

    for (Value local : locals) {
      mapping.put(getLocalRegister(local), getFrameType(block, local));
    }
    instructions.add(new CfFrame(mapping, stackTypes));
  }

  private FrameType getFrameType(BasicBlock liveBlock, Value local) {
    switch (local.outType()) {
      case INT:
        return FrameType.initialized(factory.intType);
      case FLOAT:
        return FrameType.initialized(factory.floatType);
      case LONG:
        return FrameType.initialized(factory.longType);
      case DOUBLE:
        return FrameType.initialized(factory.doubleType);
      case OBJECT:
        FrameType type = findAllocator(liveBlock, local);
        return type != null ? type : FrameType.initialized(types.get(local));
      default:
        throw new Unreachable(
            "Unexpected local type: " + local.outType() + " for local: " + local);
    }
  }

  private FrameType findAllocator(BasicBlock liveBlock, Value value) {
    Instruction definition = value.definition;
    while (definition != null && (definition.isStore() || definition.isLoad())) {
      definition = definition.inValues().get(0).definition;
    }
    if (definition == null) {
      return null;
    }
    FrameType res;
    if (definition.isNewInstance()) {
      res = FrameType.uninitializedNew(newInstanceLabels.get(definition.asNewInstance()));
    } else if (definition.isArgument()
        && method.isInstanceInitializer()
        && definition.outValue().isThis()) {
      res = FrameType.uninitializedThis();
    } else {
      return null;
    }
    BasicBlock definitionBlock = definition.getBlock();
    Set<BasicBlock> visited = new HashSet<>();
    Deque<BasicBlock> toVisit = new ArrayDeque<>();
    List<InvokeDirect> valueInitializers =
        definition.isArgument() ? thisInitializers : initializers.get(definition.asNewInstance());
    for (InvokeDirect initializer : valueInitializers) {
      BasicBlock initializerBlock = initializer.getBlock();
      if (initializerBlock == liveBlock) {
        return res;
      }
      if (initializerBlock != definitionBlock && visited.add(initializerBlock)) {
        toVisit.addLast(initializerBlock);
      }
    }
    while (!toVisit.isEmpty()) {
      BasicBlock block = toVisit.removeLast();
      for (BasicBlock predecessor : block.getPredecessors()) {
        if (predecessor == liveBlock) {
          return res;
        }
        if (predecessor != definitionBlock && visited.add(predecessor)) {
          toVisit.addLast(predecessor);
        }
      }
    }
    return null;
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
