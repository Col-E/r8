// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.Cmp.Bias;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder.BlockInfo;
import com.android.tools.r8.ir.conversion.JarState.Local;
import com.android.tools.r8.ir.conversion.JarState.LocalChangeAtOffset;
import com.android.tools.r8.ir.conversion.JarState.Slot;
import com.android.tools.r8.logging.Log;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiConsumer;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

public class JarSourceCode implements SourceCode {

  // Try-catch block wrapper containing resolved offsets.
  private static class TryCatchBlock {

    private final int handler;
    private final int start;
    private final int end;

    private final String type;

    public TryCatchBlock(TryCatchBlockNode node, JarSourceCode code) {
      this(code.getOffset(node.handler),
          code.getOffset(node.start),
          code.getOffset(node.end),
          node.type);
    }

    private TryCatchBlock(int handler, int start, int end, String type) {
      assert start < end;
      this.handler = handler;
      this.start = start;
      this.end = end;
      this.type = type;
    }

    int getStart() {
      return start;
    }

    int getEnd() {
      return end;
    }

    int getHandler() {
      return handler;
    }

    String getType() {
      return type;
    }
  }

  private static class JarStateWorklistItem {
    BlockInfo blockInfo;
    int instructionIndex;

    public JarStateWorklistItem(BlockInfo blockInfo, int instructionIndex) {
      this.blockInfo = blockInfo;
      this.instructionIndex = instructionIndex;
    }
  }

  // Various descriptors.
  private static final String INT_ARRAY_DESC = "[I";
  private static final String REFLECT_ARRAY_DESC = "Ljava/lang/reflect/Array;";
  private static final String REFLECT_ARRAY_NEW_INSTANCE_NAME = "newInstance";
  private static final String REFLECT_ARRAY_NEW_INSTANCE_DESC =
      "(Ljava/lang/Class;[I)Ljava/lang/Object;";
  private static final String POLYMORPHIC_DEFAULT_SIGNATURE_DESC =
      "([Ljava/lang/Object;)Ljava/lang/Object;";
  private static final String POLYMORPHIC_VARHANDLE_SET_SIGNATURE_DESC =
      "([Ljava/lang/Object;)V";
  private static final String POLYMORPHIC_VARHANDLE_COMPARE_AND_SET_SIGNATURE_DESC =
      "([Ljava/lang/Object;)Z";

  // Various internal names.
  public static final String INTERNAL_NAME_METHOD_HANDLE = "java/lang/invoke/MethodHandle";
  public static final String INTERNAL_NAME_VAR_HANDLE = "java/lang/invoke/VarHandle";

  // Language types.
  static final Type CLASS_TYPE = Type.getObjectType("java/lang/Class");
  static final Type STRING_TYPE = Type.getObjectType("java/lang/String");
  static final Type INT_ARRAY_TYPE = Type.getObjectType(INT_ARRAY_DESC);
  static final Type THROWABLE_TYPE = Type.getObjectType("java/lang/Throwable");
  static final Type METHOD_HANDLE_TYPE = Type.getObjectType(INTERNAL_NAME_METHOD_HANDLE);
  static final Type METHOD_TYPE_TYPE = Type.getObjectType("java/lang/invoke/MethodType");

  private static final int[] NO_TARGETS = {};

  private final JarApplicationReader application;
  private final MethodNode node;
  private final DexType clazz;
  private final List<Type> parameterTypes;
  private final LabelNode initialLabel;

  private TraceMethodVisitor printVisitor = null;

  private final JarState state;
  private AbstractInsnNode currentInstruction = null;

  // Special try-catch block for synchronized methods.
  // This block is given a negative instruction index as it is not part of the instruction stream.
  // The start range of 0 ensures that a new block will start at the first real instruction and
  // thus that the monitor-entry prelude (part of the argument block which must not have a try-catch
  // successor) is not joined with the first instruction block (which likely will have a try-catch
  // successor).
  private static final int EXCEPTIONAL_SYNC_EXIT_OFFSET = -2;
  private static final TryCatchBlock EXCEPTIONAL_SYNC_EXIT =
      new TryCatchBlock(EXCEPTIONAL_SYNC_EXIT_OFFSET, 0, Integer.MAX_VALUE, null);

  // Instruction that enters the monitor. Null if the method is not synchronized.
  private Monitor monitorEnter = null;

  // State to signal that the code currently being emitted is part of synchronization prelude/exits.
  private boolean generatingMethodSynchronization = false;

  // Current position associated with the current instruction during building.
  private Position currentPosition;

  // Canonicalized positions to lower memory usage.
  private final Int2ReferenceMap<Position> canonicalPositions = new Int2ReferenceOpenHashMap<>();

  // Synthetic position with line = 0.
  private Position preamblePosition = null;

  // Cooked position to indicate positions in synthesized code (ie, for synchronization).
  private Position syntheticPosition = null;

  private final DexMethod originalMethod;
  private final Position callerPosition;

  private boolean hasExitingInstruction = false;

  private boolean debug;

  public JarSourceCode(
      DexType clazz,
      MethodNode node,
      JarApplicationReader application,
      DexMethod originalMethod,
      Position callerPosition) {
    assert node != null;
    assert node.desc != null;
    this.node = node;
    this.application = application;
    this.originalMethod = originalMethod;
    this.clazz = clazz;
    this.callerPosition = callerPosition;
    parameterTypes = Arrays.asList(application.getArgumentTypes(node.desc));
    state = new JarState(node.maxLocals, node.localVariables, this, application);
    AbstractInsnNode first = node.instructions.getFirst();
    initialLabel = first instanceof LabelNode ? (LabelNode) first : null;
  }

  private boolean isStatic() {
    return (node.access & Opcodes.ACC_STATIC) > 0;
  }

  /**
   * Determine if we should emit monitor enter/exit instructions at method entry/exit.
   *
   * @return true if we are generating Dex and method is marked synchronized, otherwise false.
   */
  private boolean generateMethodSynchronization() {
    // When generating class files, don't treat the method specially because it is synchronized.
    // At runtime, the JVM will automatically perform the correct monitor enter/exit instructions.
    return !application.options.isGeneratingClassFiles()
        && (node.access & Opcodes.ACC_SYNCHRONIZED) > 0;
  }

  private int formalParameterCount() {
    return parameterTypes.size();
  }

  private int actualArgumentCount() {
    return isStatic() ? formalParameterCount() : formalParameterCount() + 1;
  }

  @Override
  public int instructionCount() {
    return node.instructions.size();
  }

  @Override
  public int instructionIndex(int instructionOffset) {
    return instructionOffset;
  }

  @Override
  public int instructionOffset(int instructionIndex) {
    return instructionIndex;
  }

  @Override
  public boolean verifyRegister(int register) {
    // The register set is dynamically managed by the state so we assume all values valid here.
    return true;
  }

  @Override
  public void setUp() {
    if (Log.ENABLED) {
      Log.debug(JarSourceCode.class, "Computing blocks for:\n" + toString());
    }
  }

  @Override
  public void clear() {

  }

  @Override
  public void buildPrelude(IRBuilder builder) {
    debug = builder.isDebugMode();
    currentPosition = getPreamblePosition();

    state.beginTransactionSynthetic();

    // Record types for arguments.
    Int2ReferenceMap<ValueType> argumentLocals = recordArgumentTypes();
    Int2ReferenceMap<ValueType> initializedLocals = new Int2ReferenceOpenHashMap<>(argumentLocals);
    // Initialize all non-argument locals to ensure safe insertion of debug-local instructions.
    for (Object o : node.localVariables) {
      LocalVariableNode local = (LocalVariableNode) o;
      Type localType;
      ValueType localValueType;
      switch (application.getAsmType(local.desc).getSort()) {
        case Type.OBJECT:
        case Type.ARRAY: {
          localType = JarState.NULL_TYPE;
          localValueType = ValueType.OBJECT;
          break;
        }
        case Type.LONG: {
          localType = Type.LONG_TYPE;
          localValueType = ValueType.LONG;
          break;
        }
        case Type.DOUBLE: {
          localType = Type.DOUBLE_TYPE;
          localValueType = ValueType.DOUBLE;
          break;
        }
        case Type.BOOLEAN:
        case Type.CHAR:
        case Type.BYTE:
        case Type.SHORT:
        case Type.INT: {
          localType = Type.INT_TYPE;
          localValueType = ValueType.INT;
          break;
        }
        case Type.FLOAT: {
          localType = Type.FLOAT_TYPE;
          localValueType = ValueType.FLOAT;
          break;
        }
        case Type.VOID:
        case Type.METHOD:
        default:
          throw new Unreachable("Invalid local variable type: " );
      }
      int localRegister = state.getLocalRegister(local.index, localType);
      ValueType existingLocalType = initializedLocals.get(localRegister);
      if (existingLocalType == null) {
        int writeRegister = state.writeLocal(local.index, localType);
        assert writeRegister == localRegister;
        initializedLocals.put(localRegister, localValueType);
      }
    }

    state.endTransaction();
    state.beginTransaction(0, true);

    // Build the actual argument instructions now that type and debug information is known
    // for arguments.
    buildArgumentInstructions(builder);

    // Add debug information for all locals at the initial label.
    for (Local local : state.getLocalsToOpen()) {
      if (!argumentLocals.containsKey(local.slot.register)) {
        builder.addDebugLocalStart(local.slot.register, local.info);
      }
    }
    state.endTransaction();

    if (generateMethodSynchronization()) {
      generatingMethodSynchronization = true;
      Type clazzType = application.getAsmType(clazz.toDescriptorString());
      int monitorRegister;
      if (isStatic()) {
        // Load the class using a temporary on the stack.
        monitorRegister = state.push(clazzType);
        state.pop();
        builder.addConstClass(monitorRegister, clazz);
      } else {
        assert actualArgumentCount() > 0;
        // The object is stored in the first local.
        monitorRegister = state.readLocal(0, clazzType).register;
      }
      // Build the monitor enter and save it for when generating exits later.
      monitorEnter = builder.addMonitor(Monitor.Type.ENTER, monitorRegister);
      generatingMethodSynchronization = false;
    }
    computeBlockEntryJarStates(builder);
    state.setBuilding();
  }

  private void buildArgumentInstructions(IRBuilder builder) {
    int argumentRegister = 0;
    if (!isStatic()) {
      Type thisType = application.getAsmType(clazz.descriptor.toString());
      Slot slot = state.readLocal(argumentRegister++, thisType);
      builder.addThisArgument(slot.register);
    }
    for (Type type : parameterTypes) {
      ValueType valueType = valueType(type);
      Slot slot = state.readLocal(argumentRegister, type);
      if (type == Type.BOOLEAN_TYPE) {
        builder.addBooleanNonThisArgument(slot.register);
      } else {
        builder.addNonThisArgument(slot.register, valueType);
      }
      argumentRegister += valueType.requiredRegisters();
    }
  }

  private Int2ReferenceMap<ValueType> recordArgumentTypes() {
    Int2ReferenceMap<ValueType> initializedLocals =
        new Int2ReferenceOpenHashMap<>(node.localVariables.size());
    int argumentRegister = 0;
    if (!isStatic()) {
      Type thisType = application.getAsmType(clazz.descriptor.toString());
      int register = state.writeLocal(argumentRegister++, thisType);
      initializedLocals.put(register, valueType(thisType));
    }
    for (Type type : parameterTypes) {
      ValueType valueType = valueType(type);
      int register = state.writeLocal(argumentRegister, type);
      argumentRegister += valueType.requiredRegisters();
      initializedLocals.put(register, valueType);
    }
    return initializedLocals;
  }

  private void computeBlockEntryJarStates(IRBuilder builder) {
    hasExitingInstruction = false;
    Int2ReferenceSortedMap<BlockInfo> CFG = builder.getCFG();
    Queue<JarStateWorklistItem> worklist = new LinkedList<>();
    BlockInfo entry = CFG.get(IRBuilder.INITIAL_BLOCK_OFFSET);
    if (CFG.get(0) != null) {
      entry = CFG.get(0);
    }
    worklist.add(new JarStateWorklistItem(entry, 0));
    state.recordStateForTarget(0);
    for (JarStateWorklistItem item = worklist.poll(); item != null; item = worklist.poll()) {
      state.restoreState(item.instructionIndex);
      state.beginTransactionAtBlockStart(item.instructionIndex);
      state.endTransaction();
      // Iterate each of the instructions in the block to compute the outgoing JarState.
      int instCount = instructionCount();
      int blockEnd = item.instructionIndex + 1;
      while (blockEnd < instCount && !CFG.containsKey(blockEnd)) {
        blockEnd += 1;
      }
      for (int i = item.instructionIndex; i < blockEnd; ++i) {
        boolean hasNextInstruction = i + 1 != blockEnd;
        state.beginTransaction(i + 1, hasNextInstruction);
        AbstractInsnNode insn = getInstruction(i);
        updateState(insn);
        state.endTransaction();
        if (!hasNextInstruction) {
          hasExitingInstruction |=
              isReturn(insn) || (isThrow(insn) && item.blockInfo.exceptionalSuccessors.isEmpty());
        }
      }
      // At the end of the current block, propagate the state to all successors and add the ones
      // that changed to the worklist.
      item.blockInfo.normalSuccessors.iterator().forEachRemaining((Integer offset) -> {
        if (state.recordStateForTarget(offset)) {
          if (offset >= 0) {
            worklist.add(new JarStateWorklistItem(CFG.get(offset.intValue()), offset));
          }
        }
      });
      item.blockInfo.exceptionalSuccessors.iterator().forEachRemaining((Integer offset) -> {
        if (state.recordStateForExceptionalTarget(offset)) {
          if (offset >= 0) {
            worklist.add(new JarStateWorklistItem(CFG.get(offset.intValue()), offset));
          }
        }
      });
    }
    state.restoreState(0);
  }

