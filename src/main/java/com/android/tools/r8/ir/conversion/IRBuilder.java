// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.InvalidDebugInfoException;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.EdgeType;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.Cmp;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstMethodHandle;
import com.android.tools.r8.ir.code.ConstMethodType;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugLocalRead;
import com.android.tools.r8.ir.code.DebugLocalUninitialized;
import com.android.tools.r8.ir.code.DebugLocalWrite;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.Neg;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilledData;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Not;
import com.android.tools.r8.ir.code.NumberConversion;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Switch;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueNumberGenerator;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Builder object for constructing high-level IR from dex bytecode.
 *
 * <p>The generated IR is in SSA form. The SSA construction is based on the paper
 * "Simple and Efficient Construction of Static Single Assignment Form" available at
 * http://compilers.cs.uni-saarland.de/papers/bbhlmz13cc.pdf
 */
public class IRBuilder {

  public static final int INITIAL_BLOCK_OFFSET = -1;

  public DexItemFactory getFactory() {
    return options.itemFactory;
  }

  // SSA construction uses a worklist of basic blocks reachable from the entry and their
  // instruction offsets.
  private static class WorklistItem {

    private final BasicBlock block;
    private final int firstInstructionIndex;

    private WorklistItem(BasicBlock block, int firstInstructionIndex) {
      assert block != null;
      this.block = block;
      this.firstInstructionIndex = firstInstructionIndex;
    }
  }

  private static class MoveExceptionWorklistItem extends WorklistItem {
    private final int sourceOffset;
    private final int targetOffset;

    private MoveExceptionWorklistItem(BasicBlock block, int sourceOffset, int targetOffset) {
      super(block, -1);
      this.sourceOffset = sourceOffset;
      this.targetOffset = targetOffset;
    }
  }

  private static class SplitBlockWorklistItem extends WorklistItem {
    private final int sourceOffset;
    private final int targetOffset;

    public SplitBlockWorklistItem(
        int firstInstructionIndex, BasicBlock block, int sourceOffset, int targetOffset) {
      super(block, firstInstructionIndex);
      this.sourceOffset = sourceOffset;
      this.targetOffset = targetOffset;
    }
  }

  /**
   * Representation of lists of values that can be used as keys in maps. A list of
   * values is equal to another list of values if it contains exactly the same values
   * in the same order.
   */
  private static class ValueList {

    private final List<Value> values = new ArrayList<>();

    /**
     * Creates a ValueList of all the operands at the given index in the list of phis.
     */
    public static ValueList fromPhis(List<Phi> phis, int index) {
      ValueList result = new ValueList();
      for (Phi phi : phis) {
        result.values.add(phi.getOperand(index));
      }
      return result;
    }

