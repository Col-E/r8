// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.conversion.CfSourceUtils.ensureLabel;

import com.android.tools.r8.cf.CfRegisterAllocator;
import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.TypeVerificationHelper.InitializedTypeInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.NewInstanceInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.ThisInstanceInfo;
import com.android.tools.r8.cf.TypeVerificationHelper.TypeInfo;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadata;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Inc;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.StackValues;
import com.android.tools.r8.ir.code.UninitializedThisLocalRead;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.passes.TrivialGotosCollapser;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.PeepholeOptimizer;
import com.android.tools.r8.ir.optimize.PhiOptimizations;
import com.android.tools.r8.ir.optimize.peepholes.BasicBlockMuncher;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

public class CfBuilder {

  private static final int PEEPHOLE_OPTIMIZATION_PASSES = 2;
  private static final int SUFFIX_SHARING_OVERHEAD = 30;
  private static final int IINC_PATTERN_SIZE = 4;

  public final AppView<?> appView;
  private final ProgramMethod method;
  private final IRCode code;

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

  private Map<NewInstance, List<InvokeDirect>> initializers;
  private List<InvokeDirect> thisInitializers;
  private Map<NewInstance, CfLabel> newInstanceLabels;

  // Extra information that should be attached to the bytecode instructions.
  private final BytecodeMetadata.Builder<CfInstruction> bytecodeMetadataBuilder;

  // Internal structure maintaining the stack height.
  private static class StackHeightTracker {
    int maxHeight = 0;
    int height = 0;

    boolean isEmpty() {
      return height == 0;
    }

    void push(Value value) {
      assert value.isValueOnStack();
      height += value.requiredRegisters();
      maxHeight = Math.max(maxHeight, height);
    }

    void pop(Value value) {
      assert value.isValueOnStack();
      height -= value.requiredRegisters();
    }

    void setHeight(int height) {
      assert height <= maxHeight;
      this.height = height;
    }
  }

  public CfBuilder(
      AppView<?> appView,
      ProgramMethod method,
      IRCode code,
      BytecodeMetadataProvider bytecodeMetadataProvider) {
    this.appView = appView;
    this.method = method;
    this.code = code;
    this.bytecodeMetadataBuilder = BytecodeMetadata.builder(bytecodeMetadataProvider);
  }

  public CfCode build(DeadCodeRemover deadCodeRemover, Timing timing) {
    timing.time("Trace blocks", code::traceBlocks);
    timing.time("Compute Initializers", () -> computeInitializers());
    timing.begin("Compute verification types");
    TypeVerificationHelper typeVerificationHelper = new TypeVerificationHelper(appView, code);
    typeVerificationHelper.computeVerificationTypes();
    timing.end();
    assert deadCodeRemover.verifyNoDeadCode(code);
    timing.time("Rewrite nots", this::rewriteNots);
    timing.begin("Insert loads and stores");
    LoadStoreHelper loadStoreHelper = new LoadStoreHelper(appView, code, typeVerificationHelper);
    loadStoreHelper.insertLoadsAndStores();
    timing.end();
    // Run optimizations on phis and basic blocks in a fixpoint.
    if (appView.options().enableLoadStoreOptimization) {
      timing.begin("Load store optimizations (BasicBlockMunching)");
      PhiOptimizations phiOptimizations = new PhiOptimizations();
      boolean reachedFixpoint = false;
      phiOptimizations.optimize(code);
      while (!reachedFixpoint) {
        BasicBlockMuncher.optimize(code, appView.options());
        reachedFixpoint = !phiOptimizations.optimize(code);
      }
      timing.end();
    }
    assert code.isConsistentSSA(appView);
    // Insert reads for uninitialized read blocks to ensure correct stack maps.
    timing.begin("Insert uninitialized local reads");
    Set<UninitializedThisLocalRead> uninitializedThisLocalReads =
        insertUninitializedThisLocalReads();
    timing.end();
    timing.begin("Register allocation");
    registerAllocator = new CfRegisterAllocator(appView, code, typeVerificationHelper);
    registerAllocator.allocateRegisters();
    timing.end();
    // Remove any uninitializedThisLocalRead now that the register allocation will preserve this
    // for instance initializers.
    timing.begin("Remove uninitialized local reads");
    if (!uninitializedThisLocalReads.isEmpty()) {
      for (UninitializedThisLocalRead uninitializedThisLocalRead : uninitializedThisLocalReads) {
        uninitializedThisLocalRead.getBlock().removeInstruction(uninitializedThisLocalRead);
      }
    }
    timing.end();

    timing.begin("Insert phi moves");
    loadStoreHelper.insertPhiMoves(registerAllocator);
    timing.end();

    TrivialGotosCollapser trivialGotosCollapser = new TrivialGotosCollapser(appView);
    timing.begin("BasicBlock peephole optimizations");
    if (code.getConversionOptions().isPeepholeOptimizationsEnabled()) {
      for (int i = 0; i < PEEPHOLE_OPTIMIZATION_PASSES; i++) {
        trivialGotosCollapser.run(code.context(), code, timing);
        PeepholeOptimizer.removeIdenticalPredecessorBlocks(code, registerAllocator);
        PeepholeOptimizer.shareIdenticalBlockSuffix(
            code, registerAllocator, SUFFIX_SHARING_OVERHEAD);
      }
    }
    timing.end();

    timing.time("Rewrite Iinc patterns", this::rewriteIincPatterns);

    trivialGotosCollapser.run(code.context(), code, timing);
    timing.begin("Remove redundant debug positions");
    DexBuilder.removeRedundantDebugPositions(code);
    timing.end();
    timing.begin("Build CF Code");
    CfCode code = buildCfCode();
    timing.end();
    assert verifyInvokeInterface(code, appView);
    assert code.getOrComputeStackMapStatus(method, appView, appView.graphLens())
        .isValidOrNotPresent();
    return code;
  }