  @Override
  public void buildPostlude(IRBuilder builder) {
    if (generateMethodSynchronization()) {
      generatingMethodSynchronization = true;
      buildMonitorExit(builder);
      generatingMethodSynchronization = false;
    }
  }

  private void buildExceptionalPostlude(IRBuilder builder) {
    assert generateMethodSynchronization();
    generatingMethodSynchronization = true;
    currentPosition = getExceptionalExitPosition();
    buildMonitorExit(builder);
    builder.addThrow(getMoveExceptionRegister());
    generatingMethodSynchronization = false;
  }

  private void buildMonitorExit(IRBuilder builder) {
    assert generatingMethodSynchronization;
    builder.add(new Monitor(Monitor.Type.EXIT, monitorEnter.inValues().get(0)));
  }

  @Override
  public void buildBlockTransfer(
      IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
    assert currentInstruction == null || predecessorOffset == getOffset(currentInstruction);
    currentInstruction = null;
    if (predecessorOffset == IRBuilder.INITIAL_BLOCK_OFFSET
        || successorOffset == EXCEPTIONAL_SYNC_EXIT_OFFSET) {
      return;
    }
    // The transfer has not yet taken place, so the current position is that of the predecessor,
    // except for exceptional edges where the transfer has already taken place.
    currentPosition =
        getCanonicalDebugPositionAtOffset(isExceptional ? successorOffset : predecessorOffset);

    LocalChangeAtOffset localChange = state.getLocalChange(predecessorOffset, successorOffset);
    if (!isExceptional) {
      for (Local toClose : localChange.getLocalsToClose()) {
        builder.addDebugLocalEnd(toClose.slot.register, toClose.info);
      }
    }
    for (Local toOpen : localChange.getLocalsToOpen()) {
      builder.addDebugLocalStart(toOpen.slot.register, toOpen.info);
    }

    // If there are no explicit exits from the method (ie, this method is a loop without an explict
    // return or an unhandled throw) then we cannot guarantee that a local live in a successor will
    // ensure it is marked as such (via an explict 'end' marker) and thus be live in predecessors.
    // In this case we insert an 'end' point on all explicit goto instructions ensuring that any
    // back-edge will explicitly keep locals live at that point.
    if (!hasExitingInstruction && getInstruction(predecessorOffset).getOpcode() == Opcodes.GOTO) {
      assert !isExceptional;
      for (Local local : localChange.getLocalsToPreserve()) {
        builder.addDebugLocalEnd(local.slot.register, local.info);
      }
    }
  }

  @Override
  public void buildInstruction(
      IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
    if (instructionIndex == EXCEPTIONAL_SYNC_EXIT_OFFSET) {
      buildExceptionalPostlude(builder);
      return;
    }
    AbstractInsnNode insn = getInstruction(instructionIndex);
    currentInstruction = insn;
    assert verifyExceptionEdgesAreRecorded(insn);

    // If a new block is starting here, we restore the computed JarState.
    // current position needs to be compute only for the first instruction of a block, thereafter
    // current position will be updated by LineNumberNode into this block.
    if (firstBlockInstruction || instructionIndex == 0) {
      state.restoreState(instructionIndex);
      currentPosition = getCanonicalDebugPositionAtOffset(instructionIndex);
    }

    String preInstructionState;
    if (Log.ENABLED) {
      preInstructionState = state.toString();
    }

    boolean hasNextInstruction =
        instructionIndex + 1 != instructionCount()
            && !builder.getCFG().containsKey(instructionIndex + 1);
    state.beginTransaction(instructionIndex + 1, hasNextInstruction);
    if (hasNextInstruction) {
      // Explicitly end all locals ending at this point.
      for (Local local : state.getLocalsToClose()) {
        builder.addDebugLocalEnd(local.slot.register, local.info);
      }
    }
    build(insn, builder);
    // If the block continues past this instruction then local state should be updated.
    if (hasNextInstruction) {
      // Ensure starts of locals starting at this point.
      for (Local local : state.getLocalsToOpen()) {
        builder.addDebugLocalStart(local.slot.register, local.info);
      }
    }
    state.endTransaction();

    if (Log.ENABLED && !(insn instanceof LineNumberNode)) {
      int offset = getOffset(insn);
      if (insn instanceof LabelNode) {
        Log.debug(getClass(), "\n%4d: %s",
            offset, instructionToString(insn).replace('\n', ' '));
      } else {
        Log.debug(getClass(), "\n%4d: %s          pre:  %s\n          post: %s",
            offset, instructionToString(insn), preInstructionState, state);
      }
    }

    currentInstruction = null;
  }

  private boolean verifyExceptionEdgesAreRecorded(AbstractInsnNode insn) {
    if (canThrow(insn)) {
      for (TryCatchBlock tryCatchBlock : getTryHandlers(insn)) {
        assert tryCatchBlock.getHandler() == EXCEPTIONAL_SYNC_EXIT_OFFSET
            || state.hasState(tryCatchBlock.getHandler());
      }
    }
    return true;
  }

