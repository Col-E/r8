// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.CfRegisterAllocator;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.TypeVerificationHelper.InitializedTypeInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.NewInstanceInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.ThisInstanceInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.TypeInfo;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
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
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.PeepholeOptimizer;
import com.android.tools.r8.ir.optimize.peepholes.BasicBlockMuncher;
import com.android.tools.r8.utils.InternalOptions;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
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

  private static final int PEEPHOLE_OPTIMIZATION_PASSES = 2;

  private final DexItemFactory factory;
  private final DexEncodedMethod method;
  private final IRCode code;

  private TypeVerificationHelper typeVerificationHelper;
  private Map<BasicBlock, CfLabel> labels;
  private Set<CfLabel> emittedLabels;
  private List<CfInstruction> instructions;
  private CfRegisterAllocator registerAllocator;

  private Position currentPosition = Position.none();

  private final Int2ReferenceMap<DebugLocalInfo> emittedLocals = new Int2ReferenceOpenHashMap<>();
  private Int2ReferenceMap<DebugLocalInfo> pendingLocals = null;
  private boolean pendingLocalChanges = false;
  private BasicBlock pendingFrame = null;

  private final List<LocalVariableInfo> localVariablesTable = new ArrayList<>();
  private final Int2ReferenceMap<LocalVariableInfo> openLocalVariables =
      new Int2ReferenceOpenHashMap<>();

  private AppInfo appInfo;

  private Map<NewInstance, List<InvokeDirect>> initializers;
  private List<InvokeDirect> thisInitializers;
  private Map<NewInstance, CfLabel> newInstanceLabels;

  private InternalOptions options;

  // Internal structure maintaining the stack height.
  private static class StackHeightTracker {
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

    void setHeight(int height) {
      assert height <= maxHeight;
      this.height = height;
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
      AppInfo appInfo) {
    this.options = options;
    this.appInfo = appInfo;
    computeInitializers();
    typeVerificationHelper = new TypeVerificationHelper(code, factory, appInfo);
    typeVerificationHelper.computeVerificationTypes();
    splitExceptionalBlocks();
    new DeadCodeRemover(code, rewriter, graphLense, options).run();
    rewriteNots();
    LoadStoreHelper loadStoreHelper = new LoadStoreHelper(code, typeVerificationHelper, appInfo);
    loadStoreHelper.insertLoadsAndStores();
    BasicBlockMuncher muncher = new BasicBlockMuncher();
    muncher.optimize(code);
    registerAllocator = new CfRegisterAllocator(code, options, typeVerificationHelper);
    registerAllocator.allocateRegisters();
    loadStoreHelper.insertPhiMoves(registerAllocator);

    for (int i = 0; i < PEEPHOLE_OPTIMIZATION_PASSES; i++) {
      CodeRewriter.collapsTrivialGotos(method, code);
      PeepholeOptimizer.removeIdenticalPredecessorBlocks(code, registerAllocator);
    }

    CodeRewriter.collapsTrivialGotos(method, code);
    DexBuilder.removeRedundantDebugPositions(code);
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

  private void rewriteNots() {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction current = it.next();
        if (!current.isNot()) {
          continue;
        }

        Value inValue = current.inValues().get(0);

        // Insert ConstNumber(v, -1) before Not.
        it.previous();
        Value constValue = code.createValue(inValue.getTypeLattice());
        Instruction newInstruction = new ConstNumber(constValue, -1);
        newInstruction.setBlock(block);
        newInstruction.setPosition(current.getPosition());
        it.add(newInstruction);
        it.next();

        // Replace Not with Xor.
        it.replaceCurrentInstruction(
            new Xor(current.asNot().type, current.outValue(), inValue, constValue));
      }
    }
  }

  private int stackHeightAtBlockEntry(BasicBlock block) {
    int height = 0;
    for (TypeInfo type : registerAllocator.getTypesAtBlockEntry(block).stack) {
      DexType dexType = type.getDexType();
      height += dexType.isDoubleType() || dexType.isLongType() ? 2 : 1;
    }
    return height;
  }

  private CfCode buildCfCode() {
    StackHeightTracker stackHeightTracker = new StackHeightTracker();
    List<CfTryCatch> tryCatchRanges = new ArrayList<>();
    labels = new HashMap<>(code.blocks.size());
    emittedLabels = new HashSet<>(code.blocks.size());
    newInstanceLabels = new HashMap<>(initializers.size());
    instructions = new ArrayList<>();
    Iterator<BasicBlock> blockIterator = code.blocks.iterator();
    BasicBlock block = blockIterator.next();
    CfLabel tryCatchStart = null;
    CatchHandlers<BasicBlock> tryCatchHandlers = CatchHandlers.EMPTY_BASIC_BLOCK;
    boolean previousFallthrough = false;

    do {
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

      // Continue
      stackHeightTracker.setHeight(stackHeightAtBlockEntry(block));

      buildCfInstructions(block, nextBlock, fallthrough, stackHeightTracker);

      assert !block.exit().isReturn() || stackHeightTracker.isEmpty();

      block = nextBlock;
      previousFallthrough = fallthrough;
    } while (block != null);

    CfLabel endLabel = ensureLabel();
    for (LocalVariableInfo info : openLocalVariables.values()) {
      info.setEnd(endLabel);
      localVariablesTable.add(info);
    }
    return new CfCode(
        method.method,
        stackHeightTracker.maxHeight,
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

  private void buildCfInstructions(
      BasicBlock block, BasicBlock nextBlock, boolean fallthrough, StackHeightTracker stack) {
    if (pendingFrame != null) {
      boolean advancesPC = hasMaterializingInstructions(block, nextBlock);
      // If block has no materializing instructions, then we postpone emitting the frame
      // until the next block. In this case, nextBlock must be non-null
      // (or we would fall off the edge of the method).
      assert advancesPC || nextBlock != null;
      if (advancesPC) {
        addFrame(pendingFrame);
        pendingFrame = null;
      }
    }
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
        if (outValue instanceof StackValues) {
          for (StackValue outVal : ((StackValues) outValue).getStackValues()) {
            stack.push(outVal);
          }
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
            // Ignore synthetic positions prior to any actual position, except when inlined
            // (that is, callerPosition != null).
            && !(currentPosition.isNone() && position.synthetic && position.callerPosition == null)
            && (options.debug || instruction.instructionTypeCanThrow());
    if (!didLocalsChange && !didPositionChange) {
      return;
    }
    CfLabel label = ensureLabel();
    if (didLocalsChange) {
      updateLocals(label);
    }
    if (didPositionChange) {
      add(new CfPosition(label, position));
      currentPosition = position;
    }
  }

  private void updateLocals(CfLabel label) {
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

  private void addFrame(BasicBlock block) {
    List<TypeInfo> stack = registerAllocator.getTypesAtBlockEntry(block).stack;
    List<FrameType> stackTypes;
    if (block.entry().isMoveException()) {
      assert stack.isEmpty();
      StackValue exception = (StackValue) block.entry().outValue();
      stackTypes = Collections.singletonList(getFrameType(block, exception.getTypeInfo()));
    } else {
      stackTypes = new ArrayList<>(stack.size());
      for (TypeInfo typeInfo : stack) {
        stackTypes.add(getFrameType(block, typeInfo));
      }
    }

    Int2ReferenceMap<TypeInfo> locals = registerAllocator.getTypesAtBlockEntry(block).registers;
    Int2ReferenceSortedMap<FrameType> mapping = new Int2ReferenceAVLTreeMap<>();
    for (Entry<TypeInfo> local : locals.int2ReferenceEntrySet()) {
      mapping.put(local.getIntKey(), getFrameType(block, local.getValue()));
    }
    CfFrame frame = new CfFrame(mapping, stackTypes);

    // Make sure to end locals on this transition before the synthetic CfFrame instruction.
    // Otherwise we might extend the live range of a local across a CfFrame instruction that
    // the local is not live across. For example if we have a return followed by a move exception
    // where the locals end. Inserting a CfFrame instruction between the return and the move
    // exception without ending the locals will lead to having the local alive on the CfFrame
    // instruction which is not correct and will cause us to not be able to build IR from the
    // CfCode.
    boolean didLocalsChange = localsChanged();
    if (didLocalsChange) {
      CfLabel label = ensureLabel();
      updateLocals(label);
    }

    instructions.add(frame);
  }

  private FrameType getFrameType(BasicBlock liveBlock, TypeInfo typeInfo) {
    if (typeInfo instanceof InitializedTypeInfo) {
      return FrameType.initialized(typeInfo.getDexType());
    }
    FrameType type = findAllocator(liveBlock, typeInfo);
    return type != null ? type : FrameType.initialized(typeInfo.getDexType());
  }

  private FrameType findAllocator(BasicBlock liveBlock, TypeInfo typeInfo) {
    FrameType res;
    Instruction definition;
    if (typeInfo instanceof NewInstanceInfo) {
      definition = ((NewInstanceInfo) typeInfo).newInstance;
      res = FrameType.uninitializedNew(newInstanceLabels.get(definition));
    } else if (typeInfo instanceof ThisInstanceInfo) {
      definition = ((ThisInstanceInfo) typeInfo).thisArgument;
      res = FrameType.uninitializedThis();
    } else {
      throw new Unreachable("Unexpected type info: " + typeInfo);
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