  private Set<UninitializedThisLocalRead> insertUninitializedThisLocalReads() {
    if (!method.getReference().isInstanceInitializerInlineIntoOrMerged(appView)) {
      return Collections.emptySet();
    }
    // Find all non-normal exit blocks.
    Set<UninitializedThisLocalRead> uninitializedThisLocalReads = Sets.newIdentityHashSet();
    for (BasicBlock exitBlock : code.blocks) {
      if (exitBlock.exit().isThrow() && !exitBlock.hasCatchHandlers()) {
        LinkedList<Instruction> instructions = exitBlock.getInstructions();
        Instruction throwing = instructions.removeLast();
        assert throwing.isThrow();
        UninitializedThisLocalRead read = new UninitializedThisLocalRead(code.getThis());
        read.setPosition(throwing.getPosition());
        uninitializedThisLocalReads.add(read);
        read.setBlock(exitBlock);
        instructions.addLast(read);
        instructions.addLast(throwing);
      }
    }
    return uninitializedThisLocalReads;
  }

  private static boolean verifyInvokeInterface(CfCode code, AppView<?> appView) {
    if (appView.options().testing.allowInvokeErrors) {
      return true;
    }
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction instanceof CfInvoke) {
        CfInvoke invoke = (CfInvoke) instruction;
        if (invoke.getMethod().holder.isClassType()) {
          DexClass holder = appView.definitionFor(invoke.getMethod().holder);
          assert holder == null || holder.isInterface() == invoke.isInterface();
        }
      }
    }
    return true;
  }

  public DexField resolveField(DexField field) {
    DexEncodedField resolvedField =
        appView.appInfoForDesugaring().resolveField(field).getResolvedField();
    return resolvedField == null ? field : resolvedField.getReference();
  }

  private void computeInitializers() {
    assert initializers == null;
    assert thisInitializers == null;
    initializers = new HashMap<>();
    boolean isInstanceInitializer =
        method.getReference().isInstanceInitializerInlineIntoOrMerged(appView);
    for (BasicBlock block : code.blocks) {
      for (Instruction insn : block.getInstructions()) {
        if (insn.isNewInstance()) {
          initializers.put(insn.asNewInstance(), computeInitializers(insn.outValue()));
        } else if (insn.isArgument() && isInstanceInitializer) {
          if (insn.outValue().isThis()) {
            // By JVM8 §4.10.1.9 (invokespecial), a this() or super() call in a constructor
            // changes the type of `this` from uninitializedThis
            // to the type of the class of the <init> method.
            thisInitializers = computeInitializers(insn.outValue());
          }
        }
      }
    }
    assert !(isInstanceInitializer && thisInitializers == null);
  }

  private List<InvokeDirect> computeInitializers(Value value) {
    List<InvokeDirect> initializers = new ArrayList<>();
    for (Instruction user : value.uniqueUsers()) {
      if (user instanceof InvokeDirect
          && user.inValues().get(0) == value
          && user.asInvokeDirect().getInvokedMethod().name
              == appView.dexItemFactory().constructorMethodName) {
        initializers.add(user.asInvokeDirect());
      }
    }
    return initializers;
  }

  private void rewriteNots() {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction current = it.next();
        if (!current.isNot()) {
          continue;
        }

        Value inValue = current.inValues().get(0);

        // Insert ConstNumber(v, -1) before Not.
        it.previous();
        Value constValue = code.createValue(inValue.getType());
        Instruction newInstruction = new ConstNumber(constValue, -1);
        newInstruction.setBlock(block);
        newInstruction.setPosition(current.getPosition());
        it.add(newInstruction);
        it.next();

        // Replace Not with Xor.
        it.replaceCurrentInstruction(
            Xor.create(current.asNot().type, current.outValue(), inValue, constValue));
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
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    BasicBlock block = blockIterator.next();
    CfLabel tryCatchStart = null;
    CatchHandlers<BasicBlock> tryCatchHandlers = CatchHandlers.EMPTY_BASIC_BLOCK;
    boolean previousFallthrough = false;

    boolean firstBlock = true;
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

      if (firstBlock) {
        firstBlock = false;
      }

      block = nextBlock;
      previousFallthrough = fallthrough;
    } while (block != null);
    // TODO(mkroghj) Move computation of stack-height to CF instructions.
    if (!openLocalVariables.isEmpty()) {
      CfLabel endLabel = ensureLabel(instructions);
      for (LocalVariableInfo info : openLocalVariables.values()) {
        info.setEnd(endLabel);
        localVariablesTable.add(info);
      }
    }
    com.android.tools.r8.position.Position diagnosticPosition =
        com.android.tools.r8.position.Position.UNKNOWN;
    if (method.getDefinition().getCode().isCfCode()) {
      diagnosticPosition = method.getDefinition().getCode().asCfCode().getDiagnosticPosition();
    }
    return new CfCode(
        method.getHolderType(),
        stackHeightTracker.maxHeight,
        registerAllocator.registersUsed(),
        instructions,
        tryCatchRanges,
        localVariablesTable,
        diagnosticPosition,
        bytecodeMetadataBuilder.build());
  }

  private static boolean isNopInstruction(Instruction instruction, BasicBlock nextBlock) {
    // From DexBuilder
    return instruction.isArgument()
        || instruction.isMoveException()
        || instruction.isDebugLocalsChange()
        || instruction.isMoveException()
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

  private void rewriteIincPatterns() {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      // Test that we have enough instructions for iinc.
      while (IINC_PATTERN_SIZE <= block.getInstructions().size() - it.nextIndex()) {
        Instruction loadOrConst1 = it.next();
        if (!loadOrConst1.isLoad() && !loadOrConst1.isConstNumber()) {
          continue;
        }
        Load load;
        ConstNumber constNumber;
        if (loadOrConst1.isLoad()) {
          load = loadOrConst1.asLoad();
          constNumber = it.next().asConstNumber();
        } else {
          load = it.next().asLoad();
          constNumber = loadOrConst1.asConstNumber();
        }
        Instruction add = it.next().asAdd();
        Instruction store = it.next().asStore();
        // Reset pointer to load.
        it.previous();
        it.previous();
        it.previous();
        it.previous();
        if (load == null
            || constNumber == null
            || add == null
            || store == null
            || constNumber.getOutType() != TypeElement.getInt()) {
          it.next();
          continue;
        }
        int increment = constNumber.getIntValue();
        if (increment < Byte.MIN_VALUE || Byte.MAX_VALUE < increment) {
          it.next();
          continue;
        }
        if (getLocalRegister(load.src()) != getLocalRegister(store.outValue())) {
          it.next();
          continue;
        }
        Position position = add.getPosition();
        if (position != load.getPosition()
            || position != constNumber.getPosition()
            || position != store.getPosition()) {
          continue;
        }
        it.removeInstructionIgnoreOutValue();
        it.next();
        it.removeInstructionIgnoreOutValue();
        it.next();
        it.removeInstructionIgnoreOutValue();
        it.next();
        Inc inc = new Inc(store.outValue(), load.inValues().get(0), increment);
        inc.setPosition(position);
        inc.setBlock(block);
        it.set(inc);
      }
    }
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
    for (Instruction instruction : block.getInstructions()) {
      if (fallthrough && instruction.isGoto()) {
        assert block.exit() == instruction;
        return;
      }
      for (int i = instruction.inValues().size() - 1; i >= 0; i--) {
        if (instruction.inValues().get(i).isValueOnStack()) {
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
          newInstanceLabels.put(instruction.asNewInstance(), ensureLabel(instructions));
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
            && !(currentPosition.isNone()
                && position.isSyntheticPosition()
                && !position.hasCallerPosition())
            && (appView.options().debug || instruction.instructionTypeCanThrow());
    if (!didLocalsChange && !didPositionChange) {
      return;
    }
    CfLabel label = ensureLabel(instructions);
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

  private void addFrame(BasicBlock block) {
    List<TypeInfo> stack = registerAllocator.getTypesAtBlockEntry(block).stack;
    CfFrame.Builder builder = CfFrame.builder();
    if (block.entry().isMoveException()) {
      assert stack.isEmpty();
      StackValue exception = (StackValue) block.entry().outValue();
      builder.push(getFrameType(block, exception.getTypeInfo()));
    } else {
      builder.allocateStack(stack.size());
      for (TypeInfo typeInfo : stack) {
        builder.push(getFrameType(block, typeInfo));
      }
    }

    Int2ReferenceMap<TypeInfo> locals = registerAllocator.getTypesAtBlockEntry(block).registers;
    for (Entry<TypeInfo> local : locals.int2ReferenceEntrySet()) {
      builder.store(local.getIntKey(), getFrameType(block, local.getValue()));
    }
    CfFrame frame = builder.build();

    // Make sure to end locals on this transition before the synthetic CfFrame instruction.
    // Otherwise we might extend the live range of a local across a CfFrame instruction that
    // the local is not live across. For example if we have a return followed by a move exception
    // where the locals end. Inserting a CfFrame instruction between the return and the move
    // exception without ending the locals will lead to having the local alive on the CfFrame
    // instruction which is not correct and will cause us to not be able to build IR from the
    // CfCode.
    boolean didLocalsChange = localsChanged();
    if (didLocalsChange) {
      CfLabel label = ensureLabel(instructions);
      updateLocals(label);
    }

    instructions.add(frame);
  }

  private PreciseFrameType getFrameType(BasicBlock liveBlock, TypeInfo typeInfo) {
    if (typeInfo instanceof InitializedTypeInfo) {
      return FrameType.initialized(typeInfo.getDexType());
    }
    UninitializedFrameType type = findAllocator(liveBlock, typeInfo);
    return type != null ? type : FrameType.initialized(typeInfo.getDexType());
  }

  private UninitializedFrameType findAllocator(BasicBlock liveBlock, TypeInfo typeInfo) {
    UninitializedFrameType res;
    Instruction definition;
    if (typeInfo instanceof NewInstanceInfo) {
      NewInstanceInfo newInstanceInfo = (NewInstanceInfo) typeInfo;
      definition = newInstanceInfo.newInstance;
      res =
          FrameType.uninitializedNew(
              newInstanceLabels.get(definition), newInstanceInfo.getDexType());
    } else if (typeInfo instanceof ThisInstanceInfo) {
      definition = ((ThisInstanceInfo) typeInfo).thisArgument;
      res = FrameType.uninitializedThis();
    } else {
      throw new Unreachable("Unexpected type info: " + typeInfo);
    }
    BasicBlock definitionBlock = definition.getBlock();
    assert definitionBlock != liveBlock;
    Set<BasicBlock> initializerBlocks = new HashSet<>();
    List<InvokeDirect> valueInitializers =
        definition.isArgument() ? thisInitializers : initializers.get(definition.asNewInstance());
    for (InvokeDirect initializer : valueInitializers) {
      BasicBlock initializerBlock = initializer.getBlock();
      if (initializerBlock == liveBlock) {
        // We assume that we are not initializing an object twice.
        return res;
      }
      initializerBlocks.add(initializerBlock);
    }
    // If the live-block has a predecessor that is an initializer-block the value is initialized,
    // otherwise it is not.
    WorkList<BasicBlock> findInitWorkList =
        WorkList.newEqualityWorkList(liveBlock.getPredecessors());
    while (findInitWorkList.hasNext()) {
      BasicBlock predecessor = findInitWorkList.next();
      if (initializerBlocks.contains(predecessor)) {
        return null;
      }
      if (predecessor != definitionBlock) {
        findInitWorkList.addIfNotSeen(predecessor.getPredecessors());
      }
    }
    return res;
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

  private void add(CfInstruction instruction) {
    instructions.add(instruction);
  }

  public void add(CfInstruction instruction, Instruction origin) {
    bytecodeMetadataBuilder.setMetadata(origin, instruction);
    add(instruction);
  }

  public void add(CfInstruction... instructions) {
    Collections.addAll(this.instructions, instructions);
  }

  public void addArgument(Argument argument) {
    // Nothing so far.
  }

  public boolean verifyNoMetadata(Instruction instruction) {
    assert bytecodeMetadataBuilder.verifyNoMetadata(instruction);
    return true;
  }
}