  @Override
  public void resolveAndBuildSwitch(int value, int fallthroughOffset,
      int payloadOffset, IRBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public void resolveAndBuildNewArrayFilledData(int arrayRef, int payloadOffset,
      IRBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
    return blockOffset == EXCEPTIONAL_SYNC_EXIT_OFFSET
        ? null
        : state.getIncomingLocalAtBlock(register, blockOffset);
  }

  @Override
  public DebugLocalInfo getIncomingLocal(int register) {
    return generatingMethodSynchronization ? null : state.getIncomingLocalInfoForRegister(register);
  }

  @Override
  public DebugLocalInfo getOutgoingLocal(int register) {
    return generatingMethodSynchronization ? null : state.getOutgoingLocalInfoForRegister(register);
  }

  @Override
  public CatchHandlers<Integer> getCurrentCatchHandlers() {
    if (generatingMethodSynchronization) {
      return null;
    }
    List<TryCatchBlock> tryCatchBlocks = getTryHandlers(currentInstruction);
    if (tryCatchBlocks.isEmpty()) {
      return null;
    }
    // TODO(zerny): Compute this more efficiently.
    return new CatchHandlers<>(
        getTryHandlerGuards(tryCatchBlocks),
        getTryHandlerOffsets(tryCatchBlocks));
  }

  @Override
  public int getMoveExceptionRegister(int instructionIndex) {
    return getMoveExceptionRegister();
  }

  // In classfiles the register is always on top of stack.
  private int getMoveExceptionRegister() {
    return state.startOfStack;
  }

  @Override
  public boolean verifyCurrentInstructionCanThrow() {
    return generatingMethodSynchronization || canThrow(currentInstruction);
  }

  @Override
  public boolean verifyLocalInScope(DebugLocalInfo local) {
    for (Local open : state.getLocals()) {
      if (open.info != null && open.info.name == local.name) {
        return true;
      }
    }
    return false;
  }

  private AbstractInsnNode getInstruction(int index) {
    return node.instructions.get(index);
  }

  private static boolean isReturn(AbstractInsnNode insn) {
    return Opcodes.IRETURN <= insn.getOpcode() && insn.getOpcode() <= Opcodes.RETURN;
  }

  private static boolean isSwitch(AbstractInsnNode insn) {
    return Opcodes.TABLESWITCH == insn.getOpcode() || insn.getOpcode() == Opcodes.LOOKUPSWITCH;
  }

  private static boolean isThrow(AbstractInsnNode insn) {
    return Opcodes.ATHROW == insn.getOpcode();
  }

  private static boolean isControlFlowInstruction(AbstractInsnNode insn) {
    return isReturn(insn) || isThrow(insn) || isSwitch(insn) || (insn instanceof JumpInsnNode)
        || insn.getOpcode() == Opcodes.RET;
  }

  private boolean canThrow(AbstractInsnNode insn) {
    switch (insn.getOpcode()) {
      case Opcodes.AALOAD:
      case Opcodes.AASTORE:
      case Opcodes.ANEWARRAY:
        // ARETURN does not throw in its dex image.
      case Opcodes.ARRAYLENGTH:
      case Opcodes.ATHROW:
      case Opcodes.BALOAD:
      case Opcodes.BASTORE:
      case Opcodes.CALOAD:
      case Opcodes.CASTORE:
      case Opcodes.CHECKCAST:
      case Opcodes.DALOAD:
      case Opcodes.DASTORE:
        // DRETURN does not throw in its dex image.
      case Opcodes.FALOAD:
      case Opcodes.FASTORE:
        // FRETURN does not throw in its dex image.
      case Opcodes.GETFIELD:
      case Opcodes.GETSTATIC:
      case Opcodes.IALOAD:
      case Opcodes.IASTORE:
      case Opcodes.IDIV:
      case Opcodes.INSTANCEOF:
      case Opcodes.INVOKEDYNAMIC:
      case Opcodes.INVOKEINTERFACE:
      case Opcodes.INVOKESPECIAL:
      case Opcodes.INVOKESTATIC:
      case Opcodes.INVOKEVIRTUAL:
      case Opcodes.IREM:
        // IRETURN does not throw in its dex image.
      case Opcodes.LALOAD:
      case Opcodes.LASTORE:
      case Opcodes.LDIV:
      case Opcodes.LREM:
        // LRETURN does not throw in its dex image.
      case Opcodes.MONITORENTER:
      case Opcodes.MONITOREXIT:
      case Opcodes.MULTIANEWARRAY:
      case Opcodes.NEW:
      case Opcodes.NEWARRAY:
      case Opcodes.PUTFIELD:
      case Opcodes.PUTSTATIC:
        // RETURN does not throw in its dex image.
      case Opcodes.SALOAD:
      case Opcodes.SASTORE:
        return true;
      case Opcodes.LDC: {
        // const-class and const-string* may throw in dex.
        LdcInsnNode ldc = (LdcInsnNode) insn;
        return ldc.cst instanceof String || ldc.cst instanceof Type || ldc.cst instanceof Handle;
      }
      default:
        return false;
    }
  }

  @Override
  public int traceInstruction(int index, IRBuilder builder) {
    AbstractInsnNode insn = getInstruction(index);
    // Exit early on no-op instructions.
    if (insn instanceof LabelNode || insn instanceof LineNumberNode) {
      return -1;
    }
    // If this instruction exits, close this block.
    if (isReturn(insn)) {
      return index;
    }
    // For each target ensure a basic block and close this block.
    int[] targets = getTargets(insn);
    if (targets != NO_TARGETS) {
      assert !canThrow(insn);
      for (int target : targets) {
        builder.ensureNormalSuccessorBlock(index, target);
      }
      return index;
    }
    if (canThrow(insn)) {
      List<TryCatchBlock> tryCatchBlocks = getTryHandlers(insn);
      if (!tryCatchBlocks.isEmpty()) {
        Set<Integer> seenHandlerOffsets = new HashSet<>();
        for (TryCatchBlock tryCatchBlock : tryCatchBlocks) {
          // Ensure the block starts at the start of the try-range (don't enqueue, not a target).
          builder.ensureBlockWithoutEnqueuing(tryCatchBlock.getStart());
          // Add edge to exceptional successor (only one edge for each unique successor).
          int handler = tryCatchBlock.getHandler();
          if (!seenHandlerOffsets.contains(handler)) {
            seenHandlerOffsets.add(handler);
            builder.ensureExceptionalSuccessorBlock(index, handler);
          }
        }
        // Edge to normal successor if any (fallthrough).
        if (!isThrow(insn)) {
          builder.ensureNormalSuccessorBlock(index, getOffset(insn.getNext()));
        }
        return index;
      }
      // If the throwable instruction is "throw" it closes the block.
      return isThrow(insn) ? index : -1;
    }
    // This instruction does not close the block.
    return -1;
  }

  private List<TryCatchBlock> getPotentialTryHandlers(AbstractInsnNode insn) {
    int offset = getOffset(insn);
    return getPotentialTryHandlers(offset);
  }

  private boolean tryBlockRelevant(TryCatchBlockNode tryHandler, int offset) {
    int start = getOffset(tryHandler.start);
    int end = getOffset(tryHandler.end);
    return start <= offset && offset < end;
  }

  private List<TryCatchBlock> getPotentialTryHandlers(int offset) {
    List<TryCatchBlock> handlers = new ArrayList<>();
    for (int i = 0; i < node.tryCatchBlocks.size(); i++) {
      TryCatchBlockNode tryBlock = (TryCatchBlockNode) node.tryCatchBlocks.get(i);
      if (tryBlockRelevant(tryBlock, offset)) {
        handlers.add(new TryCatchBlock(tryBlock, this));
      }
    }
    return handlers;
  }

  private List<TryCatchBlock> getTryHandlers(AbstractInsnNode insn) {
    List<TryCatchBlock> handlers = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    // The try-catch blocks are ordered by precedence.
    for (TryCatchBlock tryCatchBlock : getPotentialTryHandlers(insn)) {
      if (tryCatchBlock.getType() == null) {
        handlers.add(tryCatchBlock);
        return handlers;
      }
      if (!seen.contains(tryCatchBlock.getType())) {
        seen.add(tryCatchBlock.getType());
        handlers.add(tryCatchBlock);
      }
    }
    if (generateMethodSynchronization()) {
      // Add synchronized exceptional exit for synchronized-method instructions without a default.
      assert handlers.isEmpty() || handlers.get(handlers.size() - 1).getType() != null;
      handlers.add(EXCEPTIONAL_SYNC_EXIT);
    }
    return handlers;
  }

  private List<Integer> getTryHandlerOffsets(List<TryCatchBlock> tryCatchBlocks) {
    List<Integer> offsets = new ArrayList<>();
    for (TryCatchBlock tryCatchBlock : tryCatchBlocks) {
      offsets.add(tryCatchBlock.getHandler());
    }
    return offsets;
  }

  private List<DexType> getTryHandlerGuards(List<TryCatchBlock> tryCatchBlocks) {
    List<DexType> guards = new ArrayList<>();
    for (TryCatchBlock tryCatchBlock : tryCatchBlocks) {
      guards.add(tryCatchBlock.getType() == null
          ? DexItemFactory.catchAllType
          : application.getTypeFromName(tryCatchBlock.getType()));

    }
    return guards;
  }

  int getOffset(AbstractInsnNode insn) {
    return node.instructions.indexOf(insn);
  }

  private int[] getTargets(AbstractInsnNode insn) {
    switch (insn.getType()) {
      case AbstractInsnNode.TABLESWITCH_INSN: {
        TableSwitchInsnNode switchInsn = (TableSwitchInsnNode) insn;
        return getSwitchTargets(switchInsn.dflt, switchInsn.labels);
      }
      case AbstractInsnNode.LOOKUPSWITCH_INSN: {
        LookupSwitchInsnNode switchInsn = (LookupSwitchInsnNode) insn;
        return getSwitchTargets(switchInsn.dflt, switchInsn.labels);
      }
      case AbstractInsnNode.JUMP_INSN: {
        return getJumpTargets((JumpInsnNode) insn);
      }
      case AbstractInsnNode.VAR_INSN: {
        return getVarTargets((VarInsnNode) insn);
      }
      default:
        return NO_TARGETS;
    }
  }

  private int[] getSwitchTargets(LabelNode dflt, List labels) {
    int[] targets = new int[1 + labels.size()];
    targets[0] = getOffset(dflt);
    for (int i = 1; i < targets.length; i++) {
      targets[i] = getOffset((LabelNode) labels.get(i - 1));
    }
    return targets;
  }

  private int[] getJumpTargets(JumpInsnNode jump) {
    switch (jump.getOpcode()) {
      case Opcodes.IFEQ:
      case Opcodes.IFNE:
      case Opcodes.IFLT:
      case Opcodes.IFGE:
      case Opcodes.IFGT:
      case Opcodes.IFLE:
      case Opcodes.IF_ICMPEQ:
      case Opcodes.IF_ICMPNE:
      case Opcodes.IF_ICMPLT:
      case Opcodes.IF_ICMPGE:
      case Opcodes.IF_ICMPGT:
      case Opcodes.IF_ICMPLE:
      case Opcodes.IF_ACMPEQ:
      case Opcodes.IF_ACMPNE:
      case Opcodes.IFNULL:
      case Opcodes.IFNONNULL:
        return new int[]{getOffset(jump.label), getOffset(jump.getNext())};
      case Opcodes.GOTO:
        return new int[]{getOffset(jump.label)};
      case Opcodes.JSR: {
        throw new Unreachable("JSR should be handled by the ASM jsr inliner");
      }
      default:
        throw new Unreachable("Unexpected opcode in jump instruction: " + jump);
    }
  }

  private int[] getVarTargets(VarInsnNode insn) {
    if (insn.getOpcode() == Opcodes.RET) {
      throw new Unreachable("RET should be handled by the ASM jsr inliner");
    }
    return NO_TARGETS;
  }

  // Type conversion helpers.

  private static ValueType valueType(Type type) {
    switch (type.getSort()) {
      case Type.ARRAY:
      case Type.OBJECT:
        return ValueType.OBJECT;
      case Type.BOOLEAN:
      case Type.BYTE:
      case Type.SHORT:
      case Type.CHAR:
      case Type.INT:
        return ValueType.INT;
      case Type.FLOAT:
        return ValueType.FLOAT;
      case Type.LONG:
        return ValueType.LONG;
      case Type.DOUBLE:
        return ValueType.DOUBLE;
      case Type.VOID:
        // Illegal. Throws in fallthrough.
      default:
        throw new Unreachable("Invalid type in valueType: " + type);
    }
  }

  private static MemberType memberType(Type type) {
    switch (type.getSort()) {
      case Type.ARRAY:
      case Type.OBJECT:
        return MemberType.OBJECT;
      case Type.BOOLEAN:
        return MemberType.BOOLEAN;
      case Type.BYTE:
        return MemberType.BYTE;
      case Type.SHORT:
        return MemberType.SHORT;
      case Type.CHAR:
        return MemberType.CHAR;
      case Type.INT:
        return MemberType.INT;
      case Type.FLOAT:
        return MemberType.FLOAT;
      case Type.LONG:
        return MemberType.LONG;
      case Type.DOUBLE:
        return MemberType.DOUBLE;
      case Type.VOID:
        // Illegal. Throws in fallthrough.
      default:
        throw new Unreachable("Invalid type in memberType: " + type);
    }
  }

  private MemberType memberType(String fieldDesc) {
    return memberType(application.getAsmType(fieldDesc));
  }

  private static NumericType numericType(Type type) {
    switch (type.getSort()) {
      case Type.BYTE:
        return NumericType.BYTE;
      case Type.CHAR:
        return NumericType.CHAR;
      case Type.SHORT:
        return NumericType.SHORT;
      case Type.INT:
        return NumericType.INT;
      case Type.LONG:
        return NumericType.LONG;
      case Type.FLOAT:
        return NumericType.FLOAT;
      case Type.DOUBLE:
        return NumericType.DOUBLE;
      default:
        throw new Unreachable("Invalid type in numericType: " + type);
    }
  }

  private Invoke.Type invokeType(MethodInsnNode method) {
    switch (method.getOpcode()) {
      case Opcodes.INVOKEVIRTUAL:
        if (isCallToPolymorphicSignatureMethod(method.owner, method.name)) {
          return Invoke.Type.POLYMORPHIC;
        }
        return Invoke.Type.VIRTUAL;
      case Opcodes.INVOKESTATIC:
        return Invoke.Type.STATIC;
      case Opcodes.INVOKEINTERFACE:
        return Invoke.Type.INTERFACE;
      case Opcodes.INVOKESPECIAL: {
        // This is actually incorrect unless the input was verified. The spec says that it has
        // invoke super semantics, if the type is a supertype of the current class. If it is the
        // same or a subtype, it has invoke direct semantics. The latter case is illegal, so we
        // map it to a super call here. In R8, we abort at a later stage (see.
        // See also <a href=
        // "https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-6.html#jvms-6.5.invokespecial"
        // </a> for invokespecial dispatch and <a href="https://docs.oracle.com/javase/specs/jvms/"
        // "se7/html/jvms-4.html#jvms-4.10.1.9.invokespecial"</a> for verification requirements. In
        // particular, the requirement
        //   isAssignable(class(CurrentClassName, L), class(MethodClassName, L)).
        DexType owner = application.getTypeFromName(method.owner);
        if (owner == clazz || method.name.equals(Constants.INSTANCE_INITIALIZER_NAME)) {
          return Invoke.Type.DIRECT;
        } else {
          return Invoke.Type.SUPER;
        }
      }
      default:
        throw new Unreachable("Unexpected MethodInsnNode opcode: " + method.getOpcode());
    }
  }

  private Type makeArrayType(Type elementType) {
    return application.getAsmObjectType("[" + elementType.getDescriptor());
  }

  private static String arrayTypeDesc(int arrayTypeCode) {
    switch (arrayTypeCode) {
      case Opcodes.T_BOOLEAN:
        return "[Z";
      case Opcodes.T_CHAR:
        return "[C";
      case Opcodes.T_FLOAT:
        return "[F";
      case Opcodes.T_DOUBLE:
        return "[D";
      case Opcodes.T_BYTE:
        return "[B";
      case Opcodes.T_SHORT:
        return "[S";
      case Opcodes.T_INT:
        return "[I";
      case Opcodes.T_LONG:
        return "[J";
      default:
        throw new Unreachable("Unexpected array-type code " + arrayTypeCode);
    }
  }

  private static Type getArrayElementTypeForOpcode(int opcode) {
    switch (opcode) {
      case Opcodes.IALOAD:
      case Opcodes.IASTORE:
        return Type.INT_TYPE;
      case Opcodes.FALOAD:
      case Opcodes.FASTORE:
        return Type.FLOAT_TYPE;
      case Opcodes.LALOAD:
      case Opcodes.LASTORE:
        return Type.LONG_TYPE;
      case Opcodes.DALOAD:
      case Opcodes.DASTORE:
        return Type.DOUBLE_TYPE;
      case Opcodes.AALOAD:
      case Opcodes.AASTORE:
        return JarState.NULL_TYPE; // We might not know the type.
      case Opcodes.BALOAD:
      case Opcodes.BASTORE:
        return Type.BYTE_TYPE; // We don't distinguish byte and boolean.
      case Opcodes.CALOAD:
      case Opcodes.CASTORE:
        return Type.CHAR_TYPE;
      case Opcodes.SALOAD:
      case Opcodes.SASTORE:
        return Type.SHORT_TYPE;
      default:
        throw new Unreachable("Unexpected array opcode " + opcode);
    }
  }

  private static boolean isCompatibleArrayElementType(int opcode, Type type) {
    switch (opcode) {
      case Opcodes.IALOAD:
      case Opcodes.IASTORE:
        return Slot.isCompatible(type, Type.INT_TYPE);
      case Opcodes.FALOAD:
      case Opcodes.FASTORE:
        return Slot.isCompatible(type, Type.FLOAT_TYPE);
      case Opcodes.LALOAD:
      case Opcodes.LASTORE:
        return Slot.isCompatible(type, Type.LONG_TYPE);
      case Opcodes.DALOAD:
      case Opcodes.DASTORE:
        return Slot.isCompatible(type, Type.DOUBLE_TYPE);
      case Opcodes.AALOAD:
      case Opcodes.AASTORE:
        return Slot.isCompatible(type, JarState.REFERENCE_TYPE);
      case Opcodes.BALOAD:
      case Opcodes.BASTORE:
        return Slot.isCompatible(type, Type.BYTE_TYPE)
            || Slot.isCompatible(type, Type.BOOLEAN_TYPE);
      case Opcodes.CALOAD:
      case Opcodes.CASTORE:
        return Slot.isCompatible(type, Type.CHAR_TYPE);
      case Opcodes.SALOAD:
      case Opcodes.SASTORE:
        return Slot.isCompatible(type, Type.SHORT_TYPE);
      default:
        throw new Unreachable("Unexpected array opcode " + opcode);
    }
  }

  private static If.Type ifType(int opcode) {
    switch (opcode) {
      case Opcodes.IFEQ:
      case Opcodes.IF_ICMPEQ:
      case Opcodes.IF_ACMPEQ:
        return If.Type.EQ;
      case Opcodes.IFNE:
      case Opcodes.IF_ICMPNE:
      case Opcodes.IF_ACMPNE:
        return If.Type.NE;
      case Opcodes.IFLT:
      case Opcodes.IF_ICMPLT:
        return If.Type.LT;
      case Opcodes.IFGE:
      case Opcodes.IF_ICMPGE:
        return If.Type.GE;
      case Opcodes.IFGT:
      case Opcodes.IF_ICMPGT:
        return If.Type.GT;
      case Opcodes.IFLE:
      case Opcodes.IF_ICMPLE:
        return If.Type.LE;
      default:
        throw new Unreachable("Unexpected If instruction opcode: " + opcode);
    }
  }

  private static Type opType(int opcode) {
    switch (opcode) {
      case Opcodes.IADD:
      case Opcodes.ISUB:
      case Opcodes.IMUL:
      case Opcodes.IDIV:
      case Opcodes.IREM:
      case Opcodes.INEG:
      case Opcodes.ISHL:
      case Opcodes.ISHR:
      case Opcodes.IUSHR:
        return Type.INT_TYPE;
      case Opcodes.LADD:
      case Opcodes.LSUB:
      case Opcodes.LMUL:
      case Opcodes.LDIV:
      case Opcodes.LREM:
      case Opcodes.LNEG:
      case Opcodes.LSHL:
      case Opcodes.LSHR:
      case Opcodes.LUSHR:
        return Type.LONG_TYPE;
      case Opcodes.FADD:
      case Opcodes.FSUB:
      case Opcodes.FMUL:
      case Opcodes.FDIV:
      case Opcodes.FREM:
      case Opcodes.FNEG:
        return Type.FLOAT_TYPE;
      case Opcodes.DADD:
      case Opcodes.DSUB:
      case Opcodes.DMUL:
      case Opcodes.DDIV:
      case Opcodes.DREM:
      case Opcodes.DNEG:
        return Type.DOUBLE_TYPE;
      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }

  // State updating procedures.

  private void updateState(AbstractInsnNode insn) {
    switch (insn.getType()) {
      case AbstractInsnNode.INSN:
        updateState((InsnNode) insn);
        break;
      case AbstractInsnNode.INT_INSN:
        updateState((IntInsnNode) insn);
        break;
      case AbstractInsnNode.VAR_INSN:
        updateState((VarInsnNode) insn);
        break;
      case AbstractInsnNode.TYPE_INSN:
        updateState((TypeInsnNode) insn);
        break;
      case AbstractInsnNode.FIELD_INSN:
        updateState((FieldInsnNode) insn);
        break;
      case AbstractInsnNode.METHOD_INSN:
        updateState((MethodInsnNode) insn);
        break;
      case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
        updateState((InvokeDynamicInsnNode) insn);
        break;
      case AbstractInsnNode.JUMP_INSN:
        updateState((JumpInsnNode) insn);
        break;
      case AbstractInsnNode.LABEL:
        updateState((LabelNode) insn);
        break;
      case AbstractInsnNode.LDC_INSN:
        updateState((LdcInsnNode) insn);
        break;
      case AbstractInsnNode.IINC_INSN:
        updateState((IincInsnNode) insn);
        break;
      case AbstractInsnNode.TABLESWITCH_INSN:
        updateState((TableSwitchInsnNode) insn);
        break;
      case AbstractInsnNode.LOOKUPSWITCH_INSN:
        updateState((LookupSwitchInsnNode) insn);
        break;
      case AbstractInsnNode.MULTIANEWARRAY_INSN:
        updateState((MultiANewArrayInsnNode) insn);
        break;
      case AbstractInsnNode.LINE:
        updateState((LineNumberNode) insn);
        break;
      default:
        throw new Unreachable("Unexpected instruction " + insn);
    }
  }

  private void updateState(InsnNode insn) {
    int opcode = insn.getOpcode();
    switch (opcode) {
      case Opcodes.NOP:
        // Intentionally left empty.
        break;
      case Opcodes.ACONST_NULL:
        state.push(JarState.NULL_TYPE);
        break;
      case Opcodes.ICONST_M1:
      case Opcodes.ICONST_0:
      case Opcodes.ICONST_1:
      case Opcodes.ICONST_2:
      case Opcodes.ICONST_3:
      case Opcodes.ICONST_4:
      case Opcodes.ICONST_5:
        state.push(Type.INT_TYPE);
        break;
      case Opcodes.LCONST_0:
      case Opcodes.LCONST_1:
        state.push(Type.LONG_TYPE);
        break;
      case Opcodes.FCONST_0:
      case Opcodes.FCONST_1:
      case Opcodes.FCONST_2:
        state.push(Type.FLOAT_TYPE);
        break;
      case Opcodes.DCONST_0:
      case Opcodes.DCONST_1:
        state.push(Type.DOUBLE_TYPE);
        break;
      case Opcodes.IALOAD:
      case Opcodes.LALOAD:
      case Opcodes.FALOAD:
      case Opcodes.DALOAD:
      case Opcodes.AALOAD:
      case Opcodes.BALOAD:
      case Opcodes.CALOAD:
      case Opcodes.SALOAD: {
        state.pop();
        Type elementType = state.pop(JarState.ARRAY_TYPE).getArrayElementType();
        if (elementType == null) {
          // We propagate the byte-or-bool type, which will then get resolved to an
          // actual type if we have a concrete byte type or bool type on another flow edge.
          elementType = (Opcodes.BALOAD == opcode)
              ? JarState.BYTE_OR_BOOL_TYPE
              : getArrayElementTypeForOpcode(opcode);
        }
        state.push(elementType);
        break;
      }
      case Opcodes.IASTORE:
      case Opcodes.LASTORE:
      case Opcodes.FASTORE:
      case Opcodes.DASTORE:
      case Opcodes.AASTORE:
      case Opcodes.BASTORE:
      case Opcodes.CASTORE:
      case Opcodes.SASTORE: {
        state.pop();
        state.pop();
        state.pop();
        break;
      }
      case Opcodes.POP: {
        Slot value = state.pop();
        value.isCategory1();
        break;
      }
      case Opcodes.POP2: {
        Slot value = state.pop();
        if (value.isCategory1()) {
          Slot value2 = state.pop();
          assert value2.isCategory1();
        }
        break;
      }
      case Opcodes.DUP: {
        Slot value = state.peek();
        assert value.isCategory1();
        state.push(value.type);
        break;
      }
      case Opcodes.DUP_X1: {
        // Stack transformation: ..., v2, v1 -> ..., v1, v2, v1
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1() && value2.isCategory1();
        int stack2 = state.push(value1.type);
        int stack1 = state.push(value2.type);
        state.push(value1.type);
        assert value2.register == stack2;
        assert value1.register == stack1;
        break;
      }
      case Opcodes.DUP_X2: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1();
        if (value2.isCategory1()) {
          Slot value3 = state.pop();
          assert value3.isCategory1();
          // Stack transformation: ..., v3, v2, v1 -> ..., v1, v3, v2, v1
          updateStateForDupOneBelowTwo(value3, value2, value1);
        } else {
          // Stack transformation: ..., w2, v1 -> ..., v1, w2, v1
          updateStateForDupOneBelowOne(value2, value1);
        }
        break;
      }
      case Opcodes.DUP2: {
        Slot value1 = state.pop();
        if (value1.isCategory1()) {
          Slot value2 = state.pop();
          // Stack transformation: ..., v2, v1 -> ..., v2, v1, v2, v1
          assert value2.isCategory1();
          state.push(value2.type);
          state.push(value1.type);
          state.push(value2.type);
          state.push(value1.type);
        } else {
          // Stack transformation: ..., w1 -> ..., w1, w1
          state.push(value1.type);
          state.push(value1.type);
        }
        break;
      }
      case Opcodes.DUP2_X1: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value2.isCategory1();
        if (value1.isCategory1()) {
          // Stack transformation: ..., v3, v2, v1 -> v2, v1, v3, v2, v1
          Slot value3 = state.pop();
          assert value3.isCategory1();
          updateStateForDupTwoBelowOne(value3, value2, value1);
        } else {
          // Stack transformation: ..., v2, w1 -> ..., w1, v2, w1
          updateStateForDupOneBelowOne(value2, value1);
        }
        break;
      }
      case Opcodes.DUP2_X2: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        if (!value1.isCategory1() && !value2.isCategory1()) {
          // State transformation: ..., w2, w1 -> w1, w2, w1
          updateStateForDupOneBelowOne(value2, value1);
        } else {
          Slot value3 = state.pop();
          if (!value1.isCategory1()) {
            assert value2.isCategory1();
            assert value3.isCategory1();
            // State transformation: ..., v3, v2, w1 -> w1, v3, v2, w1
            updateStateForDupOneBelowTwo(value3, value2, value1);
          } else if (!value3.isCategory1()) {
            assert value1.isCategory1();
            assert value2.isCategory1();
            // State transformation: ..., w3, v2, v1 -> v2, v1, w3, v2, v1
            updateStateForDupTwoBelowOne(value3, value2, value1);
          } else {
            Slot value4 = state.pop();
            assert value1.isCategory1();
            assert value2.isCategory1();
            assert value3.isCategory1();
            assert value4.isCategory1();
            // State transformation: ..., v4, v3, v2, v1 -> v2, v1, v4, v3, v2, v1
            updateStateForDupTwoBelowTwo(value4, value3, value2, value1);
          }
        }
        break;
      }
      case Opcodes.SWAP: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1() && value2.isCategory1();
        state.push(value1.type);
        state.push(value2.type);
        break;
      }
      case Opcodes.IADD:
      case Opcodes.LADD:
      case Opcodes.FADD:
      case Opcodes.DADD:
      case Opcodes.ISUB:
      case Opcodes.LSUB:
      case Opcodes.FSUB:
      case Opcodes.DSUB:
      case Opcodes.IMUL:
      case Opcodes.LMUL:
      case Opcodes.FMUL:
      case Opcodes.DMUL:
      case Opcodes.IDIV:
      case Opcodes.LDIV:
      case Opcodes.FDIV:
      case Opcodes.DDIV:
      case Opcodes.IREM:
      case Opcodes.LREM:
      case Opcodes.FREM:
      case Opcodes.DREM: {
        Type type = opType(opcode);
        state.pop();
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.INEG:
      case Opcodes.LNEG:
      case Opcodes.FNEG:
      case Opcodes.DNEG: {
        Type type = opType(opcode);
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.ISHL:
      case Opcodes.LSHL:
      case Opcodes.ISHR:
      case Opcodes.LSHR:
      case Opcodes.IUSHR:
      case Opcodes.LUSHR: {
        Type type = opType(opcode);
        state.pop();
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.IAND:
      case Opcodes.LAND: {
        Type type = opcode == Opcodes.IAND ? Type.INT_TYPE : Type.LONG_TYPE;
        state.pop();
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.IOR:
      case Opcodes.LOR: {
        Type type = opcode == Opcodes.IOR ? Type.INT_TYPE : Type.LONG_TYPE;
        state.pop();
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.IXOR:
      case Opcodes.LXOR: {
        Type type = opcode == Opcodes.IXOR ? Type.INT_TYPE : Type.LONG_TYPE;
        state.pop();
        state.pop();
        state.push(type);
        break;
      }
      case Opcodes.I2L:
        updateStateForConversion(Type.INT_TYPE, Type.LONG_TYPE);
        break;
      case Opcodes.I2F:
        updateStateForConversion(Type.INT_TYPE, Type.FLOAT_TYPE);
        break;
      case Opcodes.I2D:
        updateStateForConversion(Type.INT_TYPE, Type.DOUBLE_TYPE);
        break;
      case Opcodes.L2I:
        updateStateForConversion(Type.LONG_TYPE, Type.INT_TYPE);
        break;
      case Opcodes.L2F:
        updateStateForConversion(Type.LONG_TYPE, Type.FLOAT_TYPE);
        break;
      case Opcodes.L2D:
        updateStateForConversion(Type.LONG_TYPE, Type.DOUBLE_TYPE);
        break;
      case Opcodes.F2I:
        updateStateForConversion(Type.FLOAT_TYPE, Type.INT_TYPE);
        break;
      case Opcodes.F2L:
        updateStateForConversion(Type.FLOAT_TYPE, Type.LONG_TYPE);
        break;
      case Opcodes.F2D:
        updateStateForConversion(Type.FLOAT_TYPE, Type.DOUBLE_TYPE);
        break;
      case Opcodes.D2I:
        updateStateForConversion(Type.DOUBLE_TYPE, Type.INT_TYPE);
        break;
      case Opcodes.D2L:
        updateStateForConversion(Type.DOUBLE_TYPE, Type.LONG_TYPE);
        break;
      case Opcodes.D2F:
        updateStateForConversion(Type.DOUBLE_TYPE, Type.FLOAT_TYPE);
        break;
      case Opcodes.I2B:
        updateStateForConversion(Type.INT_TYPE, Type.BYTE_TYPE);
        break;
      case Opcodes.I2C:
        updateStateForConversion(Type.INT_TYPE, Type.CHAR_TYPE);
        break;
      case Opcodes.I2S:
        updateStateForConversion(Type.INT_TYPE, Type.SHORT_TYPE);
        break;
      case Opcodes.LCMP: {
        state.pop();
        state.pop();
        state.push(Type.INT_TYPE);
        break;
      }
      case Opcodes.FCMPL:
      case Opcodes.FCMPG: {
        state.pop();
        state.pop();
        state.push(Type.INT_TYPE);
        break;
      }
      case Opcodes.DCMPL:
      case Opcodes.DCMPG: {
        state.pop();
        state.pop();
        state.push(Type.INT_TYPE);
        break;
      }
      case Opcodes.IRETURN: {
        state.pop();
        break;
      }
      case Opcodes.LRETURN: {
        state.pop();
        break;
      }
      case Opcodes.FRETURN: {
        state.pop();
        break;
      }
      case Opcodes.DRETURN: {
        state.pop();
        break;
      }
      case Opcodes.ARETURN: {
        state.pop(JarState.REFERENCE_TYPE);
        break;
      }
      case Opcodes.RETURN: {
        break;
      }
      case Opcodes.ARRAYLENGTH: {
        state.pop(JarState.ARRAY_TYPE);
        state.push(Type.INT_TYPE);
        break;
      }
      case Opcodes.ATHROW: {
        state.pop(JarState.OBJECT_TYPE);
        break;
      }
      case Opcodes.MONITORENTER: {
        state.pop(JarState.REFERENCE_TYPE);
        break;
      }
      case Opcodes.MONITOREXIT: {
        state.pop(JarState.REFERENCE_TYPE);
        break;
      }
      default:
        throw new Unreachable("Unexpected Insn opcode: " + insn.getOpcode());
    }
  }

  private void updateStateForDupOneBelowTwo(Slot value3, Slot value2, Slot value1) {
    state.push(value1.type);
    state.push(value3.type);
    state.push(value2.type);
    state.push(value1.type);
  }

  private void updateStateForDupOneBelowOne(Slot value2, Slot value1) {
    state.push(value1.type);
    state.push(value2.type);
    state.push(value1.type);
  }

  private void updateStateForDupTwoBelowOne(Slot value3, Slot value2, Slot value1) {
    state.push(value2.type);
    state.push(value1.type);
    state.push(value3.type);
    state.push(value2.type);
    state.push(value1.type);
  }

  private void updateStateForDupTwoBelowTwo(Slot value4, Slot value3, Slot value2, Slot value1) {
    state.push(value2.type);
    state.push(value1.type);
    state.push(value4.type);
    state.push(value3.type);
    state.push(value2.type);
    state.push(value1.type);
  }

  private void updateState(IntInsnNode insn) {
    switch (insn.getOpcode()) {
      case Opcodes.BIPUSH:
      case Opcodes.SIPUSH: {
        state.push(Type.INT_TYPE);
        break;
      }
      case Opcodes.NEWARRAY: {
        String desc = arrayTypeDesc(insn.operand);
        Type type = application.getAsmType(desc);
        state.pop();
        state.push(type);
        break;
      }
      default:
        throw new Unreachable("Unexpected IntInsn opcode: " + insn.getOpcode());
    }
  }

  private void updateState(VarInsnNode insn) {
    int opcode = insn.getOpcode();
    Type expectedType;
    switch (opcode) {
      case Opcodes.ILOAD:
      case Opcodes.ISTORE:
        expectedType = Type.INT_TYPE;
        break;
      case Opcodes.FLOAD:
      case Opcodes.FSTORE:
        expectedType = Type.FLOAT_TYPE;
        break;
      case Opcodes.LLOAD:
      case Opcodes.LSTORE:
        expectedType = Type.LONG_TYPE;
        break;
      case Opcodes.DLOAD:
      case Opcodes.DSTORE:
        expectedType = Type.DOUBLE_TYPE;
        break;
      case Opcodes.ALOAD:
      case Opcodes.ASTORE:
        expectedType = JarState.REFERENCE_TYPE;
        break;
      case Opcodes.RET: {
        throw new Unreachable("RET should be handled by the ASM jsr inliner");
      }
      default:
        throw new Unreachable("Unexpected VarInsn opcode: " + insn.getOpcode());
    }
    if (Opcodes.ILOAD <= opcode && opcode <= Opcodes.ALOAD) {
      Slot src = state.readLocal(insn.var, expectedType);
      state.push(src.type);
    } else {
      assert Opcodes.ISTORE <= opcode && opcode <= Opcodes.ASTORE;
      Slot slot = state.pop();
      if (slot.type == JarState.NULL_TYPE && expectedType != JarState.REFERENCE_TYPE) {
        state.writeLocal(insn.var, expectedType);
      } else {
        state.writeLocal(insn.var, slot.type);
      }
    }
  }

  private void updateState(TypeInsnNode insn) {
    Type type = application.getAsmObjectType(insn.desc);
    switch (insn.getOpcode()) {
      case Opcodes.NEW: {
        state.push(type);
        break;
      }
      case Opcodes.ANEWARRAY: {
        Type arrayType = makeArrayType(type);
        state.pop();
        state.push(arrayType);
        break;
      }
      case Opcodes.CHECKCAST: {
        // Pop the top value and push it back on with the checked type.
        state.pop(type);
        state.push(type);
        break;
      }
      case Opcodes.INSTANCEOF: {
        state.pop(JarState.REFERENCE_TYPE);
        state.push(Type.INT_TYPE);
        break;
      }
      default:
        throw new Unreachable("Unexpected TypeInsn opcode: " + insn.getOpcode());
    }

  }

  private void updateState(FieldInsnNode insn) {
    Type type = application.getAsmType(insn.desc);
    switch (insn.getOpcode()) {
      case Opcodes.GETSTATIC:
        state.push(type);
        break;
      case Opcodes.PUTSTATIC:
        state.pop();
        break;
      case Opcodes.GETFIELD: {
        state.pop(JarState.OBJECT_TYPE);
        state.push(type);
        break;
      }
      case Opcodes.PUTFIELD: {
        state.pop();
        state.pop(JarState.OBJECT_TYPE);
        break;
      }
      default:
        throw new Unreachable("Unexpected FieldInsn opcode: " + insn.getOpcode());
    }
  }

  private void updateState(MethodInsnNode insn) {
    updateStateForInvoke(insn.desc, insn.getOpcode() != Opcodes.INVOKESTATIC);
  }

  private void updateState(InvokeDynamicInsnNode insn) {
    updateStateForInvoke(insn.desc, false /* receiver passed explicitly */);
  }

  private void updateStateForInvoke(String desc, boolean implicitReceiver) {
    // Pop arguments.
    state.popReverse(application.getArgumentCount(desc));
    // Pop implicit receiver if needed.
    if (implicitReceiver) {
      state.pop();
    }
    // Push return value if needed.
    Type returnType = application.getReturnType(desc);
    if (returnType != Type.VOID_TYPE) {
      state.push(returnType);
    }
  }

  private void updateState(JumpInsnNode insn) {
    int[] targets = getTargets(insn);
    int opcode = insn.getOpcode();
    if (Opcodes.IFEQ <= opcode && opcode <= Opcodes.IF_ACMPNE) {
      assert targets.length == 2;
      if (opcode <= Opcodes.IFLE) {
        state.pop();
      } else {
        state.pop();
        state.pop();
      }
    } else {
      switch (opcode) {
        case Opcodes.GOTO: {
          assert targets.length == 1;
          break;
        }
        case Opcodes.IFNULL:
        case Opcodes.IFNONNULL: {
          state.pop();
          break;
        }
        case Opcodes.JSR: {
          throw new Unreachable("JSR should be handled by the ASM jsr inliner");
        }
        default:
          throw new Unreachable("Unexpected JumpInsn opcode: " + insn.getOpcode());
      }
    }
  }

  private void updateState(LabelNode insn) {
    // Intentionally empty.
  }

  private void updateState(LdcInsnNode insn) {
    if (insn.cst instanceof Type) {
      Type type = (Type) insn.cst;
      state.push(type);
    } else if (insn.cst instanceof String) {
      state.push(STRING_TYPE);
    } else if (insn.cst instanceof Long) {
      state.push(Type.LONG_TYPE);
    } else if (insn.cst instanceof Double) {
      state.push(Type.DOUBLE_TYPE);
    } else if (insn.cst instanceof Integer) {
      state.push(Type.INT_TYPE);
    } else if (insn.cst instanceof Float) {
      state.push(Type.FLOAT_TYPE);
    } else if (insn.cst instanceof Handle) {
      state.push(METHOD_HANDLE_TYPE);
    } else {
      throw new CompilationError("Unsupported constant: " + insn.cst.toString());
    }
  }

  private void updateState(IincInsnNode insn) {
    state.readLocal(insn.var, Type.INT_TYPE);
  }

  private void updateState(TableSwitchInsnNode insn) {
    state.pop();
  }

  private void updateState(LookupSwitchInsnNode insn) {
    state.pop();
  }

  private void updateState(MultiANewArrayInsnNode insn) {
    // Type of the full array.
    Type arrayType = application.getAsmObjectType(insn.desc);
    state.popReverse(insn.dims, Type.INT_TYPE);
    state.push(arrayType);
  }

  private void updateState(LineNumberNode insn) {
    // Intentionally empty.
  }

  private void updateStateForConversion(Type from, Type to) {
    state.pop();
    state.push(to);
  }

  // IR instruction building procedures.

  private void build(AbstractInsnNode insn, IRBuilder builder) {
    switch (insn.getType()) {
      case AbstractInsnNode.INSN:
        build((InsnNode) insn, builder);
        break;
      case AbstractInsnNode.INT_INSN:
        build((IntInsnNode) insn, builder);
        break;
      case AbstractInsnNode.VAR_INSN:
        build((VarInsnNode) insn, builder);
        break;
      case AbstractInsnNode.TYPE_INSN:
        build((TypeInsnNode) insn, builder);
        break;
      case AbstractInsnNode.FIELD_INSN:
        build((FieldInsnNode) insn, builder);
        break;
      case AbstractInsnNode.METHOD_INSN:
        build((MethodInsnNode) insn, builder);
        break;
      case AbstractInsnNode.INVOKE_DYNAMIC_INSN:
        build((InvokeDynamicInsnNode) insn, builder);
        break;
      case AbstractInsnNode.JUMP_INSN:
        build((JumpInsnNode) insn, builder);
        break;
      case AbstractInsnNode.LABEL:
        build((LabelNode) insn, builder);
        break;
      case AbstractInsnNode.LDC_INSN:
        build((LdcInsnNode) insn, builder);
        break;
      case AbstractInsnNode.IINC_INSN:
        build((IincInsnNode) insn, builder);
        break;
      case AbstractInsnNode.TABLESWITCH_INSN:
        build((TableSwitchInsnNode) insn, builder);
        break;
      case AbstractInsnNode.LOOKUPSWITCH_INSN:
        build((LookupSwitchInsnNode) insn, builder);
        break;
      case AbstractInsnNode.MULTIANEWARRAY_INSN:
        build((MultiANewArrayInsnNode) insn, builder);
        break;
      case AbstractInsnNode.LINE:
        build((LineNumberNode) insn, builder);
        break;
      default:
        throw new Unreachable("Unexpected instruction " + insn);
    }
  }

  private void processLocalVariablesAtExit(AbstractInsnNode insn, IRBuilder builder) {
    assert isReturn(insn) || isThrow(insn);
    // Read all locals live at exit to ensure liveness.
    for (Local local : state.getLocals()) {
      if (local.info != null) {
        builder.addDebugLocalEnd(local.slot.register, local.info);
      }
    }
  }

  private void build(InsnNode insn, IRBuilder builder) {
    int opcode = insn.getOpcode();
    switch (opcode) {
      case Opcodes.NOP:
        // Intentionally left empty.
        break;
      case Opcodes.ACONST_NULL:
        builder.addNullConst(state.push(JarState.NULL_TYPE));
        break;
      case Opcodes.ICONST_M1:
      case Opcodes.ICONST_0:
      case Opcodes.ICONST_1:
      case Opcodes.ICONST_2:
      case Opcodes.ICONST_3:
      case Opcodes.ICONST_4:
      case Opcodes.ICONST_5:
        builder.addIntConst(state.push(Type.INT_TYPE), opcode - Opcodes.ICONST_0);
        break;
      case Opcodes.LCONST_0:
      case Opcodes.LCONST_1:
        builder.addLongConst(state.push(Type.LONG_TYPE), opcode - Opcodes.LCONST_0);
        break;
      case Opcodes.FCONST_0:
      case Opcodes.FCONST_1:
      case Opcodes.FCONST_2:
        builder.addFloatConst(state.push(Type.FLOAT_TYPE),
            Float.floatToRawIntBits(opcode - Opcodes.FCONST_0));
        break;
      case Opcodes.DCONST_0:
      case Opcodes.DCONST_1:
        builder.addDoubleConst(state.push(Type.DOUBLE_TYPE),
            Double.doubleToRawLongBits(opcode - Opcodes.DCONST_0));
        break;
      case Opcodes.IALOAD:
      case Opcodes.LALOAD:
      case Opcodes.FALOAD:
      case Opcodes.DALOAD:
      case Opcodes.AALOAD:
      case Opcodes.BALOAD:
      case Opcodes.CALOAD:
      case Opcodes.SALOAD: {
        Slot index = state.pop(Type.INT_TYPE);
        Slot array = state.pop(JarState.ARRAY_TYPE);
        Type elementType = array.getArrayElementType();
        if (elementType == null) {
          elementType = getArrayElementTypeForOpcode(opcode);
        }
        int dest = state.push(elementType);
        assert isCompatibleArrayElementType(opcode, elementType);
        builder.addArrayGet(memberType(elementType), dest, array.register, index.register);
        break;
      }
      case Opcodes.IASTORE:
      case Opcodes.LASTORE:
      case Opcodes.FASTORE:
      case Opcodes.DASTORE:
      case Opcodes.AASTORE:
      case Opcodes.BASTORE:
      case Opcodes.CASTORE:
      case Opcodes.SASTORE: {
        Slot value = state.pop();
        Slot index = state.pop(Type.INT_TYPE);
        Slot array = state.pop(JarState.ARRAY_TYPE);
        Type elementType = array.getArrayElementType();
        if (elementType == null) {
          elementType = getArrayElementTypeForOpcode(opcode);
        }
        assert isCompatibleArrayElementType(opcode, elementType);
        assert isCompatibleArrayElementType(opcode, value.type);
        builder.addArrayPut(
            memberType(elementType), value.register, array.register, index.register);
        break;
      }
      case Opcodes.POP: {
        Slot value = state.pop();
        assert value.isCategory1();
        break;
      }
      case Opcodes.POP2: {
        Slot value = state.pop();
        if (value.isCategory1()) {
          Slot value2 = state.pop();
          assert value2.isCategory1();
        }
        break;
      }
      case Opcodes.DUP: {
        Slot value = state.peek();
        assert value.isCategory1();
        int copy = state.push(value.type);
        builder.addMove(valueType(value.type), copy, value.register);
        break;
      }
      case Opcodes.DUP_X1: {
        // Stack transformation: ..., v2, v1 -> ..., v1, v2, v1
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1() && value2.isCategory1();
        int stack2 = state.push(value1.type);
        int stack1 = state.push(value2.type);
        int stack0 = state.push(value1.type);
        assert value2.register == stack2;
        assert value1.register == stack1;
        // stack0 is new top-of-stack.
        builder.addMove(valueType(value1.type), stack0, stack1);
        builder.addMove(valueType(value2.type), stack1, stack2);
        builder.addMove(valueType(value1.type), stack2, stack0);
        break;
      }
      case Opcodes.DUP_X2: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1();
        if (value2.isCategory1()) {
          Slot value3 = state.pop();
          assert value3.isCategory1();
          // Stack transformation: ..., v3, v2, v1 -> ..., v1, v3, v2, v1
          dupOneBelowTwo(value3, value2, value1, builder);
        } else {
          // Stack transformation: ..., w2, v1 -> ..., v1, w2, v1
          dupOneBelowOne(value2, value1, builder);
        }
        break;
      }
      case Opcodes.DUP2: {
        Slot value1 = state.pop();
        if (value1.isCategory1()) {
          Slot value2 = state.pop();
          // Stack transformation: ..., v2, v1 -> ..., v2, v1, v2, v1
          assert value2.isCategory1();
          state.push(value2.type);
          state.push(value1.type);
          int copy2 = state.push(value2.type);
          int copy1 = state.push(value1.type);
          builder.addMove(valueType(value1.type), copy1, value1.register);
          builder.addMove(valueType(value2.type), copy2, value2.register);
        } else {
          // Stack transformation: ..., w1 -> ..., w1, w1
          state.push(value1.type);
          int copy1 = state.push(value1.type);
          builder.addMove(valueType(value1.type), copy1, value1.register);
        }
        break;
      }
      case Opcodes.DUP2_X1: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value2.isCategory1();
        if (value1.isCategory1()) {
          // Stack transformation: ..., v3, v2, v1 -> v2, v1, v3, v2, v1
          Slot value3 = state.pop();
          assert value3.isCategory1();
          dupTwoBelowOne(value3, value2, value1, builder);
        } else {
          // Stack transformation: ..., v2, w1 -> ..., w1, v2, w1
          dupOneBelowOne(value2, value1, builder);
        }
        break;
      }
      case Opcodes.DUP2_X2: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        if (!value1.isCategory1() && !value2.isCategory1()) {
          // State transformation: ..., w2, w1 -> w1, w2, w1
          dupOneBelowOne(value2, value1, builder);
        } else {
          Slot value3 = state.pop();
          if (!value1.isCategory1()) {
            assert value2.isCategory1();
            assert value3.isCategory1();
            // State transformation: ..., v3, v2, w1 -> w1, v3, v2, w1
            dupOneBelowTwo(value3, value2, value1, builder);
          } else if (!value3.isCategory1()) {
            assert value1.isCategory1();
            assert value2.isCategory1();
            // State transformation: ..., w3, v2, v1 -> v2, v1, w3, v2, v1
            dupTwoBelowOne(value3, value2, value1, builder);
          } else {
            Slot value4 = state.pop();
            assert value1.isCategory1();
            assert value2.isCategory1();
            assert value3.isCategory1();
            assert value4.isCategory1();
            // State transformation: ..., v4, v3, v2, v1 -> v2, v1, v4, v3, v2, v1
            dupTwoBelowTwo(value4, value3, value2, value1, builder);
          }
        }
        break;
      }
      case Opcodes.SWAP: {
        Slot value1 = state.pop();
        Slot value2 = state.pop();
        assert value1.isCategory1() && value2.isCategory1();
        state.push(value1.type);
        state.push(value2.type);
        int tmp = state.push(value1.type);
        builder.addMove(valueType(value1.type), tmp, value1.register);
        builder.addMove(valueType(value2.type), value1.register, value2.register);
        builder.addMove(valueType(value1.type), value2.register, tmp);
        state.pop(); // Remove temp.
        break;
      }
      case Opcodes.IADD:
      case Opcodes.LADD:
      case Opcodes.FADD:
      case Opcodes.DADD:
      case Opcodes.ISUB:
      case Opcodes.LSUB:
      case Opcodes.FSUB:
      case Opcodes.DSUB:
      case Opcodes.IMUL:
      case Opcodes.LMUL:
      case Opcodes.FMUL:
      case Opcodes.DMUL:
      case Opcodes.IDIV:
      case Opcodes.LDIV:
      case Opcodes.FDIV:
      case Opcodes.DDIV:
      case Opcodes.IREM:
      case Opcodes.LREM:
      case Opcodes.FREM:
      case Opcodes.DREM: {
        Type type = opType(opcode);
        NumericType numericType = numericType(type);
        int right = state.pop(type).register;
        int left = state.pop(type).register;
        int dest = state.push(type);
        if (opcode <= Opcodes.DADD) {
          builder.addAdd(numericType, dest, left, right);
        } else if (opcode <= Opcodes.DSUB) {
          builder.addSub(numericType, dest, left, right);
        } else if (opcode <= Opcodes.DMUL) {
          builder.addMul(numericType, dest, left, right);
        } else if (opcode <= Opcodes.DDIV) {
          builder.addDiv(numericType, dest, left, right);
        } else {
          assert Opcodes.IREM <= opcode && opcode <= Opcodes.DREM;
          builder.addRem(numericType, dest, left, right);
        }
        break;
      }
      case Opcodes.INEG:
      case Opcodes.LNEG:
      case Opcodes.FNEG:
      case Opcodes.DNEG: {
        Type type = opType(opcode);
        NumericType numericType = numericType(type);
        int value = state.pop(type).register;
        int dest = state.push(type);
        builder.addNeg(numericType, dest, value);
        break;
      }
      case Opcodes.ISHL:
      case Opcodes.LSHL:
      case Opcodes.ISHR:
      case Opcodes.LSHR:
      case Opcodes.IUSHR:
      case Opcodes.LUSHR: {
        Type type = opType(opcode);
        NumericType numericType = numericType(type);
        int right = state.pop(Type.INT_TYPE).register;
        int left = state.pop(type).register;
        int dest = state.push(type);
        if (opcode <= Opcodes.LSHL) {
          builder.addShl(numericType, dest, left, right);
        } else if (opcode <= Opcodes.LSHR) {
          builder.addShr(numericType, dest, left, right);
        } else {
          assert opcode == Opcodes.IUSHR || opcode == Opcodes.LUSHR;
          builder.addUshr(numericType, dest, left, right);
        }
        break;
      }
      case Opcodes.IAND:
      case Opcodes.LAND: {
        Type type = opcode == Opcodes.IAND ? Type.INT_TYPE : Type.LONG_TYPE;
        int right = state.pop(type).register;
        int left = state.pop(type).register;
        int dest = state.push(type);
        builder.addAnd(numericType(type), dest, left, right);
        break;
      }
      case Opcodes.IOR:
      case Opcodes.LOR: {
        Type type = opcode == Opcodes.IOR ? Type.INT_TYPE : Type.LONG_TYPE;
        int right = state.pop(type).register;
        int left = state.pop(type).register;
        int dest = state.push(type);
        builder.addOr(numericType(type), dest, left, right);
        break;
      }
      case Opcodes.IXOR:
      case Opcodes.LXOR: {
        Type type = opcode == Opcodes.IXOR ? Type.INT_TYPE : Type.LONG_TYPE;
        int right = state.pop(type).register;
        int left = state.pop(type).register;
        int dest = state.push(type);
        builder.addXor(numericType(type), dest, left, right);
        break;
      }
      case Opcodes.I2L:
        buildConversion(Type.INT_TYPE, Type.LONG_TYPE, builder);
        break;
      case Opcodes.I2F:
        buildConversion(Type.INT_TYPE, Type.FLOAT_TYPE, builder);
        break;
      case Opcodes.I2D:
        buildConversion(Type.INT_TYPE, Type.DOUBLE_TYPE, builder);
        break;
      case Opcodes.L2I:
        buildConversion(Type.LONG_TYPE, Type.INT_TYPE, builder);
        break;
      case Opcodes.L2F:
        buildConversion(Type.LONG_TYPE, Type.FLOAT_TYPE, builder);
        break;
      case Opcodes.L2D:
        buildConversion(Type.LONG_TYPE, Type.DOUBLE_TYPE, builder);
        break;
      case Opcodes.F2I:
        buildConversion(Type.FLOAT_TYPE, Type.INT_TYPE, builder);
        break;
      case Opcodes.F2L:
        buildConversion(Type.FLOAT_TYPE, Type.LONG_TYPE, builder);
        break;
      case Opcodes.F2D:
        buildConversion(Type.FLOAT_TYPE, Type.DOUBLE_TYPE, builder);
        break;
      case Opcodes.D2I:
        buildConversion(Type.DOUBLE_TYPE, Type.INT_TYPE, builder);
        break;
      case Opcodes.D2L:
        buildConversion(Type.DOUBLE_TYPE, Type.LONG_TYPE, builder);
        break;
      case Opcodes.D2F:
        buildConversion(Type.DOUBLE_TYPE, Type.FLOAT_TYPE, builder);
        break;
      case Opcodes.I2B:
        buildConversion(Type.INT_TYPE, Type.BYTE_TYPE, builder);
        break;
      case Opcodes.I2C:
        buildConversion(Type.INT_TYPE, Type.CHAR_TYPE, builder);
        break;
      case Opcodes.I2S:
        buildConversion(Type.INT_TYPE, Type.SHORT_TYPE, builder);
        break;
      case Opcodes.LCMP: {
        Slot right = state.pop(Type.LONG_TYPE);
        Slot left = state.pop(Type.LONG_TYPE);
        int dest = state.push(Type.INT_TYPE);
        builder.addCmp(NumericType.LONG, Bias.NONE, dest, left.register, right.register);
        break;
      }
      case Opcodes.FCMPL:
      case Opcodes.FCMPG: {
        Slot right = state.pop(Type.FLOAT_TYPE);
        Slot left = state.pop(Type.FLOAT_TYPE);
        int dest = state.push(Type.INT_TYPE);
        Bias bias = opcode == Opcodes.FCMPL ? Bias.LT : Bias.GT;
        builder.addCmp(NumericType.FLOAT, bias, dest, left.register, right.register);
        break;
      }
      case Opcodes.DCMPL:
      case Opcodes.DCMPG: {
        Slot right = state.pop(Type.DOUBLE_TYPE);
        Slot left = state.pop(Type.DOUBLE_TYPE);
        int dest = state.push(Type.INT_TYPE);
        Bias bias = opcode == Opcodes.DCMPL ? Bias.LT : Bias.GT;
        builder.addCmp(NumericType.DOUBLE, bias, dest, left.register, right.register);
        break;
      }
      case Opcodes.IRETURN: {
        Slot value = state.pop(Type.INT_TYPE);
        addReturn(insn, ValueType.INT, value.register, builder);
        break;
      }
      case Opcodes.LRETURN: {
        Slot value = state.pop(Type.LONG_TYPE);
        addReturn(insn, ValueType.LONG, value.register, builder);
        break;
      }
      case Opcodes.FRETURN: {
        Slot value = state.pop(Type.FLOAT_TYPE);
        addReturn(insn, ValueType.FLOAT, value.register, builder);
        break;
      }
      case Opcodes.DRETURN: {
        Slot value = state.pop(Type.DOUBLE_TYPE);
        addReturn(insn, ValueType.DOUBLE, value.register, builder);
        break;
      }
      case Opcodes.ARETURN: {
        Slot obj = state.pop(JarState.REFERENCE_TYPE);
        addReturn(insn, ValueType.OBJECT, obj.register, builder);
        break;
      }
      case Opcodes.RETURN: {
        addReturn(insn, null, -1, builder);
        break;
      }
      case Opcodes.ARRAYLENGTH: {
        Slot array = state.pop(JarState.ARRAY_TYPE);
        int dest = state.push(Type.INT_TYPE);
        builder.addArrayLength(dest, array.register);
        break;
      }
      case Opcodes.ATHROW: {
        Slot object = state.pop(JarState.OBJECT_TYPE);
        addThrow(insn, object.register, builder);
        break;
      }
      case Opcodes.MONITORENTER: {
        Slot object = state.pop(JarState.REFERENCE_TYPE);
        builder.addMonitor(Monitor.Type.ENTER, object.register);
        break;
      }
      case Opcodes.MONITOREXIT: {
        Slot object = state.pop(JarState.REFERENCE_TYPE);
        builder.addMonitor(Monitor.Type.EXIT, object.register);
        break;
      }
      default:
        throw new Unreachable("Unexpected Insn opcode: " + insn.getOpcode());
    }
  }