    @Override
    public int hashCode() {
      return values.hashCode();
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ValueList)) {
        return false;
      }
      ValueList o = (ValueList) other;
      if (o.values.size() != values.size()) {
        return false;
      }
      for (int i = 0; i < values.size(); i++) {
        if (values.get(i) != o.values.get(i)) {
          return false;
        }
      }
      return true;
    }
  }

  public static class BlockInfo {
    BasicBlock block = new BasicBlock();
    IntSet normalPredecessors = new IntArraySet();
    IntSet normalSuccessors = new IntArraySet();
    IntSet exceptionalPredecessors = new IntArraySet();
    IntSet exceptionalSuccessors = new IntArraySet();

    void addNormalPredecessor(int offset) {
      normalPredecessors.add(offset);
    }

    void addNormalSuccessor(int offset) {
      normalSuccessors.add(offset);
    }

    void replaceNormalPredecessor(int existing, int replacement) {
      normalPredecessors.remove(existing);
      normalPredecessors.add(replacement);
    }

    void addExceptionalPredecessor(int offset) {
      exceptionalPredecessors.add(offset);
    }

    void addExceptionalSuccessor(int offset) {
      exceptionalSuccessors.add(offset);
    }

    int predecessorCount() {
      return normalPredecessors.size() + exceptionalPredecessors.size();
    }

    IntSet allSuccessors() {
      IntSet all = new IntArraySet(normalSuccessors.size() + exceptionalSuccessors.size());
      all.addAll(normalSuccessors);
      all.addAll(exceptionalSuccessors);
      return all;
    }

    boolean hasMoreThanASingleNormalExit() {
      return normalSuccessors.size() > 1
          || (normalSuccessors.size() == 1 && !exceptionalSuccessors.isEmpty());
    }

    BlockInfo split(
        int blockStartOffset, int fallthroughOffset, Int2ReferenceMap<BlockInfo> targets) {
      BlockInfo fallthroughInfo = new BlockInfo();
      fallthroughInfo.normalPredecessors = new IntArraySet(Collections.singleton(blockStartOffset));
      fallthroughInfo.block.incrementUnfilledPredecessorCount();
      // Move all normal successors to the fallthrough block.
      IntIterator normalSuccessorIterator = normalSuccessors.iterator();
      while (normalSuccessorIterator.hasNext()) {
        BlockInfo normalSuccessor = targets.get(normalSuccessorIterator.nextInt());
        normalSuccessor.replaceNormalPredecessor(blockStartOffset, fallthroughOffset);
      }
      fallthroughInfo.normalSuccessors = normalSuccessors;
      normalSuccessors = new IntArraySet(Collections.singleton(fallthroughOffset));
      // Copy all exceptional successors to the fallthrough block.
      IntIterator exceptionalSuccessorIterator = fallthroughInfo.exceptionalSuccessors.iterator();
      while (exceptionalSuccessorIterator.hasNext()) {
        BlockInfo exceptionalSuccessor = targets.get(exceptionalSuccessorIterator.nextInt());
        exceptionalSuccessor.addExceptionalPredecessor(fallthroughOffset);
      }
      fallthroughInfo.exceptionalSuccessors = new IntArraySet(this.exceptionalSuccessors);
      return fallthroughInfo;
    }

    @Override
    public String toString() {
      StringBuilder stringBuilder =
          new StringBuilder()
              .append("block ")
              .append(block.getNumberAsString())
              .append(" predecessors: ");
      String sep = "";
      for (int offset : normalPredecessors) {
        stringBuilder.append(sep).append(offset);
        sep = ", ";
      }
      for (int offset : exceptionalPredecessors) {
        stringBuilder.append(sep).append('*').append(offset);
        sep = ", ";
      }
      stringBuilder.append(" successors: ");
      sep = "";
      for (int offset : normalSuccessors) {
        stringBuilder.append(sep).append(offset);
        sep = ", ";
      }
      for (int offset : exceptionalSuccessors) {
        stringBuilder.append(sep).append('*').append(offset);
        sep = ", ";
      }
      return stringBuilder.toString();
    }
  }

  // Mapping from instruction offsets to basic-block targets.
  private final Int2ReferenceSortedMap<BlockInfo> targets = new Int2ReferenceAVLTreeMap<>();
  private final Reference2IntMap<BasicBlock> offsets = new Reference2IntOpenHashMap<>();

  // Worklist of reachable blocks.
  private final Queue<Integer> traceBlocksWorklist = new LinkedList<>();

  // Bitmap to ensure we don't process an instruction more than once.
  private boolean[] processedInstructions = null;

  // Bitmap of processed subroutine instructions. Lazily allocated off the fast-path.
  private Set<Integer> processedSubroutineInstructions = null;

  // Worklist for SSA construction.
  private final Queue<WorklistItem> ssaWorklist = new LinkedList<>();

  // Basic blocks. Added after processing from the worklist.
  private final LinkedList<BasicBlock> blocks = new LinkedList<>();

  private BasicBlock entryBlock = null;
  private BasicBlock currentBlock = null;
  private int currentInstructionOffset = -1;

  final private ValueNumberGenerator valueNumberGenerator;
  private final DexEncodedMethod method;
  private final AppInfo appInfo;
  private final Origin origin;

  // Source code to build IR from. Null if already built.
  private SourceCode source;

  private boolean throwingInstructionInCurrentBlock = false;
  private final InternalOptions options;

  // Pending local reads.
  private Value previousLocalValue = null;
  private final List<Value> debugLocalEnds = new ArrayList<>();

  // Lazily populated list of local values that are referenced without being actually defined.
  private Int2ReferenceMap<List<Value>> uninitializedDebugLocalValues = null;

  private int nextBlockNumber = 0;

  // Flag indicating if the instructions define values with imprecise types.
  private boolean hasImpreciseInstructionOutValueTypes = false;

  public IRBuilder(
      DexEncodedMethod method,
      AppInfo appInfo,
      SourceCode source,
      InternalOptions options,
      Origin origin) {
    this(method, appInfo, source, options, origin, new ValueNumberGenerator());
  }

  public IRBuilder(
      DexEncodedMethod method,
      AppInfo appInfo,
      SourceCode source,
      InternalOptions options,
      Origin origin,
      ValueNumberGenerator valueNumberGenerator) {
    assert source != null;
    this.method = method;
    this.appInfo = appInfo;
    this.source = source;
    this.valueNumberGenerator =
        valueNumberGenerator != null ? valueNumberGenerator : new ValueNumberGenerator();
    this.options = options;
    this.origin = origin;
  }

  public boolean isGeneratingClassFiles() {
    return options.isGeneratingClassFiles();
  }

  public Int2ReferenceSortedMap<BlockInfo> getCFG() {
    return targets;
  }

  public DexMethod getMethod() {
    return method.method;
  }

  private void addToWorklist(BasicBlock block, int firstInstructionIndex) {
    // TODO(ager): Filter out the ones that are already in the worklist, mark bit in block?
    if (!block.isFilled()) {
      ssaWorklist.add(new WorklistItem(block, firstInstructionIndex));
    }
  }

  private void setCurrentBlock(BasicBlock block) {
    currentBlock = block;
  }

  /**
   * Build the high-level IR in SSA form.
   *
   * @return The list of basic blocks. First block is the main entry.
   */
  public IRCode build() {
    assert source != null;
    source.setUp();

    // Create entry block (at a non-targetable address).
    BlockInfo initialBlockInfo = new BlockInfo();
    targets.put(INITIAL_BLOCK_OFFSET, initialBlockInfo);
    offsets.put(initialBlockInfo.block, INITIAL_BLOCK_OFFSET);

    // Process reachable code paths starting from instruction 0.
    int instCount = source.instructionCount();
    processedInstructions = new boolean[instCount];
    traceBlocksWorklist.add(0);
    while (!traceBlocksWorklist.isEmpty()) {
      int startOfBlockOffset = traceBlocksWorklist.remove();
      int startOfBlockIndex = source.instructionIndex(startOfBlockOffset);
      // Check that the block has not been processed after being added.
      if (isIndexProcessed(startOfBlockIndex)) {
        continue;
      }
      // Process each instruction until the block is closed.
      for (int index = startOfBlockIndex; index < instCount; ++index) {
        markIndexProcessed(index);
        int closedAt = source.traceInstruction(index, this);
        if (closedAt != -1) {
          if (closedAt + 1 < instCount) {
            ensureBlockWithoutEnqueuing(source.instructionOffset(closedAt + 1));
          }
          break;
        }
        // If the next instruction starts a block, fall through to it.
        if (index + 1 < instCount) {
          int nextOffset = source.instructionOffset(index + 1);
          if (targets.get(nextOffset) != null) {
            ensureNormalSuccessorBlock(startOfBlockOffset, nextOffset);
            break;
          }
        }
      }
    }
    processedInstructions = null;

    setCurrentBlock(targets.get(INITIAL_BLOCK_OFFSET).block);
    entryBlock = currentBlock;
    source.buildPrelude(this);

    // Process normal blocks reachable from the entry block using a worklist of reachable
    // blocks.
    addToWorklist(currentBlock, 0);
    processWorklist();

    // Check that the last block is closed and does not fall off the end.
    assert currentBlock == null;

    // Verify that we have properly filled all blocks
    // Must be after handle-catch (which has delayed edges),
    // but before handle-exit (which does not maintain predecessor counts).
    assert verifyFilledPredecessors();

    // Insert debug positions so all position changes are marked by an explicit instruction.
    boolean hasDebugPositions = insertDebugPositions();

    // Insert definitions for all uninitialized local values.
    if (uninitializedDebugLocalValues != null) {
      InstructionListIterator it = entryBlock.listIterator();
      it.nextUntil(i -> !i.isArgument());
      it.previous();
      for (List<Value> values : uninitializedDebugLocalValues.values()) {
        for (Value value : values) {
          if (value.isUsed()) {
            Instruction def = new DebugLocalUninitialized(value);
            def.setBlock(entryBlock);
            def.setPosition(Position.none());
            it.add(def);
          }
        }
      }
    }

    // Clear all reaching definitions to free up memory (and avoid invalid use).
    for (BasicBlock block : blocks) {
      block.clearCurrentDefinitions();
    }

    // Join predecessors for which all phis have the same inputs. This avoids generating the
    // same phi moves in multiple blocks.
    joinPredecessorsWithIdenticalPhis();

    // Package up the IR code.
    IRCode ir = new IRCode(options, method, blocks, valueNumberGenerator, hasDebugPositions);

    // Verify critical edges are split so we have a place to insert phi moves if necessary.
    assert ir.verifySplitCriticalEdges();

    for (BasicBlock block : blocks) {
      block.deduplicatePhis();
    }

    ir.removeAllTrivialPhis();

    if (hasImpreciseTypes()) {
      TypeConstraintResolver resolver = new TypeConstraintResolver();
      resolver.resolve(ir, this);
    }

    // Clear the code so we don't build multiple times.
    source.clear();
    source = null;

    assert ir.isConsistentSSA();
    return ir;
  }

  public void constrainType(Value value, ValueType constraint) {
    value.constrainType(constraint, method.method, origin, options.reporter);
  }

  private boolean hasImpreciseTypes() {
    if (hasImpreciseInstructionOutValueTypes) {
      return true;
    }
    // TODO(zerny): Consider keeping track of the imprecise phi types during phi construction.
    for (BasicBlock block : blocks) {
      for (Phi phi : block.getPhis()) {
        if (!phi.outType().isPreciseType()) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean insertDebugPositions() {
    boolean hasDebugPositions = false;
    if (!options.debug) {
      return hasDebugPositions;
    }
    for (BasicBlock block : blocks) {
      InstructionListIterator it = block.listIterator();
      Position current = null;
      while (it.hasNext()) {
        Instruction instruction = it.next();
        Position position = instruction.getPosition();
        if (instruction.isMoveException()) {
          assert current == null;
          current = position;
          hasDebugPositions = hasDebugPositions || position.isSome();
        } else if (instruction.isDebugPosition()) {
          hasDebugPositions = true;
          if (position.equals(current)) {
            it.removeOrReplaceByDebugLocalRead();
          } else {
            current = position;
          }
        } else if (position.isSome() && !position.synthetic && !position.equals(current)) {
          DebugPosition positionChange = new DebugPosition();
          positionChange.setPosition(position);
          it.previous();
          it.add(positionChange);
          it.next();
          current = position;
        }
      }
    }
    return hasDebugPositions;
  }

  private boolean verifyFilledPredecessors() {
    for (BasicBlock block : blocks) {
      assert verifyFilledPredecessors(block);
    }
    return true;
  }

  private boolean verifyFilledPredecessors(BasicBlock block) {
    assert block.verifyFilledPredecessors();
    // TODO(zerny): Consider moving the validation of the initial control-flow graph to after its
    // construction and prior to building the IR.
    for (BlockInfo info : targets.values()) {
      if (info != null && info.block == block) {
        assert info.predecessorCount() == block.getPredecessors().size();
        assert info.normalSuccessors.size() == block.getNormalSuccessors().size();
        if (block.hasCatchHandlers()) {
          assert info.exceptionalSuccessors.size()
              == block.getCatchHandlers().getUniqueTargets().size();
        } else {
          assert !block.canThrow()
              || info.exceptionalSuccessors.isEmpty()
              || (info.exceptionalSuccessors.size() == 1
                  && info.exceptionalSuccessors.iterator().nextInt() < 0);
        }
        return true;
      }
    }
    // There are places where we add in new blocks that we do not represent in the initial CFG.
    // TODO(zerny): Should we maintain the initial CFG after instruction building?
    return true;
  }

  private void processWorklist() {
    for (WorklistItem item = ssaWorklist.poll(); item != null; item = ssaWorklist.poll()) {
      if (item.block.isFilled()) {
        continue;
      }
      assert debugLocalEnds.isEmpty();
      setCurrentBlock(item.block);
      blocks.add(currentBlock);
      currentBlock.setNumber(nextBlockNumber++);
      // Process synthesized move-exception block specially.
      if (item instanceof MoveExceptionWorklistItem) {
        processMoveExceptionItem((MoveExceptionWorklistItem) item);
        closeCurrentBlockGuaranteedNotToNeedEdgeSplitting();
        continue;
      }
      // Process split blocks which need to emit the locals transfer.
      if (item instanceof SplitBlockWorklistItem) {
        SplitBlockWorklistItem splitEdgeItem = (SplitBlockWorklistItem) item;
        source.buildBlockTransfer(
            this, splitEdgeItem.sourceOffset, splitEdgeItem.targetOffset, false);
        if (item.firstInstructionIndex == -1) {
          // If the block is a pure split-edge block emit goto (picks up local ends) and close.
          addInstruction(new Goto(), Position.none());
          closeCurrentBlockGuaranteedNotToNeedEdgeSplitting();
          continue;
        } else if (!debugLocalEnds.isEmpty()) {
          // Otherwise, if some locals ended, insert a read so it takes place at the
          // predecessor position.
          addInstruction(new DebugLocalRead());
        }
      }
      // Build IR for each dex instruction in the block.
      int instCount = source.instructionCount();
      for (int i = item.firstInstructionIndex; i < instCount; ++i) {
        if (currentBlock == null) {
          break;
        }
        int instructionOffset = source.instructionOffset(i);
        BlockInfo info = targets.get(instructionOffset);
        if (info != null && info.block != currentBlock) {
          addToWorklist(info.block, i);
          closeCurrentBlockWithFallThrough(info.block);
          break;
        }
        currentInstructionOffset = instructionOffset;
        source.buildInstruction(this, i, i == item.firstInstructionIndex);
      }
    }
  }

  private void processMoveExceptionItem(MoveExceptionWorklistItem moveExceptionItem) {
    // TODO(zerny): Link with outer try-block handlers, if any. b/65203529
    int targetIndex = source.instructionIndex(moveExceptionItem.targetOffset);
    int moveExceptionDest = source.getMoveExceptionRegister(targetIndex);
    Position position = source.getCanonicalDebugPositionAtOffset(moveExceptionItem.targetOffset);
    if (moveExceptionDest >= 0) {
      Value out = writeRegister(moveExceptionDest, ValueType.OBJECT, ThrowingInfo.NO_THROW, null);
      MoveException moveException = new MoveException(out);
      moveException.setPosition(position);
      currentBlock.add(moveException);
    }
    // The block-transfer for exceptional edges needs to inform that this is an exceptional transfer
    // so that local ends become implicit. The reason for this issue is that the "split block" for
    // and exceptional edge is *after* control transfer, so inserting an end will end up causing
    // locals to remain live longer than they should. The problem with this is that it is now
    // possible to resurrect a local by declaring debug info that does not contain the exception
    // handler but then loading the value from the local index. This should not be a problem in
    // practice since the stack is empty so the known case of extending the local liveness via the
    // stack can't happen. If this does end up being an issue, it can potentially be solved by
    // ending any local that could possibly end in any of the exceptional targets and then
    // explicitly restart the local on each split-edge that does not end the local.
    boolean isExceptionalEdge = true;
    source.buildBlockTransfer(
        this, moveExceptionItem.sourceOffset, moveExceptionItem.targetOffset, isExceptionalEdge);
    BasicBlock targetBlock = getTarget(moveExceptionItem.targetOffset);
    currentBlock.link(targetBlock);
    addInstruction(new Goto(), position);
    addToWorklist(targetBlock, targetIndex);
  }

  // Helper to resolve switch payloads and build switch instructions (dex code only).
  public void resolveAndBuildSwitch(int value, int fallthroughOffset, int payloadOffset) {
    source.resolveAndBuildSwitch(value, fallthroughOffset, payloadOffset, this);
  }

  // Helper to resolve fill-array data and build new-array instructions (dex code only).
  public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset) {
    source.resolveAndBuildNewArrayFilledData(arrayRef, payloadOffset, this);
  }

  /**
   * Add an (non-jump) instruction to the builder.
   *
   * @param ir IR instruction to add as the next instruction.
   */
  public void add(Instruction ir) {
    assert !ir.isJumpInstruction();
    addInstruction(ir);
  }

  public void addThisArgument(int register) {
    DebugLocalInfo local = getOutgoingLocal(register);
    Value value = writeRegister(register, ValueType.OBJECT, ThrowingInfo.NO_THROW, local);
    addInstruction(new Argument(value));
    value.markAsThis();
  }

  public void addNonThisArgument(int register, ValueType valueType) {
    DebugLocalInfo local = getOutgoingLocal(register);
    Value value = writeRegister(register, valueType, ThrowingInfo.NO_THROW, local);
    addInstruction(new Argument(value));
  }

  public void addBooleanNonThisArgument(int register) {
    DebugLocalInfo local = getOutgoingLocal(register);
    Value value = writeRegister(register, ValueType.INT, ThrowingInfo.NO_THROW, local);
    value.setKnownToBeBoolean(true);
    addInstruction(new Argument(value));
  }

  private static boolean isValidFor(Value value, DebugLocalInfo local) {
    // Invalid debug-info may cause attempt to read a local that is not actually alive.
    // See b/37722432 and regression test {@code jasmin.InvalidDebugInfoTests::testInvalidInfoThrow}
    return !value.isUninitializedLocal() && value.getLocalInfo() == local;
  }

  public void addDebugLocalStart(int register, DebugLocalInfo local) {
    assert local != null;
    if (!options.debug) {
      return;
    }
    // If the local was not introduced by the previous instruction, start it here.
    Value incomingValue = readRegisterForDebugLocal(register, local);
    if (incomingValue.getLocalInfo() != local
        || currentBlock.isEmpty()
        || currentBlock.getInstructions().getLast().outValue() != incomingValue) {
      // Note that the write register must not lookup outgoing local information and the local is
      // never considered clobbered by a start (if the in value has local info it must have been
      // marked ended elsewhere).
      Value out = writeRegister(register, incomingValue.outType(), ThrowingInfo.NO_THROW, local);
      DebugLocalWrite write = new DebugLocalWrite(out, incomingValue);
      addInstruction(write);
    }
  }

  public void addDebugLocalEnd(int register, DebugLocalInfo local) {
    assert local != null;
    if (!options.debug) {
      return;
    }
    Value value = readRegisterForDebugLocal(register, local);
    if (isValidFor(value, local)) {
      debugLocalEnds.add(value);
    }
  }

  public void addDebugPosition(Position position) {
    if (options.debug) {
      assert source.getCurrentPosition().equals(position);
      if (!debugLocalEnds.isEmpty()) {
        // If there are pending local ends, end them before changing the line.
        addInstruction(new DebugLocalRead(), Position.none());
      }
      addInstruction(new DebugPosition());
    }
  }

  public void addAdd(NumericType type, int dest, int left, int right) {
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Add instruction = new Add(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addAddLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Add instruction = new Add(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addAnd(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    And instruction = new And(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addAndLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    And instruction = new And(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addArrayGet(MemberType type, int dest, int array, int index) {
    Value in1 = readRegister(array, ValueType.OBJECT);
    Value in2 = readRegister(index, ValueType.INT);
    Value out = writeRegister(dest, ValueType.fromMemberType(type), ThrowingInfo.CAN_THROW);
    out.setKnownToBeBoolean(type == MemberType.BOOLEAN);
    ArrayGet instruction = new ArrayGet(type, out, in1, in2);
    assert instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addArrayLength(int dest, int array) {
    Value in = readRegister(array, ValueType.OBJECT);
    Value out = writeRegister(dest, ValueType.INT, ThrowingInfo.CAN_THROW);
    ArrayLength instruction = new ArrayLength(out, in);
    assert instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addArrayPut(MemberType type, int value, int array, int index) {
    Value inValue = readRegister(value, ValueType.fromMemberType(type));
    Value inArray = readRegister(array, ValueType.OBJECT);
    Value inIndex = readRegister(index, ValueType.INT);
    ArrayPut instruction = new ArrayPut(type, inArray, inIndex, inValue);
    add(instruction);
  }

  public void addCheckCast(int value, DexType type) {
    Value in = readRegister(value, ValueType.OBJECT);
    Value out = writeRegister(value, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    CheckCast instruction = new CheckCast(out, in, type);
    assert instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addCmp(NumericType type, Bias bias, int dest, int left, int right) {
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeRegister(dest, ValueType.INT, ThrowingInfo.NO_THROW);
    Cmp instruction = new Cmp(type, bias, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addConst(ValueType type, int dest, long value) {
    Value out = writeRegister(dest, type, ThrowingInfo.NO_THROW);
    ConstNumber instruction = new ConstNumber(out, value);
    assert !instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addLongConst(int dest, long value) {
    add(new ConstNumber(writeRegister(dest, ValueType.LONG, ThrowingInfo.NO_THROW), value));
  }

  public void addDoubleConst(int dest, long value) {
    add(new ConstNumber(writeRegister(dest, ValueType.DOUBLE, ThrowingInfo.NO_THROW), value));
  }

  public void addIntConst(int dest, long value) {
    add(new ConstNumber(writeRegister(dest, ValueType.INT, ThrowingInfo.NO_THROW), value));
  }

  public void addFloatConst(int dest, long value) {
    add(new ConstNumber(writeRegister(dest, ValueType.FLOAT, ThrowingInfo.NO_THROW), value));
  }

  public void addNullConst(int dest) {
    add(new ConstNumber(writeRegister(dest, ValueType.OBJECT, ThrowingInfo.NO_THROW), 0L));
  }

  public void addConstClass(int dest, DexType type) {
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    ConstClass instruction = new ConstClass(out, type);
    assert instruction.instructionTypeCanThrow();
    add(instruction);
  }

  public void addConstMethodHandle(int dest, DexMethodHandle methodHandle) {
    if (!options.canUseConstantMethodHandle()) {
      throw new ApiLevelException(
          AndroidApiLevel.P,
          "Const-method-handle",
          null /* sourceString */);
    }
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    ConstMethodHandle instruction = new ConstMethodHandle(out, methodHandle);
    add(instruction);
  }

  public void addConstMethodType(int dest, DexProto methodType) {
    if (!options.canUseConstantMethodType()) {
      throw new ApiLevelException(
          AndroidApiLevel.P,
          "Const-method-type",
          null /* sourceString */);
    }
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    ConstMethodType instruction = new ConstMethodType(out, methodType);
    add(instruction);
  }

  public void addConstString(int dest, DexString string) {
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    ConstString instruction = new ConstString(out, string);
    add(instruction);
  }

  public void addDiv(NumericType type, int dest, int left, int right) {
    boolean canThrow = type != NumericType.DOUBLE && type != NumericType.FLOAT;
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type,
        canThrow ? ThrowingInfo.CAN_THROW : ThrowingInfo.NO_THROW);
    Div instruction = new Div(type, out, in1, in2);
    assert instruction.instructionTypeCanThrow() == canThrow;
    add(instruction);
  }

  public void addDivLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    boolean canThrow = type != NumericType.DOUBLE && type != NumericType.FLOAT;
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type,
        canThrow ? ThrowingInfo.CAN_THROW : ThrowingInfo.NO_THROW);
    Div instruction = new Div(type, out, in1, in2);
    assert instruction.instructionTypeCanThrow() == canThrow;
    add(instruction);
  }

  public Monitor addMonitor(Monitor.Type type, int monitor) {
    Value in = readRegister(monitor, ValueType.OBJECT);
    Monitor monitorEnter = new Monitor(type, in);
    add(monitorEnter);
    return monitorEnter;
  }

  public void addMove(ValueType type, int dest, int src) {
    Value in = readRegister(src, type);
    if (options.debug) {
      // If the move is writing to a different local we must construct a new value.
      DebugLocalInfo destLocal = getOutgoingLocal(dest);
      if (destLocal != null && destLocal != in.getLocalInfo()) {
        Value out = writeRegister(dest, type, ThrowingInfo.NO_THROW);
        addInstruction(new DebugLocalWrite(out, in));
        return;
      }
    }
    currentBlock.writeCurrentDefinition(dest, in, ThrowingInfo.NO_THROW);
  }

  public void addMul(NumericType type, int dest, int left, int right) {
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Mul instruction = new Mul(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addMulLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Mul instruction = new Mul(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addRem(NumericType type, int dest, int left, int right) {
    boolean canThrow = type != NumericType.DOUBLE && type != NumericType.FLOAT;
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type,
        canThrow ? ThrowingInfo.CAN_THROW : ThrowingInfo.NO_THROW);
    Rem instruction = new Rem(type, out, in1, in2);
    assert instruction.instructionTypeCanThrow() == canThrow;
    addInstruction(instruction);
  }

  public void addRemLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    boolean canThrow = type != NumericType.DOUBLE && type != NumericType.FLOAT;
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type,
        canThrow ? ThrowingInfo.CAN_THROW : ThrowingInfo.NO_THROW);
    Rem instruction = new Rem(type, out, in1, in2);
    assert instruction.instructionTypeCanThrow() == canThrow;
    addInstruction(instruction);
  }

  public void addGoto(int targetOffset) {
    BasicBlock targetBlock = getTarget(targetOffset);
    assert !currentBlock.hasCatchSuccessor(targetBlock);
    currentBlock.link(targetBlock);
    addToWorklist(targetBlock, source.instructionIndex(targetOffset));
    closeCurrentBlock(new Goto());
  }

  private void addTrivialIf(int trueTargetOffset, int falseTargetOffset) {
    assert trueTargetOffset == falseTargetOffset;
    // Conditional instructions with the same true and false targets are noops. They will
    // always go to the next instruction. We end this basic block with a goto instead of
    // a conditional.
    BasicBlock target = getTarget(trueTargetOffset);
    // We expected an if here and therefore we incremented the expected predecessor count
    // twice for the following block.
    target.decrementUnfilledPredecessorCount();
    currentBlock.link(target);
    addToWorklist(target, source.instructionIndex(trueTargetOffset));
    closeCurrentBlock(new Goto());
  }

  private void addNonTrivialIf(If instruction, int trueTargetOffset, int falseTargetOffset) {
    BasicBlock trueTarget = getTarget(trueTargetOffset);
    BasicBlock falseTarget = getTarget(falseTargetOffset);
    currentBlock.link(trueTarget);
    currentBlock.link(falseTarget);
    // Generate fall-through before the block that is branched to.
    addToWorklist(falseTarget, source.instructionIndex(falseTargetOffset));
    addToWorklist(trueTarget, source.instructionIndex(trueTargetOffset));
    closeCurrentBlock(instruction);
  }

  public void addIf(If.Type type, ValueType operandType, int value1, int value2,
      int trueTargetOffset, int falseTargetOffset) {
    if (trueTargetOffset == falseTargetOffset) {
      addTrivialIf(trueTargetOffset, falseTargetOffset);
    } else {
      List<Value> values = new ArrayList<>(2);
      values.add(readRegister(value1, operandType));
      values.add(readRegister(value2, operandType));
      If instruction = new If(type, values);
      addNonTrivialIf(instruction, trueTargetOffset, falseTargetOffset);
    }
  }

  public void addIfZero(If.Type type, ValueType operandType, int value, int trueTargetOffset, int falseTargetOffset) {
    if (trueTargetOffset == falseTargetOffset) {
      addTrivialIf(trueTargetOffset, falseTargetOffset);
    } else {
      If instruction = new If(type, readRegister(value, operandType));
      addNonTrivialIf(instruction, trueTargetOffset, falseTargetOffset);
    }
  }

  public void addInstanceGet(int dest, int object, DexField field) {
    MemberType type = MemberType.fromDexType(field.type);
    Value in = readRegister(object, ValueType.OBJECT);
    Value out = writeRegister(dest, ValueType.fromMemberType(type), ThrowingInfo.CAN_THROW);
    out.setKnownToBeBoolean(type == MemberType.BOOLEAN);
    InstanceGet instruction = new InstanceGet(type, out, in, field);
    assert instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addInstanceOf(int dest, int value, DexType type) {
    Value in = readRegister(value, ValueType.OBJECT);
    Value out = writeRegister(dest, ValueType.INT, ThrowingInfo.CAN_THROW);
    InstanceOf instruction = new InstanceOf(out, in, type);
    assert instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addInstancePut(int value, int object, DexField field) {
    MemberType type = MemberType.fromDexType(field.type);
    Value objectValue = readRegister(object, ValueType.OBJECT);
    Value valueValue = readRegister(value, ValueType.fromMemberType(type));
    InstancePut instruction = new InstancePut(type, field, objectValue, valueValue);
    add(instruction);
  }

  public void addInvoke(
      Type type, DexItem item, DexProto callSiteProto, List<Value> arguments, boolean itf) {
    if (type == Type.POLYMORPHIC) {
      assert item instanceof DexMethod;
      if (!options.canUseInvokePolymorphic()) {
        throw new ApiLevelException(
            AndroidApiLevel.O,
            "MethodHandle.invoke and MethodHandle.invokeExact",
            null /* sourceString */);
      } else if (!options.canUseInvokePolymorphicOnVarHandle()
          && ((DexMethod) item).getHolder() == options.itemFactory.varHandleType) {
        throw new ApiLevelException(
            AndroidApiLevel.P,
            "Call to polymorphic signature of VarHandle",
            null /* sourceString */);
      }
    }
    if (appInfo != null && type == Type.VIRTUAL) {
      // If an invoke-virtual targets a private method in the current class overriding will
      // not apply (see jvm spec on method resolution 5.4.3.3 and overriding 5.4.5) and
      // therefore we use an invoke-direct instead. We need to do this as the Android Runtime
      // will not allow invoke-virtual of a private method.
      DexMethod invocationMethod = (DexMethod) item;
      if (invocationMethod.holder == method.method.holder) {
        DexEncodedMethod directTarget = appInfo.lookupDirectTarget(invocationMethod);
        if (directTarget != null && invocationMethod.holder == directTarget.method.holder) {
          type = Type.DIRECT;
        }
      }
    }
    add(Invoke.create(type, item, callSiteProto, null, arguments, itf));
  }

  public void addInvoke(Type type, DexItem item, DexProto callSiteProto, List<Value> arguments) {
    addInvoke(type, item, callSiteProto, arguments, false);
  }

  public void addInvoke(
      Invoke.Type type,
      DexItem item,
      DexProto callSiteProto,
      List<ValueType> types,
      List<Integer> registers) {
    addInvoke(type, item, callSiteProto, types, registers, false);
  }

  public void addInvoke(
      Invoke.Type type,
      DexItem item,
      DexProto callSiteProto,
      List<ValueType> types,
      List<Integer> registers,
      boolean itf) {
    assert types.size() == registers.size();
    List<Value> arguments = new ArrayList<>(types.size());
    for (int i = 0; i < types.size(); i++) {
      arguments.add(readRegister(registers.get(i), types.get(i)));
    }
    addInvoke(type, item, callSiteProto, arguments, itf);
  }

  public void addInvokeCustomRegisters(
      DexCallSite callSite, int argumentRegisterCount, int[] argumentRegisters) {
    int registerIndex = 0;
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    List<Value> arguments = new ArrayList<>(argumentRegisterCount);

    if (!bootstrapMethod.isStaticHandle()) {
      arguments.add(readRegister(argumentRegisters[registerIndex], ValueType.OBJECT));
      registerIndex += ValueType.OBJECT.requiredRegisters();
    }

    String shorty = callSite.methodProto.shorty.toString();

    for (int i = 1; i < shorty.length(); i++) {
      ValueType valueType = ValueType.fromTypeDescriptorChar(shorty.charAt(i));
      arguments.add(readRegister(argumentRegisters[registerIndex], valueType));
      registerIndex += valueType.requiredRegisters();
    }

    add(new InvokeCustom(callSite, null, arguments));
  }

  public void addInvokeCustomRange(
      DexCallSite callSite, int argumentCount, int firstArgumentRegister) {
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    List<Value> arguments = new ArrayList<>(argumentCount);

    int register = firstArgumentRegister;
    if (!bootstrapMethod.isStaticHandle()) {
      arguments.add(readRegister(register, ValueType.OBJECT));
      register += ValueType.OBJECT.requiredRegisters();
    }

    String shorty = callSite.methodProto.shorty.toString();

    for (int i = 1; i < shorty.length(); i++) {
      ValueType valueType = ValueType.fromTypeDescriptorChar(shorty.charAt(i));
      arguments.add(readRegister(register, valueType));
      register += valueType.requiredRegisters();
    }
    checkInvokeArgumentRegisters(register, firstArgumentRegister + argumentCount);
    add(new InvokeCustom(callSite, null, arguments));
  }

  public void addInvokeCustom(
      DexCallSite callSite, List<ValueType> types, List<Integer> registers) {
    assert types.size() == registers.size();
    List<Value> arguments = new ArrayList<>(types.size());
    for (int i = 0; i < types.size(); i++) {
      arguments.add(readRegister(registers.get(i), types.get(i)));
    }
    add(new InvokeCustom(callSite, null, arguments));
  }

  public void addInvokeRegisters(
      Invoke.Type type,
      DexMethod method,
      DexProto callSiteProto,
      int argumentRegisterCount,
      int[] argumentRegisters) {
    // The value of argumentRegisterCount is the number of registers - not the number of values,
    // but it is an upper bound on the number of arguments.
    List<Value> arguments = new ArrayList<>(argumentRegisterCount);
    int registerIndex = 0;
    if (type != Invoke.Type.STATIC) {
      arguments.add(readRegister(argumentRegisters[registerIndex], ValueType.OBJECT));
      registerIndex += ValueType.OBJECT.requiredRegisters();
    }
    DexString methodShorty;
    if (type == Invoke.Type.POLYMORPHIC) {
      // The call site signature for invoke polymorphic must be take from call site and not from
      // the called method.
      methodShorty = callSiteProto.shorty;
    } else {
      methodShorty = method.proto.shorty;
    }
    String shorty = methodShorty.toString();
    for (int i = 1; i < methodShorty.size; i++) {
      ValueType valueType = ValueType.fromTypeDescriptorChar(shorty.charAt(i));
      arguments.add(readRegister(argumentRegisters[registerIndex], valueType));
      registerIndex += valueType.requiredRegisters();
    }
    checkInvokeArgumentRegisters(registerIndex, argumentRegisterCount);
    addInvoke(type, method, callSiteProto, arguments);
  }

  public void addInvokeNewArray(DexType type, int argumentCount, int[] argumentRegisters) {
    String descriptor = type.descriptor.toString();
    assert descriptor.charAt(0) == '[';
    assert descriptor.length() >= 2;
    ValueType valueType = ValueType.fromTypeDescriptorChar(descriptor.charAt(1));
    List<Value> arguments = new ArrayList<>(argumentCount / valueType.requiredRegisters());
    int registerIndex = 0;
    while (registerIndex < argumentCount) {
      arguments.add(readRegister(argumentRegisters[registerIndex], valueType));
      if (valueType.isWide()) {
        assert registerIndex < argumentCount - 1;
        assert argumentRegisters[registerIndex] == argumentRegisters[registerIndex + 1] + 1;
      }
      registerIndex += valueType.requiredRegisters();
    }
    checkInvokeArgumentRegisters(registerIndex, argumentCount);
    addInvoke(Invoke.Type.NEW_ARRAY, type, null, arguments);
  }

  public void addMultiNewArray(DexType type, int dest, int[] dimensions) {
    assert isGeneratingClassFiles();
    List<Value> arguments = new ArrayList<>(dimensions.length);
    for (int dimension : dimensions) {
      arguments.add(readRegister(dimension, ValueType.INT));
    }
    addInvoke(Invoke.Type.MULTI_NEW_ARRAY, type, null, arguments);
    addMoveResult(dest);
  }

  public void addInvokeRange(
      Invoke.Type type,
      DexMethod method,
      DexProto callSiteProto,
      int argumentCount,
      int firstArgumentRegister) {
    // The value of argumentCount is the number of registers - not the number of values, but it
    // is an upper bound on the number of arguments.
    List<Value> arguments = new ArrayList<>(argumentCount);
    int register = firstArgumentRegister;
    if (type != Invoke.Type.STATIC) {
      arguments.add(readRegister(register, ValueType.OBJECT));
      register += ValueType.OBJECT.requiredRegisters();
    }
    DexString methodShorty;
    if (type == Invoke.Type.POLYMORPHIC) {
      // The call site signature for invoke polymorphic must be take from call site and not from
      // the called method.
      methodShorty = callSiteProto.shorty;
    } else {
      methodShorty = method.proto.shorty;
    }
    String shorty = methodShorty.toString();
    for (int i = 1; i < methodShorty.size; i++) {
      ValueType valueType = ValueType.fromTypeDescriptorChar(shorty.charAt(i));
      arguments.add(readRegister(register, valueType));
      register += valueType.requiredRegisters();
    }
    checkInvokeArgumentRegisters(register, firstArgumentRegister + argumentCount);
    addInvoke(type, method, callSiteProto, arguments);
  }

  public void addInvokeRangeNewArray(DexType type, int argumentCount, int firstArgumentRegister) {
    String descriptor = type.descriptor.toString();
    assert descriptor.charAt(0) == '[';
    assert descriptor.length() >= 2;
    ValueType valueType = ValueType.fromTypeDescriptorChar(descriptor.charAt(1));
    List<Value> arguments = new ArrayList<>(argumentCount / valueType.requiredRegisters());
    int register = firstArgumentRegister;
    while (register < firstArgumentRegister + argumentCount) {
      arguments.add(readRegister(register, valueType));
      register += valueType.requiredRegisters();
    }
    checkInvokeArgumentRegisters(register, firstArgumentRegister + argumentCount);
    addInvoke(Invoke.Type.NEW_ARRAY, type, null, arguments);
  }

  private void checkInvokeArgumentRegisters(int expected, int actual) {
    if (expected != actual) {
      throw new CompilationError("Invalid invoke instruction. "
          + "Expected use of " + expected + " argument registers, "
          + "found actual use of " + actual);
    }
  }

  public void addMoveException(int dest) {
    assert !currentBlock.getPredecessors().isEmpty();
    assert currentBlock.getPredecessors().stream().allMatch(b -> b.entry().isMoveException());
    assert verifyValueIsMoveException(readRegister(dest, ValueType.OBJECT));
  }

  private static boolean verifyValueIsMoveException(Value value) {
    if (value.isPhi()) {
      for (Value operand : value.asPhi().getOperands()) {
        assert operand.definition.isMoveException();
      }
    } else {
      assert value.definition.isMoveException();
    }
    return true;
  }

  public void addMoveResult(int dest) {
    List<Instruction> instructions = currentBlock.getInstructions();
    Invoke invoke = instructions.get(instructions.size() - 1).asInvoke();
    assert invoke.outValue() == null;
    assert invoke.instructionTypeCanThrow();
    DexType outType = invoke.getReturnType();
    Value outValue = writeRegister(dest, ValueType.fromDexType(outType), ThrowingInfo.CAN_THROW);
    outValue.setKnownToBeBoolean(outType.isBooleanType());
    invoke.setOutValue(outValue);
  }

  public void addNeg(NumericType type, int dest, int value) {
    Value in = readNumericRegister(value, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Neg instruction = new Neg(type, out, in);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addNot(NumericType type, int dest, int value) {
    Value in = readNumericRegister(value, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Instruction instruction;
    if (options.canUseNotInstruction()) {
      instruction = new Not(type, out, in);
    } else {
      Value minusOne = readLiteral(ValueType.fromNumericType(type), -1);
      instruction = new Xor(type, out, in, minusOne);
    }
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addNewArrayEmpty(int dest, int size, DexType type) {
    assert type.isArrayType();
    Value in = readRegister(size, ValueType.INT);
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    NewArrayEmpty instruction = new NewArrayEmpty(out, in, type);
    assert instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addNewArrayFilledData(int arrayRef, int elementWidth, long size, short[] data) {
    add(new NewArrayFilledData(readRegister(arrayRef, ValueType.OBJECT), elementWidth, size, data));
  }

  public void addNewInstance(int dest, DexType type) {
    Value out = writeRegister(dest, ValueType.OBJECT, ThrowingInfo.CAN_THROW);
    NewInstance instruction = new NewInstance(type, out);
    assert instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addReturn(ValueType type, int value) {
    ValueType returnType = ValueType.fromDexType(method.method.proto.returnType);
    assert returnType.verifyCompatible(type);
    Value in = readRegister(value, returnType);
    addReturn(new Return(in, returnType));
  }

  public void addReturn() {
    addReturn(new Return());
  }

  private void addReturn(Return ret) {
    // Attach the live locals to the return instruction to avoid a local change on monitor exit.
    attachLocalValues(ret);
    source.buildPostlude(this);
    closeCurrentBlock(ret);
  }

  public void addStaticGet(int dest, DexField field) {
    MemberType type = MemberType.fromDexType(field.type);
    Value out = writeRegister(dest, ValueType.fromMemberType(type), ThrowingInfo.CAN_THROW);
    out.setKnownToBeBoolean(type == MemberType.BOOLEAN);
    StaticGet instruction = new StaticGet(type, out, field);
    assert instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addStaticPut(int value, DexField field) {
    MemberType type = MemberType.fromDexType(field.type);
    Value in = readRegister(value, ValueType.fromMemberType(type));
    add(new StaticPut(type, in, field));
  }

  public void addSub(NumericType type, int dest, int left, int right) {
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Sub instruction = new Sub(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addRsubLiteral(NumericType type, int dest, int value, int constant) {
    assert type != NumericType.DOUBLE;
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    // Add this as a sub instruction - sub instructions with literals need to have the constant
    // on the left side (rsub).
    Sub instruction = new Sub(type, out, in2, in1);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addSwitch(int value, int[] keys, int fallthroughOffset, int[] labelOffsets) {
    int numberOfTargets = labelOffsets.length;
    assert (keys.length == 1) || (keys.length == numberOfTargets);

    // If the switch has no targets simply add a goto to the fallthrough.
    if (numberOfTargets == 0) {
      addGoto(fallthroughOffset);
      return;
    }

    Value switchValue = readRegister(value, ValueType.INT);

    // Find the keys not targeting the fallthrough.
    IntList nonFallthroughKeys = new IntArrayList(numberOfTargets);
    IntList nonFallthroughOffsets = new IntArrayList(numberOfTargets);
    int numberOfFallthroughs = 0;
    if (keys.length == 1) {
      int key = keys[0];
      for (int i = 0; i < numberOfTargets; i++) {
        if (labelOffsets[i] != fallthroughOffset) {
          nonFallthroughKeys.add(key);
          nonFallthroughOffsets.add(labelOffsets[i]);
        } else {
          numberOfFallthroughs++;
        }
        key++;
      }
    } else {
      assert keys.length == numberOfTargets;
      for (int i = 0; i < numberOfTargets; i++) {
        if (labelOffsets[i] != fallthroughOffset) {
          nonFallthroughKeys.add(keys[i]);
          nonFallthroughOffsets.add(labelOffsets[i]);
        } else {
          numberOfFallthroughs++;
        }
      }
    }
    targets.get(fallthroughOffset).block.decrementUnfilledPredecessorCount(numberOfFallthroughs);

    // If this was switch with only fallthrough cases we can make it a goto.
    // Oddly, this does happen.
    if (numberOfFallthroughs == numberOfTargets) {
      assert nonFallthroughKeys.size() == 0;
      addGoto(fallthroughOffset);
      return;
    }

    // Create a switch with only the non-fallthrough targets.
    keys = nonFallthroughKeys.toIntArray();
    labelOffsets = nonFallthroughOffsets.toIntArray();
    Switch aSwitch = createSwitch(switchValue, keys, fallthroughOffset, labelOffsets);
    closeCurrentBlock(aSwitch);
  }

  private Switch createSwitch(Value value, int[] keys, int fallthroughOffset, int[] targetOffsets) {
    assert keys.length == targetOffsets.length;
    // Compute target blocks for all keys. Only add a successor block once even
    // if it is hit by more of the keys.
    int[] targetBlockIndices = new int[targetOffsets.length];
    Map<Integer, Integer> offsetToBlockIndex = new HashMap<>();
    // Start with fall-through block.
    BasicBlock fallthroughBlock = getTarget(fallthroughOffset);
    currentBlock.link(fallthroughBlock);
    addToWorklist(fallthroughBlock, source.instructionIndex(fallthroughOffset));
    int fallthroughBlockIndex = currentBlock.getSuccessors().size() - 1;
    offsetToBlockIndex.put(fallthroughOffset, fallthroughBlockIndex);
    // Then all the switch target blocks.
    for (int i = 0; i < targetOffsets.length; i++) {
      int targetOffset = targetOffsets[i];
      BasicBlock targetBlock = getTarget(targetOffset);
      Integer targetBlockIndex = offsetToBlockIndex.get(targetOffset);
      if (targetBlockIndex == null) {
        // Target block not added as successor. Add it now.
        currentBlock.link(targetBlock);
        addToWorklist(targetBlock, source.instructionIndex(targetOffset));
        int successorIndex = currentBlock.getSuccessors().size() - 1;
        offsetToBlockIndex.put(targetOffset, successorIndex);
        targetBlockIndices[i] = successorIndex;
      } else {
        // Target block already added as successor. The target block therefore
        // has one less predecessor than precomputed.
        targetBlock.decrementUnfilledPredecessorCount();
        targetBlockIndices[i] = targetBlockIndex;
      }
    }
    return new Switch(value, keys, targetBlockIndices, fallthroughBlockIndex);
  }

  public void addThrow(int value) {
    Value in = readRegister(value, ValueType.OBJECT);
    // The only successors to a throw instruction are exceptional, so we directly add it (ensuring
    // the exceptional edges which are split-edge by construction) and then we close the block which
    // cannot have any additional edges that need splitting.
    addInstruction(new Throw(in));
    closeCurrentBlockGuaranteedNotToNeedEdgeSplitting();
  }

  public void addOr(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Or instruction = new Or(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addOrLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Or instruction = new Or(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addShl(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readRegister(right, ValueType.INT);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Shl instruction = new Shl(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addShlLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Shl instruction = new Shl(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addShr(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readRegister(right, ValueType.INT);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Shr instruction = new Shr(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addShrLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Shr instruction = new Shr(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addUshr(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readRegister(right, ValueType.INT);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Ushr instruction = new Ushr(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addUshrLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Value in2 = readIntLiteral(constant);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Ushr instruction = new Ushr(type, out, in1, in2);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addXor(NumericType type, int dest, int left, int right) {
    assert isIntegerType(type);
    Value in1 = readNumericRegister(left, type);
    Value in2 = readNumericRegister(right, type);
    Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
    Instruction instruction;
    if (options.canUseNotInstruction() &&
        in2.isConstNumber() &&
        in2.getConstInstruction().asConstNumber().isIntegerNegativeOne(type)) {
      instruction = new Not(type, out, in1);
    } else {
      instruction = new Xor(type, out, in1, in2);
    }
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addXorLiteral(NumericType type, int dest, int value, int constant) {
    assert isNonLongIntegerType(type);
    Value in1 = readNumericRegister(value, type);
    Instruction instruction;
    if (options.canUseNotInstruction() && constant == -1) {
      Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
      instruction = new Not(type, out, in1);
    } else {
      Value in2 = readIntLiteral(constant);
      Value out = writeNumericRegister(dest, type, ThrowingInfo.NO_THROW);
      instruction = new Xor(type, out, in1, in2);
    }
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  public void addConversion(NumericType to, NumericType from, int dest, int source) {
    Value in = readNumericRegister(source, from);
    Value out = writeNumericRegister(dest, to, ThrowingInfo.NO_THROW);
    NumberConversion instruction = new NumberConversion(from, to, out, in);
    assert !instruction.instructionTypeCanThrow();
    addInstruction(instruction);
  }

  // Value abstraction methods.

  public Value readRegister(int register, ValueType type) {
    DebugLocalInfo local = getIncomingLocal(register);
    Value value =
        readRegister(register, type, currentBlock, EdgeType.NON_EDGE, RegisterReadType.NORMAL);
    // Check that any information about a current-local is consistent with the read.
    if (local != null && value.getLocalInfo() != local && !value.isUninitializedLocal()) {
      throw new InvalidDebugInfoException(
          "Attempt to read local " + local
              + " but no local information was associated with the value being read.");
    }
    // Check that any local information on the value is actually visible.
    // If this assert triggers, the probable cause is that we end up reading an SSA value
    // after it should have been ended on a fallthrough from a conditional jump or a trivial-phi
    // removal resurrected the local.
    assert !value.hasLocalInfo()
        || value.getDebugLocalEnds() != null
        || source.verifyLocalInScope(value.getLocalInfo());
    constrainType(value, type);
    value.markNonDebugLocalRead();
    return value;
  }

  private Value readRegisterForDebugLocal(int register, DebugLocalInfo local) {
    assert options.debug;
    // TODO(b/111251032): Here we lookup a value with type based on debug info. That's just wrong!
    ValueType type = ValueType.fromDexType(local.type);
    return readRegister(register, type, currentBlock, EdgeType.NON_EDGE, RegisterReadType.DEBUG);
  }

  public Value readRegister(
      int register,
      ValueType type,
      BasicBlock block,
      EdgeType readingEdge,
      RegisterReadType readType) {
    checkRegister(register);
    Value value = block.readCurrentDefinition(register, readingEdge);
    return value != null
        ? value
        : readRegisterRecursive(register, block, readingEdge, type, readType);
  }

  private Value readRegisterRecursive(
      int register,
      BasicBlock block,
      EdgeType readingEdge,
      ValueType type,
      RegisterReadType readType) {
    Value value = null;
    // Iterate back along the predecessor chain as long as there is a single sealed predecessor.
    List<Pair<BasicBlock, EdgeType>> stack = null;
    if (block.isSealed() && block.getPredecessors().size() == 1) {
      stack = new ArrayList<>(blocks.size());
      do {
        assert block.verifyFilledPredecessors();
        BasicBlock pred = block.getPredecessors().get(0);
        EdgeType edgeType = pred.getEdgeType(block);
        checkRegister(register);
        value = pred.readCurrentDefinition(register, edgeType);
        if (value != null) {
          break;
        }
        stack.add(new Pair<>(block, readingEdge));
        block = pred;
        readingEdge = edgeType;
      } while (block.isSealed() && block.getPredecessors().size() == 1);
    }
    // If the register still has unknown value create a phi value for it.
    if (value == null) {
      if (block == entryBlock && readType == RegisterReadType.DEBUG) {
        assert block.getPredecessors().isEmpty();
        // If a debug-local is referenced at the entry block, lazily introduce an SSA value for it.
        // This value must *not* be added to the entry blocks current definitions since
        // uninitialized debug-locals may be referenced at the same register/local-index yet be of
        // different types (eg, int in one part of the CFG and float in a disjoint part).
        value = getUninitializedDebugLocalValue(register, type);
      } else {
        DebugLocalInfo local = getIncomingLocalAtBlock(register, block);
        Phi phi = new Phi(valueNumberGenerator.next(), block, type, local, readType);
        if (!block.isSealed()) {
          block.addIncompletePhi(register, phi, readingEdge);
          value = phi;
        } else {
          // We need to write the phi before adding operands to break cycles. If the phi is trivial
          // and is removed by addOperands, the definition is overwritten and looked up again below.
          block.updateCurrentDefinition(register, phi, readingEdge);
          phi.addOperands(this, register);
          // Lookup the value for the register again at this point. Recursive trivial
          // phi removal could have simplified what we wanted to return here.
          value = block.readCurrentDefinition(register, readingEdge);
        }
      }
    }
    // If the stack of successors is non-empty then update their definitions with the value.
    if (stack != null) {
      for (Pair<BasicBlock, EdgeType> item : stack) {
        item.getFirst().updateCurrentDefinition(register, value, item.getSecond());
      }
    }
    // Update the last block at which the definition was found/created.
    block.updateCurrentDefinition(register, value, readingEdge);
    return value;
  }

  private DebugLocalInfo getIncomingLocalAtBlock(int register, BasicBlock block) {
    if (options.debug) {
      int blockOffset = offsets.getInt(block);
      return source.getIncomingLocalAtBlock(register, blockOffset);
    }
    return null;
  }

  private Value getUninitializedDebugLocalValue(int register, ValueType type) {
    if (uninitializedDebugLocalValues == null) {
      uninitializedDebugLocalValues = new Int2ReferenceOpenHashMap<>();
    }
    List<Value> values = uninitializedDebugLocalValues.get(register);
    if (values != null) {
      for (Value value : values) {
        if (value.outType() == type) {
          return value;
        }
      }
    } else {
      values = new ArrayList<>(2);
      uninitializedDebugLocalValues.put(register, values);
    }
    // Create a new SSA value for the uninitialized local value.
    // Note that the uninitialized local value must not itself have local information, so that it
    // does not contribute to the visible/live-range of the local variable.
    Value value = new Value(valueNumberGenerator.next(), type, null);
    values.add(value);
    return value;
  }

  public Value readNumericRegister(int register, NumericType type) {
    return readRegister(register, ValueType.fromNumericType(type));
  }

  public Value readLiteral(ValueType type, long constant) {
    if (type == ValueType.INT) {
      return readIntLiteral(constant);
    } else {
      assert type == ValueType.LONG;
      return readLongLiteral(constant);
    }
  }

  public Value readLongLiteral(long constant) {
    Value value = new Value(valueNumberGenerator.next(), ValueType.LONG, null);
    ConstNumber number = new ConstNumber(value, constant);
    add(number);
    return number.outValue();
  }

  public Value readIntLiteral(long constant) {
    Value value = new Value(valueNumberGenerator.next(), ValueType.INT, null);
    ConstNumber number = new ConstNumber(value, constant);
    add(number);
    return number.outValue();
  }

  // This special write register is needed when changing the scoping of a local variable.
  // See addDebugLocalStart and addDebugLocalEnd.
  private Value writeRegister(
      int register, ValueType type, ThrowingInfo throwing, DebugLocalInfo local) {
    checkRegister(register);
    Value value = new Value(valueNumberGenerator.next(), type, local);
    currentBlock.writeCurrentDefinition(register, value, throwing);
    return value;
  }

  public Value writeRegister(int register, ValueType type, ThrowingInfo throwing) {
    DebugLocalInfo incomingLocal = getIncomingLocal(register);
    DebugLocalInfo outgoingLocal = getOutgoingLocal(register);
    // If the local info does not change at the current instruction, we need to ensure
    // that the old value is read at the instruction by setting 'previousLocalValue'.
    // If the local info changes, then there must be both an old local ending
    // and a new local starting at the current instruction, and it is up to the SourceCode
    // to ensure that the old local is read when it ends.
    // Furthermore, if incomingLocal != outgoingLocal, then we cannot be sure that
    // the type of the incomingLocal is the same as the type of the outgoingLocal,
    // and we must not call readRegisterIgnoreLocal() with the wrong type.
    previousLocalValue =
        (incomingLocal == null || incomingLocal != outgoingLocal)
            ? null
            : readRegisterForDebugLocal(register, incomingLocal);
    return writeRegister(register, type, throwing, outgoingLocal);
  }

  public Value writeNumericRegister(int register, NumericType type, ThrowingInfo throwing) {
    return writeRegister(register, ValueType.fromNumericType(type), throwing);
  }

  private DebugLocalInfo getIncomingLocal(int register) {
    return options.debug ? source.getIncomingLocal(register) : null;
  }

  private DebugLocalInfo getOutgoingLocal(int register) {
    return options.debug ? source.getOutgoingLocal(register) : null;
  }

  private void checkRegister(int register) {
    if (register < 0) {
      throw new InternalCompilerError("Invalid register");
    }
    if (!source.verifyRegister(register)) {
      throw new CompilationError("Invalid use of register " + register);
    }
  }

  /**
   * Ensure that the current block can hold a throwing instruction. This will create a new current
   * block if the current block has handlers and already has one throwing instruction.
   */
  void ensureBlockForThrowingInstruction() {
    if (!throwingInstructionInCurrentBlock) {
      return;
    }
    // Note that when splitting the block in two we must update the CFG information so that we can
    // correctly identify if the normal exits of the constructed block must be split once it is
    // closed.
    int currentBlockOffset = getOffset(currentBlock);
    BlockInfo currentBlockInfo = getBlockInfo(currentBlockOffset);

    BlockInfo info = new BlockInfo();
    BasicBlock block = info.block;
    block.setNumber(nextBlockNumber++);
    blocks.add(block);

    // Compute some unused offset for the new block and link it in the CFG.
    int freshOffset = INITIAL_BLOCK_OFFSET - 1;
    while (targets.containsKey(freshOffset)) {
      freshOffset--;
    }
    targets.put(freshOffset, info);
    offsets.put(block, freshOffset);

    // Copy over the exceptional successors.
    for (int offset : source.getCurrentCatchHandlers().getUniqueTargets()) {
      info.addExceptionalSuccessor(offset);
      BlockInfo target = targets.get(offset);
      assert !target.block.isSealed();
      target.block.incrementUnfilledPredecessorCount();
      target.addExceptionalPredecessor(freshOffset);
    }

    // Move all normal successors to the new block.
    currentBlockInfo.normalSuccessors.forEach((Integer offset) -> info.addNormalSuccessor(offset));
    currentBlockInfo.normalSuccessors.clear();

    // Link the two blocks.
    addInstruction(new Goto());
    currentBlock.link(block);
    block.incrementUnfilledPredecessorCount();
    currentBlockInfo.addNormalSuccessor(freshOffset);
    info.addNormalPredecessor(currentBlockOffset);

    // The new block can only have a single predecessor and so we don't need to split between them.
    closeCurrentBlockGuaranteedNotToNeedEdgeSplitting();
    setCurrentBlock(block);
  }

  // Private instruction helpers.
  private void addInstruction(Instruction ir) {
    addInstruction(ir, source.getCurrentPosition());
  }

  private void addInstruction(Instruction ir, Position position) {
    hasImpreciseInstructionOutValueTypes |= ir.outValue() != null && !ir.outType().isPreciseType();
    ir.setPosition(position);
    attachLocalValues(ir);
    currentBlock.add(ir);
    if (ir.instructionTypeCanThrow()) {
      assert source.verifyCurrentInstructionCanThrow();
      CatchHandlers<Integer> catchHandlers = source.getCurrentCatchHandlers();
      if (catchHandlers != null) {
        assert !throwingInstructionInCurrentBlock;
        throwingInstructionInCurrentBlock = true;
        List<BasicBlock> targets = new ArrayList<>(catchHandlers.getAllTargets().size());
        // Construct unique move-exception header blocks for each unique target.
        Map<BasicBlock, BasicBlock> moveExceptionHeaders =
            new IdentityHashMap<>(catchHandlers.getUniqueTargets().size());
        for (int targetOffset : catchHandlers.getAllTargets()) {
          BasicBlock target = getTarget(targetOffset);
          BasicBlock header = moveExceptionHeaders.get(target);
          if (header == null) {
            header = new BasicBlock();
            header.incrementUnfilledPredecessorCount();
            moveExceptionHeaders.put(target, header);
            ssaWorklist.add(
                new MoveExceptionWorklistItem(header, currentInstructionOffset, targetOffset));
          }
          targets.add(header);
        }
        currentBlock.linkCatchSuccessors(catchHandlers.getGuards(), targets);
      }
    }
  }

  private void attachLocalValues(Instruction ir) {
    if (!options.debug) {
      assert previousLocalValue == null;
      assert debugLocalEnds.isEmpty();
      return;
    }
    // Add a use if this instruction is overwriting a previous value of the same local.
    if (previousLocalValue != null && previousLocalValue.getLocalInfo() == ir.getLocalInfo()) {
      assert ir.outValue() != null;
      ir.addDebugValue(previousLocalValue);
      previousLocalValue.addDebugLocalEnd(ir);
    }
    // Add reads of locals if any are pending.
    for (Value value : debugLocalEnds) {
      ir.addDebugValue(value);
      value.addDebugLocalEnd(ir);
    }
    previousLocalValue = null;
    debugLocalEnds.clear();
  }

  // Package (ie, SourceCode accessed) helpers.

  // Ensure there is a block starting at offset.
  BlockInfo ensureBlockWithoutEnqueuing(int offset) {
    assert offset != INITIAL_BLOCK_OFFSET;
    BlockInfo info = targets.get(offset);
    if (info == null) {
      // If this is a processed instruction, the block split and it has a fall-through predecessor.
      if (offset >= 0 && isOffsetProcessed(offset)) {
        int blockStartOffset = getBlockStartOffset(offset);
        BlockInfo existing = targets.get(blockStartOffset);
        info = existing.split(blockStartOffset, offset, targets);
      } else {
        info = new BlockInfo();
      }
      targets.put(offset, info);
      offsets.put(info.block, offset);
    }
    return info;
  }

  private int getBlockStartOffset(int offset) {
    if (targets.containsKey(offset)) {
      return offset;
    }
    return targets.headMap(offset).lastIntKey();
  }

  // Ensure there is a block starting at offset and add it to the work-list if it needs processing.
  private BlockInfo ensureBlock(int offset) {
    // We don't enqueue negative targets (these are special blocks, eg, an argument prelude).
    if (offset >= 0 && !isOffsetProcessed(offset)) {
      traceBlocksWorklist.add(offset);
    }
    return ensureBlockWithoutEnqueuing(offset);
  }

  private boolean isOffsetProcessed(int offset) {
    return isIndexProcessed(source.instructionIndex(offset));
  }

  private boolean isIndexProcessed(int index) {
    if (index < processedInstructions.length) {
      return processedInstructions[index];
    }
    ensureSubroutineProcessedInstructions();
    return processedSubroutineInstructions.contains(index);
  }

  private void markIndexProcessed(int index) {
    assert !isIndexProcessed(index);
    if (index < processedInstructions.length) {
      processedInstructions[index] = true;
      return;
    }
    ensureSubroutineProcessedInstructions();
    processedSubroutineInstructions.add(index);
  }

  private void ensureSubroutineProcessedInstructions() {
    if (processedSubroutineInstructions == null) {
      processedSubroutineInstructions = new HashSet<>();
    }
  }

  // Ensure there is a block at offset and add a predecessor to it.
  private void ensureSuccessorBlock(int sourceOffset, int targetOffset, boolean normal) {
    BlockInfo targetInfo = ensureBlock(targetOffset);
    int sourceStartOffset = getBlockStartOffset(sourceOffset);
    BlockInfo sourceInfo = targets.get(sourceStartOffset);
    if (normal) {
      sourceInfo.addNormalSuccessor(targetOffset);
      targetInfo.addNormalPredecessor(sourceStartOffset);
    } else {
      sourceInfo.addExceptionalSuccessor(targetOffset);
      targetInfo.addExceptionalPredecessor(sourceStartOffset);
    }
    targetInfo.block.incrementUnfilledPredecessorCount();
  }

  public void ensureNormalSuccessorBlock(int sourceOffset, int targetOffset) {
    ensureSuccessorBlock(sourceOffset, targetOffset, true);
  }

  void ensureExceptionalSuccessorBlock(int sourceOffset, int targetOffset) {
    ensureSuccessorBlock(sourceOffset, targetOffset, false);
  }

  // Private block helpers.

  private BlockInfo getBlockInfo(int offset) {
    return targets.get(offset);
  }

  private BlockInfo getBlockInfo(BasicBlock block) {
    return getBlockInfo(getOffset(block));
  }

  private BasicBlock getTarget(int offset) {
    return targets.get(offset).block;
  }

  private int getOffset(BasicBlock block) {
    return offsets.getInt(block);
  }

  private void closeCurrentBlockGuaranteedNotToNeedEdgeSplitting() {
    assert currentBlock != null;
    currentBlock.close(this);
    setCurrentBlock(null);
    throwingInstructionInCurrentBlock = false;
    currentInstructionOffset = -1;
    assert debugLocalEnds.isEmpty();
  }

  private void closeCurrentBlock(JumpInstruction jumpInstruction) {
    assert !jumpInstruction.instructionTypeCanThrow();
    assert currentBlock != null;
    assert currentBlock.getInstructions().isEmpty()
        || !currentBlock.getInstructions().getLast().isJumpInstruction();
    generateSplitEdgeBlocks();
    addInstruction(jumpInstruction);
    closeCurrentBlockGuaranteedNotToNeedEdgeSplitting();
  }

  private void closeCurrentBlockWithFallThrough(BasicBlock nextBlock) {
    assert currentBlock != null;
    assert !currentBlock.hasCatchSuccessor(nextBlock);
    currentBlock.link(nextBlock);
    closeCurrentBlock(new Goto());
  }

  private void generateSplitEdgeBlocks() {
    assert currentBlock != null;
    assert currentBlock.isEmpty() || !currentBlock.getInstructions().getLast().isJumpInstruction();
    BlockInfo info = getBlockInfo(currentBlock);
    if (info.hasMoreThanASingleNormalExit()) {
      // Exceptional edges are always split on construction, so no need to split edges to them.
      // Introduce split-edge blocks for all normal edges and push them on the work list.
      for (int successorOffset : info.normalSuccessors) {
        BlockInfo successorInfo = getBlockInfo(successorOffset);
        if (successorInfo.predecessorCount() == 1) {
          // re-add to worklist as a unique succ
          WorklistItem oldItem = null;
          for (WorklistItem item : ssaWorklist) {
            if (item.block == successorInfo.block) {
              oldItem = item;
            }
          }
          assert oldItem.firstInstructionIndex == source.instructionIndex(successorOffset);
          ssaWorklist.remove(oldItem);
          ssaWorklist.add(
              new SplitBlockWorklistItem(
                  oldItem.firstInstructionIndex,
                  oldItem.block,
                  currentInstructionOffset,
                  successorOffset));
        } else {
          BasicBlock splitBlock = createSplitEdgeBlock(currentBlock, successorInfo.block);
          ssaWorklist.add(
              new SplitBlockWorklistItem(
                  -1, splitBlock, currentInstructionOffset, successorOffset));
        }
      }
    } else if (info.normalSuccessors.size() == 1) {
      int successorOffset = info.normalSuccessors.iterator().nextInt();
      source.buildBlockTransfer(this, currentInstructionOffset, successorOffset, false);
    } else {
      // TODO(zerny): Consider refactoring to compute the live-at-exit via callback here too.
      assert info.allSuccessors().isEmpty();
    }
  }

  private static BasicBlock createSplitEdgeBlock(BasicBlock source, BasicBlock target) {
    BasicBlock splitBlock = new BasicBlock();
    splitBlock.incrementUnfilledPredecessorCount();
    splitBlock.getPredecessors().add(source);
    splitBlock.getSuccessors().add(target);
    source.replaceSuccessor(target, splitBlock);
    target.replacePredecessor(source, splitBlock);
    return splitBlock;
  }

  /**
   * Change to control-flow graph to avoid repeated phi operands when all the same values
   * flow in from multiple predecessors.
   *
   * <p> As an example:
   *
   * <pre>
   *
   *              b1          b2         b3
   *              |                       |
   *              ----------\ | /----------
   *
   *                         b4
   *                  v3 = phi(v1, v1, v2)
   * </pre>
   *
   * <p> Is rewritten to:
   *
   * <pre>
   *              b1          b2         b3
   *                  \    /             /
   *                    b5        -------
   *                        \    /
   *                          b4
   *                  v3 = phi(v1, v2)
   *
   * </pre>
   */
  public void joinPredecessorsWithIdenticalPhis() {
    List<BasicBlock> blocksToAdd = new ArrayList<>();
    for (BasicBlock block : blocks) {
      // Consistency check. At this point there should be no incomplete phis.
      // If there are, the input is typically dex code that uses a register
      // that is not defined on all control-flow paths.
      if (block.hasIncompletePhis()) {
        throw new CompilationError(
            "Undefined value encountered during compilation. "
                + "This is typically caused by invalid dex input that uses a register "
                + "that is not define on all control-flow paths leading to the use.");
      }
      if (block.entry() instanceof MoveException) {
        // TODO: Should we support joining in the presence of move-exception instructions?
        continue;
      }
      List<Integer> operandsToRemove = new ArrayList<>();
      Map<ValueList, Integer> values = new HashMap<>();
      Map<Integer, BasicBlock> joinBlocks = new HashMap<>();
      if (block.getPhis().size() > 0) {
        Phi phi = block.getPhis().get(0);
        for (int operandIndex = 0; operandIndex < phi.getOperands().size(); operandIndex++) {
          ValueList v = ValueList.fromPhis(block.getPhis(), operandIndex);
          BasicBlock predecessor = block.getPredecessors().get(operandIndex);
          if (values.containsKey(v)) {
            // Seen before, create a join block (or reuse an existing join block) to join through.
            int otherPredecessorIndex = values.get(v);
            BasicBlock joinBlock = joinBlocks.get(otherPredecessorIndex);
            if (joinBlock == null) {
              joinBlock = BasicBlock.createGotoBlock(blocks.size() + blocksToAdd.size(), block);
              joinBlocks.put(otherPredecessorIndex, joinBlock);
              blocksToAdd.add(joinBlock);
              BasicBlock otherPredecessor = block.getPredecessors().get(otherPredecessorIndex);
              joinBlock.getPredecessors().add(otherPredecessor);
              otherPredecessor.replaceSuccessor(block, joinBlock);
              block.getPredecessors().set(otherPredecessorIndex, joinBlock);
            }
            joinBlock.getPredecessors().add(predecessor);
            predecessor.replaceSuccessor(block, joinBlock);
            operandsToRemove.add(operandIndex);
          } else {
            // Record the value and its predecessor index.
            values.put(v, operandIndex);
          }
        }
      }
      block.removePredecessorsByIndex(operandsToRemove);
      block.removePhisByIndex(operandsToRemove);
    }
    blocks.addAll(blocksToAdd);
  }

  // Other stuff.

  boolean isIntegerType(NumericType type) {
    return type != NumericType.FLOAT && type != NumericType.DOUBLE;
  }

  boolean isNonLongIntegerType(NumericType type) {
    return type != NumericType.FLOAT && type != NumericType.DOUBLE && type != NumericType.LONG;
  }

  public ValueNumberGenerator getValueNumberGenerator() {
    return valueNumberGenerator;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(("blocks:\n"));
    for (BasicBlock block : blocks) {
      builder.append(block.toDetailedString());
      builder.append("\n");
    }
    return builder.toString();
  }
}