  private boolean isExitingThrow(InsnNode insn) {
    List<TryCatchBlock> handlers = getTryHandlers(insn);
    if (handlers.isEmpty()) {
      return true;
    }
    if (!generateMethodSynchronization() || handlers.size() > 1) {
      return false;
    }
    return handlers.get(0) == EXCEPTIONAL_SYNC_EXIT;
  }

  private void addThrow(InsnNode insn, int register, IRBuilder builder) {
    if (isExitingThrow(insn)) {
      processLocalVariablesAtExit(insn, builder);
    } else {
      int offset = getOffset(insn);
      Int2ReferenceSortedMap<BlockInfo> cfg = builder.getCFG();
      BlockInfo info = cfg.get(cfg.headMap(offset + 1).lastIntKey());
      assert info.normalSuccessors.isEmpty();
      assert !info.exceptionalSuccessors.isEmpty();
      Int2ReferenceMap<DebugLocalInfo> ending = new Int2ReferenceOpenHashMap<>();
      for (int successorOffset : info.exceptionalSuccessors) {
        if (successorOffset == EXCEPTIONAL_SYNC_EXIT_OFFSET) {
          // TODO(zerny): It would likely be beneficial to keep locals live until the exit from the
          // exceptional sync exit block.
          continue;
        }
        LocalChangeAtOffset localChange = state.getLocalChange(offset, successorOffset);
        for (Local localEnd : localChange.getLocalsToClose()) {
          ending.put(localEnd.slot.register, localEnd.info);
        }
      }
      ending.forEach(builder::addDebugLocalEnd);
    }
    builder.addThrow(register);
  }

  private void addReturn(InsnNode insn, ValueType type, int register, IRBuilder builder) {
    processLocalVariablesAtExit(insn, builder);
    if (type == null) {
      assert register == -1;
      builder.addReturn();
    } else {
      builder.addReturn(type, register);
    }
  }

  private void dupOneBelowTwo(Slot value3, Slot value2, Slot value1, IRBuilder builder) {
    int stack3 = state.push(value1.type);
    int stack2 = state.push(value3.type);
    int stack1 = state.push(value2.type);
    int stack0 = state.push(value1.type);
    assert value3.register == stack3;
    assert value2.register == stack2;
    assert value1.register == stack1;
    builder.addMove(valueType(value1.type), stack0, stack1);
    builder.addMove(valueType(value2.type), stack1, stack2);
    builder.addMove(valueType(value3.type), stack2, stack3);
    builder.addMove(valueType(value1.type), stack3, stack0);
  }

  private void dupOneBelowOne(Slot value2, Slot value1, IRBuilder builder) {
    int stack2 = state.push(value1.type);
    int stack1 = state.push(value2.type);
    int stack0 = state.push(value1.type);
    assert value2.register == stack2;
    assert value1.register == stack1;
    builder.addMove(valueType(value1.type), stack0, stack1);
    builder.addMove(valueType(value2.type), stack1, stack2);
    builder.addMove(valueType(value1.type), stack2, stack0);
  }

  private void dupTwoBelowOne(Slot value3, Slot value2, Slot value1, IRBuilder builder) {
    int stack4 = state.push(value2.type);
    int stack3 = state.push(value1.type);
    int stack2 = state.push(value3.type);
    int stack1 = state.push(value2.type);
    int stack0 = state.push(value1.type);
    assert value3.register == stack4;
    assert value2.register == stack3;
    assert value1.register == stack2;
    builder.addMove(valueType(value1.type), stack0, stack2);
    builder.addMove(valueType(value2.type), stack1, stack3);
    builder.addMove(valueType(value3.type), stack2, stack4);
    builder.addMove(valueType(value1.type), stack3, stack0);
    builder.addMove(valueType(value2.type), stack4, stack1);
  }

  private void dupTwoBelowTwo(Slot value4, Slot value3, Slot value2, Slot value1,
      IRBuilder builder) {
    int stack5 = state.push(value2.type);
    int stack4 = state.push(value1.type);
    int stack3 = state.push(value4.type);
    int stack2 = state.push(value3.type);
    int stack1 = state.push(value2.type);
    int stack0 = state.push(value1.type);
    assert value4.register == stack5;
    assert value3.register == stack4;
    assert value2.register == stack3;
    assert value1.register == stack2;
    builder.addMove(valueType(value1.type), stack0, stack2);
    builder.addMove(valueType(value2.type), stack1, stack3);
    builder.addMove(valueType(value3.type), stack2, stack4);
    builder.addMove(valueType(value4.type), stack3, stack5);
    builder.addMove(valueType(value1.type), stack4, stack0);
    builder.addMove(valueType(value2.type), stack5, stack1);
  }

  private void buildConversion(Type from, Type to, IRBuilder builder) {
    int source = state.pop(from).register;
    int dest = state.push(to);
    builder.addConversion(numericType(to), numericType(from), dest, source);
  }

  private void build(IntInsnNode insn, IRBuilder builder) {
    switch (insn.getOpcode()) {
      case Opcodes.BIPUSH:
      case Opcodes.SIPUSH: {
        int dest = state.push(Type.INT_TYPE);
        builder.addIntConst(dest, insn.operand);
        break;
      }
      case Opcodes.NEWARRAY: {
        String desc = arrayTypeDesc(insn.operand);
        Type type = application.getAsmType(desc);
        DexType dexType = application.getTypeFromDescriptor(desc);
        int count = state.pop(Type.INT_TYPE).register;
        int array = state.push(type);
        builder.addNewArrayEmpty(array, count, dexType);
        break;
      }
      default:
        throw new Unreachable("Unexpected IntInsn opcode: " + insn.getOpcode());
    }
  }

  private void build(VarInsnNode insn, IRBuilder builder) {
    int opcode = insn.getOpcode();
    Type expectedType;
    switch (opcode) {
      case Opcodes.ILOAD:
      case Opcodes.ISTORE:
        expectedType = Type.INT_TYPE;
        break;
      case Opcodes.FLOAD:
      case Opcodes.FSTORE:
        expectedType = Type.FLOAT_TYPE;
        break;
      case Opcodes.LLOAD:
      case Opcodes.LSTORE:
        expectedType = Type.LONG_TYPE;
        break;
      case Opcodes.DLOAD:
      case Opcodes.DSTORE:
        expectedType = Type.DOUBLE_TYPE;
        break;
      case Opcodes.ALOAD:
      case Opcodes.ASTORE:
        expectedType = JarState.REFERENCE_TYPE;
        break;
      case Opcodes.RET: {
        throw new Unreachable("RET should be handled by the ASM jsr inliner");
      }
      default:
        throw new Unreachable("Unexpected VarInsn opcode: " + insn.getOpcode());
    }
    if (Opcodes.ILOAD <= opcode && opcode <= Opcodes.ALOAD) {
      Slot src = state.readLocal(insn.var, expectedType);
      int dest = state.push(src.type);
      builder.addMove(valueType(src.type), dest, src.register);
    } else {
      assert Opcodes.ISTORE <= opcode && opcode <= Opcodes.ASTORE;
      Slot src = state.pop(expectedType);
      int dest = state.getLocalRegister(insn.var, src.type);
      builder.addMove(valueType(src.type), dest, src.register);
      state.writeLocal(insn.var, src.type);
    }
  }

  private void build(TypeInsnNode insn, IRBuilder builder) {
    Type type = application.getAsmObjectType(insn.desc);
    switch (insn.getOpcode()) {
      case Opcodes.NEW: {
        DexType dexType = application.getTypeFromName(insn.desc);
        int dest = state.push(type);
        builder.addNewInstance(dest, dexType);
        break;
      }
      case Opcodes.ANEWARRAY: {
        Type arrayType = makeArrayType(type);
        DexType dexArrayType = application.getTypeFromDescriptor(arrayType.getDescriptor());
        int count = state.pop(Type.INT_TYPE).register;
        int dest = state.push(arrayType);
        builder.addNewArrayEmpty(dest, count, dexArrayType);
        break;
      }
      case Opcodes.CHECKCAST: {
        DexType dexType = application.getTypeFromDescriptor(type.getDescriptor());
        // Pop the top value and push it back on with the checked type.
        state.pop(type);
        int object = state.push(type);
        builder.addCheckCast(object, dexType);
        break;
      }
      case Opcodes.INSTANCEOF: {
        int obj = state.pop(JarState.REFERENCE_TYPE).register;
        int dest = state.push(Type.INT_TYPE);
        DexType dexType = application.getTypeFromDescriptor(type.getDescriptor());
        builder.addInstanceOf(dest, obj, dexType);
        break;
      }
      default:
        throw new Unreachable("Unexpected TypeInsn opcode: " + insn.getOpcode());
    }
  }

  private void build(FieldInsnNode insn, IRBuilder builder) {
    DexField field = application.getField(insn.owner, insn.name, insn.desc);
    Type type = application.getAsmType(insn.desc);
    switch (insn.getOpcode()) {
      case Opcodes.GETSTATIC:
        builder.addStaticGet(state.push(type), field);
        break;
      case Opcodes.PUTSTATIC:
        builder.addStaticPut(state.pop(type).register, field);
        break;
      case Opcodes.GETFIELD: {
        Slot object = state.pop(JarState.OBJECT_TYPE);
        int dest = state.push(type);
        builder.addInstanceGet(dest, object.register, field);
        break;
      }
      case Opcodes.PUTFIELD: {
        Slot value = state.pop(type);
        Slot object = state.pop(JarState.OBJECT_TYPE);
        builder.addInstancePut(value.register, object.register, field);
        break;
      }
      default:
        throw new Unreachable("Unexpected FieldInsn opcode: " + insn.getOpcode());
    }
  }

  private void build(MethodInsnNode insn, IRBuilder builder) {
    // Resolve the target method of the invoke.
    DexMethod method = application.getMethod(insn.owner, insn.name, insn.desc);

    buildInvoke(
        insn.desc,
        application.getAsmObjectType(insn.owner),
        insn.getOpcode() != Opcodes.INVOKESTATIC,
        builder,
        (types, registers) -> {
          Invoke.Type invokeType = invokeType(insn);
          DexProto callSiteProto = null;
          DexMethod targetMethod = method;
          if (invokeType == Invoke.Type.POLYMORPHIC) {
            if (insn.owner.equals(INTERNAL_NAME_METHOD_HANDLE)) {
              targetMethod = application
                  .getMethod(insn.owner, insn.name, POLYMORPHIC_DEFAULT_SIGNATURE_DESC);
            } else if (insn.owner.equals(INTERNAL_NAME_VAR_HANDLE)) {
              switch (insn.name) {
                case "compareAndExchange":
                case "compareAndExchangeAcquire":
                case "compareAndExchangeRelease":
                case "get":
                case "getAcquire":
                case "getAndAdd":
                case "getAndAddAcquire":
                case "getAndAddRelease":
                case "getAndBitwiseAnd":
                case "getAndBitwiseAndAcquire":
                case "getAndBitwiseAndRelease":
                case "getAndBitwiseOr":
                case "getAndBitwiseOrAcquire":
                case "getAndBitwiseOrRelease":
                case "getAndBitwiseXor":
                case "getAndBitwiseXorAcquire":
                case "getAndBitwiseXorRelease":
                case "getAndSet":
                case "getAndSetAcquire":
                case "getAndSetRelease":
                case "getOpaque":
                case "getVolatile": {
                  targetMethod = application
                      .getMethod(insn.owner, insn.name, POLYMORPHIC_DEFAULT_SIGNATURE_DESC);
                  break;
                }
                case "set":
                case "setOpaque":
                case "setRelease":
                case "setVolatile": {
                  targetMethod = application
                      .getMethod(insn.owner, insn.name, POLYMORPHIC_VARHANDLE_SET_SIGNATURE_DESC);
                  break;
                }
                case "compareAndSet":
                case "weakCompareAndSet":
                case "weakCompareAndSetAcquire":
                case "weakCompareAndSetPlain":
                case "weakCompareAndSetRelease": {
                  targetMethod = application.getMethod(insn.owner, insn.name,
                      POLYMORPHIC_VARHANDLE_COMPARE_AND_SET_SIGNATURE_DESC);
                  break;
                }
                default:
                  throw new Unreachable();
              }
            } else {
              throw new Unreachable();
            }
            callSiteProto = application.getProto(insn.desc);
          }
          builder.addInvoke(invokeType, targetMethod, callSiteProto, types, registers, insn.itf);
        });
  }

  private void buildInvoke(
      String methodDesc,
      Type methodOwner,
      boolean addImplicitReceiver,
      IRBuilder builder,
      BiConsumer<List<ValueType>, List<Integer>> creator) {

    // Build the argument list of the form [owner, param1, ..., paramN].
    // The arguments are in reverse order on the stack, so we pop off the parameters here.
    Type[] parameterTypes = application.getArgumentTypes(methodDesc);
    Slot[] parameterRegisters = state.popReverse(parameterTypes.length);

    List<ValueType> types = new ArrayList<>(parameterTypes.length + 1);
    List<Integer> registers = new ArrayList<>(parameterTypes.length + 1);

    // Add receiver argument for non-static calls.
    if (addImplicitReceiver) {
      addArgument(types, registers, methodOwner, state.pop());
    }

    // The remaining arguments are the parameters of the method.
    for (int i = 0; i < parameterTypes.length; i++) {
      addArgument(types, registers, parameterTypes[i], parameterRegisters[i]);
    }

    // Create the invoke.
    creator.accept(types, registers);

    // Move the result to the "top of stack".
    Type returnType = application.getReturnType(methodDesc);
    if (returnType != Type.VOID_TYPE) {
      builder.addMoveResult(state.push(returnType));
    }
  }

  private static void addArgument(List<ValueType> types, List<Integer> registers, Type type,
      Slot slot) {
    assert slot.isCompatibleWith(type);
    types.add(valueType(type));
    registers.add(slot.register);
  }

  private void build(InvokeDynamicInsnNode insn, IRBuilder builder) {
    DexCallSite callSite = DexCallSite.fromAsmInvokeDynamic(insn, application, clazz);

    buildInvoke(insn.desc, null /* Not needed */,
        false /* Receiver is passed explicitly */, builder,
        (types, registers) -> builder.addInvokeCustom(callSite, types, registers));
  }

  private void build(JumpInsnNode insn, IRBuilder builder) {
    int[] targets = getTargets(insn);
    int opcode = insn.getOpcode();
    if (Opcodes.IFEQ <= opcode && opcode <= Opcodes.IF_ACMPNE) {
      assert targets.length == 2;
      if (opcode <= Opcodes.IFLE) {
        Slot value = state.pop(Type.INT_TYPE);
        builder.addIfZero(ifType(opcode), ValueType.INT, value.register, targets[0], targets[1]);
      } else {
        ValueType valueType;
        Type expectedType;
        if (opcode < Opcodes.IF_ACMPEQ) {
          valueType = ValueType.INT;
          expectedType = Type.INT_TYPE;
        } else {
          valueType = ValueType.OBJECT;
          expectedType = JarState.REFERENCE_TYPE;
        }
        Slot value2 = state.pop(expectedType);
        Slot value1 = state.pop(expectedType);
        builder.addIf(
            ifType(opcode), valueType, value1.register, value2.register, targets[0], targets[1]);
      }
    } else {
      switch (opcode) {
        case Opcodes.GOTO: {
          assert targets.length == 1;
          builder.addGoto(targets[0]);
          break;
        }
        case Opcodes.IFNULL:
        case Opcodes.IFNONNULL: {
          Slot value = state.pop(JarState.REFERENCE_TYPE);
          If.Type type = opcode == Opcodes.IFNULL ? If.Type.EQ : If.Type.NE;
          builder.addIfZero(type, ValueType.OBJECT, value.register, targets[0], targets[1]);
          break;
        }
        case Opcodes.JSR: {
          throw new Unreachable("JSR should be handled by the ASM jsr inliner");
        }
        default:
          throw new Unreachable("Unexpected JumpInsn opcode: " + insn.getOpcode());
      }
    }
  }

  private void build(LabelNode insn, IRBuilder builder) {
    // Intentionally empty.
  }

  private void build(LdcInsnNode insn, IRBuilder builder) {
    if (insn.cst instanceof Type) {
      Type type = (Type) insn.cst;
      if (type.getSort() == Type.METHOD) {
        int dest = state.push(METHOD_TYPE_TYPE);
        builder.addConstMethodType(dest, application.getProto(type.getDescriptor()));
      } else {
        int dest = state.push(type);
        builder.addConstClass(dest, application.getTypeFromDescriptor(type.getDescriptor()));
      }
    } else if (insn.cst instanceof String) {
      int dest = state.push(STRING_TYPE);
      builder.addConstString(dest, application.getString((String) insn.cst));
    } else if (insn.cst instanceof Long) {
      int dest = state.push(Type.LONG_TYPE);
      builder.addLongConst(dest, (Long) insn.cst);
    } else if (insn.cst instanceof Double) {
      int dest = state.push(Type.DOUBLE_TYPE);
      builder.addDoubleConst(dest, Double.doubleToRawLongBits((Double) insn.cst));
    } else if (insn.cst instanceof Integer) {
      int dest = state.push(Type.INT_TYPE);
      builder.addIntConst(dest, (Integer) insn.cst);
    } else if (insn.cst instanceof Float) {
      int dest = state.push(Type.FLOAT_TYPE);
      builder.addFloatConst(dest, Float.floatToRawIntBits((Float) insn.cst));
    } else if (insn.cst instanceof Handle) {
      Handle handle = (Handle) insn.cst;
      int dest = state.push(METHOD_HANDLE_TYPE);
      builder.addConstMethodHandle(dest, DexMethodHandle.fromAsmHandle(handle, application, clazz));
    } else {
      throw new CompilationError("Unsupported constant: " + insn.cst.toString());
    }
  }

  private void build(IincInsnNode insn, IRBuilder builder) {
    int local = state.readLocal(insn.var, Type.INT_TYPE).register;
    builder.addAddLiteral(NumericType.INT, local, local, insn.incr);
  }

  private void build(TableSwitchInsnNode insn, IRBuilder builder) {
    buildSwitch(insn.dflt, insn.labels, new int[]{insn.min}, builder);
  }

  private void build(LookupSwitchInsnNode insn, IRBuilder builder) {
    int[] keys = new int[insn.keys.size()];
    for (int i = 0; i < insn.keys.size(); i++) {
      keys[i] = (int) insn.keys.get(i);
    }
    buildSwitch(insn.dflt, insn.labels, keys, builder);
  }

  private void buildSwitch(LabelNode dflt, List labels, int[] keys,
      IRBuilder builder) {
    int index = state.pop(Type.INT_TYPE).register;
    int fallthroughOffset = getOffset(dflt);
    int[] labelOffsets = new int[labels.size()];
    for (int i = 0; i < labels.size(); i++) {
      int offset = getOffset((LabelNode) labels.get(i));
      labelOffsets[i] = offset;
    }
    builder.addSwitch(index, keys, fallthroughOffset, labelOffsets);
  }

  private void build(MultiANewArrayInsnNode insn, IRBuilder builder) {
    // Type of the full array.
    Type arrayType = application.getAsmObjectType(insn.desc);
    DexType dexArrayType = application.getType(arrayType);
    Slot[] slots = state.popReverse(insn.dims, Type.INT_TYPE);
    int[] dimensions = new int[insn.dims];
    for (int i = 0; i < insn.dims; i++) {
      dimensions[i] = slots[i].register;
    }
    if (builder.isGeneratingClassFiles()) {
      int result = state.push(arrayType);
      builder.addMultiNewArray(dexArrayType, result, dimensions);
      return;
    }
    // Type of the members. Can itself be of array type, eg, 'int[]' for 'new int[x][y][]'
    // Note that this is not the same as 'arrayType.toBaseType' as is the case in the above 'int[]'.
    DexType memberType = application.getTypeFromDescriptor(insn.desc.substring(insn.dims));
    // Push an array containing the dimensions of the desired multi-dimensional array.
    DexType dimArrayType = application.getTypeFromDescriptor(INT_ARRAY_DESC);
    builder.addInvokeNewArray(dimArrayType, insn.dims, dimensions);
    int dimensionsDestTemp = state.push(INT_ARRAY_TYPE);
    builder.addMoveResult(dimensionsDestTemp);
    // Push the class object for the member type of the array.
    int classDestTemp = state.push(CLASS_TYPE);
    builder.ensureBlockForThrowingInstruction();
    builder.addConstClass(classDestTemp, memberType);
    // Create the actual multi-dimensional array using java.lang.reflect.Array::newInstance
    DexType reflectArrayClass = application.getTypeFromDescriptor(REFLECT_ARRAY_DESC);
    DexMethod newInstance = application.getMethod(reflectArrayClass,
        REFLECT_ARRAY_NEW_INSTANCE_NAME, REFLECT_ARRAY_NEW_INSTANCE_DESC);
    List<ValueType> argumentTypes = Arrays.asList(valueType(CLASS_TYPE), valueType(INT_ARRAY_TYPE));
    List<Integer> argumentRegisters = Arrays.asList(classDestTemp, dimensionsDestTemp);
    builder.ensureBlockForThrowingInstruction();
    builder.addInvoke(Invoke.Type.STATIC, newInstance, null, argumentTypes, argumentRegisters);
    // Pop the temporaries and push the final result.
    state.pop(); // classDestTemp.
    state.pop(); // dimensionsDestTemp.
    int result = state.push(arrayType);
    builder.addMoveResult(result);
    // Insert cast check to satisfy verification.
    builder.ensureBlockForThrowingInstruction();
    builder.addCheckCast(result, dexArrayType);
  }

  private void build(LineNumberNode insn, IRBuilder builder) {
    currentPosition = getCanonicalPosition(insn.line);
    builder.addDebugPosition(currentPosition);
  }

  @Override
  public Position getCanonicalDebugPositionAtOffset(int offset) {
    if (offset == EXCEPTIONAL_SYNC_EXIT_OFFSET) {
      return getExceptionalExitPosition();
    }
    int index = instructionIndex(offset);
    if (index < 0 || instructionCount() <= index) {
      assert false;
      return getPreamblePosition();
    }
    AbstractInsnNode insn = node.instructions.get(index);
    if (insn instanceof LabelNode) {
      insn = insn.getNext();
    }
    while (insn != null && !(insn instanceof LineNumberNode)) {
      insn = insn.getPrevious();
    }
    if (insn != null) {
      LineNumberNode line = (LineNumberNode) insn;
      return getCanonicalPosition(line.line);
    }
    return getPreamblePosition();
  }

  @Override
  public Position getCurrentPosition() {
    return currentPosition;
  }

  private Position getCanonicalPosition(int line) {
    return canonicalPositions.computeIfAbsent(
        line, l -> new Position(l, null, originalMethod, callerPosition));
  }

  private Position getPreamblePosition() {
    if (preamblePosition == null) {
      preamblePosition = Position.synthetic(0, originalMethod, null);
    }
    return preamblePosition;
  }

  // If we need to emit a synthetic position for exceptional monitor exits, we try to cook up a
  // position that is not actually a valid program position, so as not to incorrectly position the
  // user on an exit that is not the actual exit being taken. Our heuristic for this is that if the
  // method has at least two positions we use the first position minus one as the synthetic exit.
  // If the method only has one position it is safe to just use that position.
  private Position getExceptionalExitPosition() {
    if (syntheticPosition == null) {
      if (debug) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (Iterator it = node.instructions.iterator(); it.hasNext(); ) {
          Object insn = it.next();
          if (insn instanceof LineNumberNode) {
            LineNumberNode lineNode = (LineNumberNode) insn;
            min = Math.min(min, lineNode.line);
            max = Math.max(max, lineNode.line);
          }
        }
        syntheticPosition =
            (min == Integer.MAX_VALUE)
                ? getPreamblePosition()
                : Position.synthetic(min < max ? min - 1 : min, originalMethod, callerPosition);
      } else {
        // If in release mode we explicitly associate a synthetic none position with monitor exit.
        // This is safe as the runtime must never throw at this position because the monitor cannot
        // be null and the thread calling exit can only be the same thread that entered the monitor
        // at method entry.
        syntheticPosition = Position.syntheticNone();
      }
    }
    return syntheticPosition;
  }

  // Printing helpers.

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("node.name = [").append(node.name).append("]");
    builder.append("\n");
    builder.append("node.desc = ").append(node.desc);
    builder.append("\n");
    builder.append("node.maxStack = ").append(node.maxStack);
    builder.append("\n");
    builder.append("node.maxLocals = ").append(node.maxLocals);
    builder.append("\n");
    builder.append("node.locals.size = ").append(node.localVariables.size());
    builder.append("\n");
    builder.append("node.insns.size = ").append(node.instructions.size());
    builder.append("\n");
    for (int i = 0; i < parameterTypes.size(); i++) {
      builder.append("arg ").append(i).append(", type: ").append(parameterTypes.get(i))
          .append("\n");
    }
    for (int i = 0; i < node.localVariables.size(); i++) {
      LocalVariableNode local = (LocalVariableNode) node.localVariables.get(i);
      builder.append("local ").append(i)
          .append(", name: ").append(local.name)
          .append(", desc: ").append(local.desc)
          .append(", index: ").append(local.index)
          .append(", [").append(getOffset(local.start))
          .append("..").append(getOffset(local.end))
          .append("[\n");
    }
    for (int i = 0; i < node.tryCatchBlocks.size(); i++) {
      TryCatchBlockNode tryCatchBlockNode = (TryCatchBlockNode) node.tryCatchBlocks.get(i);
      builder.append("[").append(getOffset(tryCatchBlockNode.start))
          .append("..").append(getOffset(tryCatchBlockNode.end)).append("[ ")
          .append(tryCatchBlockNode.type).append(" -> ")
          .append(getOffset(tryCatchBlockNode.handler))
          .append("\n");
    }
    for (int i = 0; i < node.instructions.size(); i++) {
      AbstractInsnNode insn = node.instructions.get(i);
      builder.append(String.format("%4d: ", i)).append(instructionToString(insn));
    }
    return builder.toString();
  }

  private String instructionToString(AbstractInsnNode insn) {
    if (printVisitor == null) {
      printVisitor = new TraceMethodVisitor(new Textifier());
    }
    insn.accept(printVisitor);
    StringWriter writer = new StringWriter();
    printVisitor.p.print(new PrintWriter(writer));
    printVisitor.p.getText().clear();
    return writer.toString();
  }

  public static boolean isCallToPolymorphicSignatureMethod(String owner, String name) {
    if (owner.equals("java/lang/invoke/MethodHandle")) {
      switch (name) {
        case "invoke":
        case "invokeExact":
          return true;
        default :
          return false;
      }
    } else if (owner.equals("java/lang/invoke/VarHandle")) {
      switch (name) {
        case "compareAndExchange":
        case "compareAndExchangeAcquire":
        case "compareAndExchangeRelease":
        case "compareAndSet":
        case "get":
        case "getAcquire":
        case "getAndAdd":
        case "getAndAddAcquire":
        case "getAndAddRelease":
        case "getAndBitwiseAnd":
        case "getAndBitwiseAndAcquire":
        case "getAndBitwiseAndRelease":
        case "getAndBitwiseOr":
        case "getAndBitwiseOrAcquire":
        case "getAndBitwiseOrRelease":
        case "getAndBitwiseXor":
        case "getAndBitwiseXorAcquire":
        case "getAndBitwiseXorRelease":
        case "getAndSet":
        case "getAndSetAcquire":
        case "getAndSetRelease":
        case "getOpaque":
        case "getVolatile":
        case "set":
        case "setOpaque":
        case "setRelease":
        case "setVolatile":
        case "weakCompareAndSet":
        case "weakCompareAndSetAcquire":
        case "weakCompareAndSetPlain":
        case "weakCompareAndSetRelease":
          return true;
        default :
          return false;
      }
    }
    return false;
  }
}
