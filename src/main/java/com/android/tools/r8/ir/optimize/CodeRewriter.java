// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CatchHandlers.CatchHandler;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugLocalWrite;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstanceFieldInstruction;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LazyBox;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.Long2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class CodeRewriter {

  // This constant was determined by experimentation.
  private static final int STOP_SHARED_CONSTANT_THRESHOLD = 50;

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public CodeRewriter(AppView<?> appView) {
    this.appView = appView;
    this.options = appView.options();
    this.dexItemFactory = appView.dexItemFactory();
  }

  public static void removeAssumeInstructions(AppView<?> appView, IRCode code) {
    // We need to update the types of all values whose definitions depend on a non-null value.
    // This is needed to preserve soundness of the types after the Assume instructions have been
    // removed.
    //
    // As an example, consider a check-cast instruction on the form "z = (T) y". If y used to be
    // defined by a NonNull instruction, then the type analysis could have used this information
    // to mark z as non-null. However, cleanupNonNull() have now replaced y by a nullable value x.
    // Since z is defined as "z = (T) x", and x is nullable, it is no longer sound to have that z
    // is not nullable. This is fixed by rerunning the type analysis for the affected values.
    Set<Value> valuesThatRequireWidening = Sets.newIdentityHashSet();

    InstructionListIterator it = code.instructionListIterator();
    boolean needToCheckTrivialPhis = false;
    while (it.hasNext()) {
      Instruction instruction = it.next();

      // The following deletes Assume instructions and replaces any specialized value by its
      // original value:
      //   y <- Assume(x)
      //   ...
      //   y.foo()
      //
      // becomes:
      //
      //   x.foo()
      if (instruction.isAssume()) {
        Assume assumeInstruction = instruction.asAssume();
        Value src = assumeInstruction.src();
        Value dest = assumeInstruction.outValue();

        valuesThatRequireWidening.addAll(dest.affectedValues());

        // Replace `dest` by `src`.
        needToCheckTrivialPhis |= dest.numberOfPhiUsers() > 0;
        dest.replaceUsers(src);
        it.remove();
      }
    }

    // Assume insertion may introduce phis, e.g.,
    //   y <- Assume(x)
    //   ...
    //   z <- phi(x, y)
    //
    // Therefore, Assume elimination may result in a trivial phi:
    //   z <- phi(x, x)
    if (needToCheckTrivialPhis) {
      code.removeAllDeadAndTrivialPhis(valuesThatRequireWidening);
    }

    if (!valuesThatRequireWidening.isEmpty()) {
      new TypeAnalysis(appView).widening(valuesThatRequireWidening);
    }

    assert Streams.stream(code.instructions()).noneMatch(Instruction::isAssume);
  }

  // Rewrite 'throw new NullPointerException()' to 'throw null'.
  public void rewriteThrowNullPointerException(IRCode code) {
    boolean shouldRemoveUnreachableBlocks = false;
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();

        // Check for the patterns 'if (x == null) throw null' and
        // 'if (x == null) throw new NullPointerException()'.
        if (instruction.isIf()) {
          if (appView
              .dexItemFactory()
              .objectsMethods
              .isRequireNonNullMethod(code.method().getReference())) {
            continue;
          }

          If ifInstruction = instruction.asIf();
          if (!ifInstruction.isZeroTest()) {
            continue;
          }

          Value value = ifInstruction.lhs();
          if (!value.getType().isReferenceType()) {
            assert value.getType().isPrimitiveType();
            continue;
          }

          BasicBlock valueIsNullTarget = ifInstruction.targetFromCondition(0);
          if (valueIsNullTarget.getPredecessors().size() != 1
              || !valueIsNullTarget.exit().isThrow()) {
            continue;
          }

          Throw throwInstruction = valueIsNullTarget.exit().asThrow();
          Value exceptionValue = throwInstruction.exception().getAliasedValue();
          if (!exceptionValue.isConstZero()
              && exceptionValue.isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
            NewInstance newInstance = exceptionValue.definition.asNewInstance();
            if (newInstance.clazz != dexItemFactory.npeType) {
              continue;
            }
            if (newInstance.outValue().numberOfAllUsers() != 2) {
              continue; // Could be mutated before it is thrown.
            }
            InvokeDirect constructorCall = newInstance.getUniqueConstructorInvoke(dexItemFactory);
            if (constructorCall == null) {
              continue;
            }
            if (constructorCall.getInvokedMethod() != dexItemFactory.npeMethods.init) {
              continue;
            }
          } else if (!exceptionValue.isConstZero()) {
            continue;
          }

          boolean canDetachValueIsNullTarget = true;
          for (Instruction i : valueIsNullTarget.instructionsBefore(throwInstruction)) {
            if (!i.isBlockLocalInstructionWithoutSideEffects(appView, code.context())) {
              canDetachValueIsNullTarget = false;
              break;
            }
          }
          if (!canDetachValueIsNullTarget) {
            continue;
          }

          insertNotNullCheck(
              block,
              it,
              ifInstruction,
              ifInstruction.targetFromCondition(1),
              valueIsNullTarget,
              throwInstruction.getPosition());
          shouldRemoveUnreachableBlocks = true;
        }

        // Check for 'new-instance NullPointerException' with 2 users, not declaring a local and
        // not ending the scope of any locals.
        if (instruction.isNewInstance()
            && instruction.asNewInstance().clazz == dexItemFactory.npeType
            && instruction.outValue().numberOfAllUsers() == 2
            && !instruction.outValue().hasLocalInfo()
            && instruction.getDebugValues().isEmpty()) {
          if (it.hasNext()) {
            Instruction instruction2 = it.next();
            // Check for 'invoke NullPointerException.init() not ending the scope of any locals
            // and with the result of the first instruction as the argument. Also check that
            // the two first instructions have the same position.
            if (instruction2.isInvokeDirect()
                && instruction2.getDebugValues().isEmpty()) {
              InvokeDirect invokeDirect = instruction2.asInvokeDirect();
              if (invokeDirect.getInvokedMethod() == dexItemFactory.npeMethods.init
                  && invokeDirect.getReceiver() == instruction.outValue()
                  && invokeDirect.arguments().size() == 1
                  && invokeDirect.getPosition() == instruction.getPosition()) {
                if (it.hasNext()) {
                  Instruction instruction3 = it.next();
                  // Finally check that the last instruction is a throw of the initialized
                  // exception object and replace with 'throw null' if so.
                  if (instruction3.isThrow()
                      && instruction3.asThrow().exception() == instruction.outValue()) {
                    // Create const 0 with null type and a throw using that value.
                    Instruction nullPointer = code.createConstNull();
                    Instruction throwInstruction = new Throw(nullPointer.outValue());
                    // Preserve positions: we have checked that the first two original instructions
                    // have the same position.
                    assert instruction.getPosition() == instruction2.getPosition();
                    nullPointer.setPosition(instruction.getPosition());
                    throwInstruction.setPosition(instruction3.getPosition());
                    // Copy debug values from original throw to new throw to correctly end scope
                    // of locals.
                    instruction3.moveDebugValues(throwInstruction);
                    // Remove the three original instructions.
                    it.remove();
                    it.previous();
                    it.remove();
                    it.previous();
                    it.remove();
                    // Replace them with 'const 0' and 'throw'.
                    it.add(nullPointer);
                    it.add(throwInstruction);
                  }
                }
              }
            }
          }
        }
      }
    }
    if (shouldRemoveUnreachableBlocks) {
      Set<Value> affectedValues = code.removeUnreachableBlocks();
      if (!affectedValues.isEmpty()) {
        new TypeAnalysis(appView).narrowing(affectedValues);
      }
    }
    assert code.isConsistentSSA(appView);
  }

  // TODO(sgjesse); Move this somewhere else, and reuse it for some of the other switch rewritings.
  public abstract static class InstructionBuilder<T> {

    protected int blockNumber;
    protected final Position position;

    protected InstructionBuilder(Position position) {
      this.position = position;
    }

    public abstract T self();

    public T setBlockNumber(int blockNumber) {
      this.blockNumber = blockNumber;
      return self();
    }
  }

  public static class SwitchBuilder extends InstructionBuilder<SwitchBuilder> {

    private Value value;
    private final Int2ReferenceSortedMap<BasicBlock> keyToTarget = new Int2ReferenceAVLTreeMap<>();
    private BasicBlock fallthrough;

    public SwitchBuilder(Position position) {
      super(position);
    }

    @Override
    public SwitchBuilder self() {
      return this;
    }

    public SwitchBuilder setValue(Value value) {
      this.value = value;
      return this;
    }

    public SwitchBuilder addKeyAndTarget(int key, BasicBlock target) {
      keyToTarget.put(key, target);
      return this;
    }

    public SwitchBuilder setFallthrough(BasicBlock fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }

    public BasicBlock build(IRMetadata metadata) {
      final int NOT_FOUND = -1;
      Object2IntMap<BasicBlock> targetToSuccessorIndex = new Object2IntLinkedOpenHashMap<>();
      targetToSuccessorIndex.defaultReturnValue(NOT_FOUND);

      int[] keys = new int[keyToTarget.size()];
      int[] targetBlockIndices = new int[keyToTarget.size()];
      // Sort keys descending.
      int count = 0;
      IntIterator iter = keyToTarget.keySet().iterator();
      while (iter.hasNext()) {
        int key = iter.nextInt();
        BasicBlock target = keyToTarget.get(key);
        Integer targetIndex =
            targetToSuccessorIndex.computeIfAbsent(target, b -> targetToSuccessorIndex.size());
        keys[count] = key;
        targetBlockIndices[count] = targetIndex;
        count++;
      }
      Integer fallthroughIndex =
          targetToSuccessorIndex.computeIfAbsent(fallthrough, b -> targetToSuccessorIndex.size());
      IntSwitch newSwitch = new IntSwitch(value, keys, targetBlockIndices, fallthroughIndex);
      newSwitch.setPosition(position);
      BasicBlock newSwitchBlock = BasicBlock.createSwitchBlock(blockNumber, newSwitch, metadata);
      for (BasicBlock successor : targetToSuccessorIndex.keySet()) {
        newSwitchBlock.link(successor);
      }
      return newSwitchBlock;
    }
  }

  public static class IfBuilder extends InstructionBuilder<IfBuilder> {

    private final IRCode code;
    private Value left;
    private int right;
    private BasicBlock target;
    private BasicBlock fallthrough;

    public IfBuilder(Position position, IRCode code) {
      super(position);
      this.code = code;
    }

    @Override
    public IfBuilder self() {
      return this;
    }

    public IfBuilder setLeft(Value left) {
      this.left = left;
      return this;
    }

    public IfBuilder setRight(int right) {
      this.right = right;
      return this;
    }

    public IfBuilder setTarget(BasicBlock target) {
      this.target = target;
      return this;
    }

    public IfBuilder setFallthrough(BasicBlock fallthrough) {
      this.fallthrough = fallthrough;
      return this;
    }

    public BasicBlock build() {
      assert target != null;
      assert fallthrough != null;
      If newIf;
      BasicBlock ifBlock;
      if (right != 0) {
        ConstNumber rightConst = code.createIntConstant(right);
        rightConst.setPosition(position);
        newIf = new If(IfType.EQ, ImmutableList.of(left, rightConst.dest()));
        ifBlock = BasicBlock.createIfBlock(blockNumber, newIf, code.metadata(), rightConst);
      } else {
        newIf = new If(IfType.EQ, left);
        ifBlock = BasicBlock.createIfBlock(blockNumber, newIf, code.metadata());
      }
      newIf.setPosition(position);
      ifBlock.link(target);
      ifBlock.link(fallthrough);
      return ifBlock;
    }
  }

  private boolean checkArgumentType(InvokeMethod invoke, int argumentIndex) {
    // TODO(sgjesse): Insert cast if required.
    TypeElement returnType =
        TypeElement.fromDexType(invoke.getInvokedMethod().proto.returnType, maybeNull(), appView);
    TypeElement argumentType =
        TypeElement.fromDexType(getArgumentType(invoke, argumentIndex), maybeNull(), appView);
    return appView.enableWholeProgramOptimizations()
        ? argumentType.lessThanOrEqual(returnType, appView)
        : argumentType.equals(returnType);
  }

  private DexType getArgumentType(InvokeMethod invoke, int argumentIndex) {
    if (invoke.isInvokeStatic()) {
      return invoke.getInvokedMethod().proto.parameters.values[argumentIndex];
    }
    if (argumentIndex == 0) {
      return invoke.getInvokedMethod().holder;
    }
    return invoke.getInvokedMethod().proto.parameters.values[argumentIndex - 1];
  }

  // Replace result uses for methods where something is known about what is returned.
  public boolean rewriteMoveResult(IRCode code) {
    if (options.isGeneratingClassFiles() || !code.metadata().mayHaveInvokeMethod()) {
      return false;
    }

    AssumeRemover assumeRemover = new AssumeRemover(appView, code);
    boolean changed = false;
    boolean mayHaveRemovedTrivialPhi = false;
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (blocksToBeRemoved.contains(block)) {
        continue;
      }

      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        InvokeMethod invoke = iterator.next().asInvokeMethod();
        if (invoke == null || !invoke.hasOutValue() || invoke.outValue().hasLocalInfo()) {
          continue;
        }

        // Check if the invoked method is known to return one of its arguments.
        DexClassAndMethod target = invoke.lookupSingleTarget(appView, code.context());
        if (target == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo = target.getDefinition().getOptimizationInfo();
        if (!optimizationInfo.returnsArgument()) {
          continue;
        }

        int argumentIndex = optimizationInfo.getReturnedArgument();
        // Replace the out value of the invoke with the argument and ignore the out value.
        if (argumentIndex < 0 || !checkArgumentType(invoke, argumentIndex)) {
          continue;
        }

        Value argument = invoke.arguments().get(argumentIndex);
        Value outValue = invoke.outValue();
        assert outValue.verifyCompatible(argument.outType());

        // Make sure that we are only narrowing information here. Note, in cases where we cannot
        // find the definition of types, computing lessThanOrEqual will return false unless it is
        // object.
        if (!argument.getType().lessThanOrEqual(outValue.getType(), appView)) {
          continue;
        }

        Set<Value> affectedValues =
            argument.getType().equals(outValue.getType())
                ? Collections.emptySet()
                : outValue.affectedValues();

        assumeRemover.markAssumeDynamicTypeUsersForRemoval(outValue);
        mayHaveRemovedTrivialPhi |= outValue.numberOfPhiUsers() > 0;
        outValue.replaceUsers(argument);
        invoke.setOutValue(null);
        changed = true;

        if (!affectedValues.isEmpty()) {
          new TypeAnalysis(appView).narrowing(affectedValues);
        }
      }
    }
    assumeRemover.removeMarkedInstructions(blocksToBeRemoved).finish();
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    if (!blocksToBeRemoved.isEmpty()) {
      code.removeBlocks(blocksToBeRemoved);
      code.removeAllDeadAndTrivialPhis(affectedValues);
      assert code.getUnreachableBlocks().isEmpty();
    } else if (mayHaveRemovedTrivialPhi || assumeRemover.mayHaveIntroducedTrivialPhi()) {
      code.removeAllDeadAndTrivialPhis(affectedValues);
    }
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA(appView);
    return changed;
  }

  public static void removeOrReplaceByDebugLocalWrite(
      Instruction currentInstruction, InstructionListIterator it, Value inValue, Value outValue) {
    if (outValue.hasLocalInfo() && outValue.getLocalInfo() != inValue.getLocalInfo()) {
      DebugLocalWrite debugLocalWrite = new DebugLocalWrite(outValue, inValue);
      it.replaceCurrentInstruction(debugLocalWrite);
    } else {
      if (outValue.hasLocalInfo()) {
        assert outValue.getLocalInfo() == inValue.getLocalInfo();
        // Should remove the end-marker before replacing the current instruction.
        currentInstruction.removeDebugValue(outValue.getLocalInfo());
      }
      outValue.replaceUsers(inValue);
      it.removeOrReplaceByDebugLocalRead();
    }
  }

  // Split constants that flow into ranged invokes. This gives the register allocator more
  // freedom in assigning register to ranged invokes which can greatly reduce the number
  // of register needed (and thereby code size as well).
  public void splitRangeInvokeConstants(IRCode code) {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction current = it.next();
        if (current.isInvoke() && current.asInvoke().requiredArgumentRegisters() > 5) {
          Invoke invoke = current.asInvoke();
          it.previous();
          Map<ConstNumber, ConstNumber> oldToNew = new IdentityHashMap<>();
          for (int i = 0; i < invoke.inValues().size(); i++) {
            Value value = invoke.inValues().get(i);
            if (value.isConstNumber() && value.numberOfUsers() > 1) {
              ConstNumber definition = value.getConstInstruction().asConstNumber();
              Value originalValue = definition.outValue();
              ConstNumber newNumber = oldToNew.get(definition);
              if (newNumber == null) {
                newNumber = ConstNumber.copyOf(code, definition);
                it.add(newNumber);
                newNumber.setPosition(current.getPosition());
                oldToNew.put(definition, newNumber);
              }
              invoke.inValues().set(i, newNumber.outValue());
              originalValue.removeUser(invoke);
              newNumber.outValue().addUser(invoke);
            }
          }
          it.next();
        }
      }
    }
    assert code.isConsistentSSA(appView);
  }

  public void rewriteKnownArrayLengthCalls(IRCode code) {
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction current = iterator.next();
      if (!current.isArrayLength()) {
        continue;
      }

      ArrayLength arrayLength = current.asArrayLength();
      if (arrayLength.hasOutValue() && arrayLength.outValue().hasLocalInfo()) {
        continue;
      }

      Value array = arrayLength.array().getAliasedValue();
      if (array.isPhi() || !arrayLength.array().isNeverNull() || array.hasLocalInfo()) {
        continue;
      }

      AbstractValue abstractValue = array.getAbstractValue(appView, code.context());
      if (!abstractValue.hasKnownArrayLength() && !array.isNeverNull()) {
        continue;
      }
      Instruction arrayDefinition = array.getDefinition();
      assert arrayDefinition != null;

      Set<Phi> phiUsers = arrayLength.outValue().uniquePhiUsers();
      if (arrayDefinition.isNewArrayEmpty()) {
        Value size = arrayDefinition.asNewArrayEmpty().size();
        arrayLength.outValue().replaceUsers(size);
        iterator.removeOrReplaceByDebugLocalRead();
      } else if (arrayDefinition.isNewArrayFilledData()) {
        long size = arrayDefinition.asNewArrayFilledData().size;
        if (size > Integer.MAX_VALUE) {
          continue;
        }
        iterator.replaceCurrentInstructionWithConstInt(code, (int) size);
      } else if (abstractValue.hasKnownArrayLength()) {
        iterator.replaceCurrentInstructionWithConstInt(code, abstractValue.getKnownArrayLength());
      } else {
        continue;
      }

      phiUsers.forEach(Phi::removeTrivialPhi);
    }
    assert code.isConsistentSSA(appView);
  }

  /**
   * If an instruction is known to be a binop/lit8 or binop//lit16 instruction, update the
   * instruction to use its own constant that will be defined just before the instruction. This
   * transformation allows to decrease pressure on register allocation by defining the shortest
   * range of constant used by this kind of instruction. D8 knowns at build time that constant will
   * be encoded directly into the final Dex instruction.
   */
  public void useDedicatedConstantForLitInstruction(IRCode code) {
    if (!code.metadata().mayHaveArithmeticOrLogicalBinop()) {
      return;
    }

    for (BasicBlock block : code.blocks) {
      InstructionListIterator instructionIterator = block.listIterator(code);
      // Collect all the non constant in values for binop/lit8 or binop/lit16 instructions.
      Set<Value> binopsWithLit8OrLit16NonConstantValues = Sets.newIdentityHashSet();
      while (instructionIterator.hasNext()) {
        Instruction currentInstruction = instructionIterator.next();
        if (!isBinopWithLit8OrLit16(currentInstruction)) {
          continue;
        }
        Value value = binopWithLit8OrLit16NonConstant(currentInstruction.asBinop());
        assert value != null;
        binopsWithLit8OrLit16NonConstantValues.add(value);
      }
      if (binopsWithLit8OrLit16NonConstantValues.isEmpty()) {
        continue;
      }
      // Find last use in block of all the non constant in values for binop/lit8 or binop/lit16
      // instructions.
      Reference2IntMap<Value> lastUseOfBinopsWithLit8OrLit16NonConstantValues =
          new Reference2IntOpenHashMap<>();
      lastUseOfBinopsWithLit8OrLit16NonConstantValues.defaultReturnValue(-1);
      int currentInstructionNumber = block.getInstructions().size();
      while (instructionIterator.hasPrevious()) {
        Instruction currentInstruction = instructionIterator.previous();
        currentInstructionNumber--;
        for (Value value :
            Iterables.concat(currentInstruction.inValues(), currentInstruction.getDebugValues())) {
          if (!binopsWithLit8OrLit16NonConstantValues.contains(value)) {
            continue;
          }
          if (!lastUseOfBinopsWithLit8OrLit16NonConstantValues.containsKey(value)) {
            lastUseOfBinopsWithLit8OrLit16NonConstantValues.put(value, currentInstructionNumber);
          }
        }
      }
      // Do the transformation except if the binop can use the binop/2addr format.
      currentInstructionNumber--;
      assert currentInstructionNumber == -1;
      while (instructionIterator.hasNext()) {
        Instruction currentInstruction = instructionIterator.next();
        currentInstructionNumber++;
        if (!isBinopWithLit8OrLit16(currentInstruction)) {
          continue;
        }
        Binop binop = currentInstruction.asBinop();
        if (!canBe2AddrInstruction(
            binop, currentInstructionNumber, lastUseOfBinopsWithLit8OrLit16NonConstantValues)) {
          Value constValue = binopWithLit8OrLit16Constant(currentInstruction);
          if (constValue.numberOfAllUsers() > 1) {
            // No need to do the transformation if the const value is already used only one time.
            ConstNumber newConstant = ConstNumber
                .copyOf(code, constValue.definition.asConstNumber());
            newConstant.setPosition(currentInstruction.getPosition());
            newConstant.setBlock(currentInstruction.getBlock());
            currentInstruction.replaceValue(constValue, newConstant.outValue());
            constValue.removeUser(currentInstruction);
            instructionIterator.previous();
            instructionIterator.add(newConstant);
            instructionIterator.next();
          }
        }
      }
    }

    assert code.isConsistentSSA(appView);
  }

  // Check if a binop can be represented in the binop/lit8 or binop/lit16 form.
  private static boolean isBinopWithLit8OrLit16(Instruction instruction) {
    if (!instruction.isArithmeticBinop() && !instruction.isLogicalBinop()) {
      return false;
    }
    Binop binop = instruction.asBinop();
    // If one of the values does not need a register it is implicitly a binop/lit8 or binop/lit16.
    boolean result =
        !binop.needsValueInRegister(binop.leftValue())
            || !binop.needsValueInRegister(binop.rightValue());
    assert !result || binop.leftValue().isConstNumber() || binop.rightValue().isConstNumber();
    return result;
  }

  // Return the constant in-value of a binop/lit8 or binop/lit16 instruction.
  private static Value binopWithLit8OrLit16Constant(Instruction instruction) {
    assert isBinopWithLit8OrLit16(instruction);
    Binop binop = instruction.asBinop();
    if (binop.leftValue().isConstNumber()) {
      return binop.leftValue();
    } else if (binop.rightValue().isConstNumber()) {
      return binop.rightValue();
    } else {
      throw new Unreachable();
    }
  }

  // Return the non-constant in-value of a binop/lit8 or binop/lit16 instruction.
  private static Value binopWithLit8OrLit16NonConstant(Binop binop) {
    if (binop.leftValue().isConstNumber()) {
      return binop.rightValue();
    } else if (binop.rightValue().isConstNumber()) {
      return binop.leftValue();
    } else {
      throw new Unreachable();
    }
  }

  /**
   * Estimate if a binary operation can be a binop/2addr form or not. It can be a 2addr form when an
   * argument is no longer needed after the binary operation and can be overwritten. That is
   * definitely the case if there is no path between the binary operation and all other usages.
   */
  private static boolean canBe2AddrInstruction(
      Binop binop, int binopInstructionNumber, Reference2IntMap<Value> lastUseOfRelevantValue) {
    Value value = binopWithLit8OrLit16NonConstant(binop);
    assert value != null;
    int lastUseInstructionNumber = lastUseOfRelevantValue.getInt(value);
    // The binop instruction is a user, so there is always a last use in the block.
    assert lastUseInstructionNumber != -1;
    if (lastUseInstructionNumber > binopInstructionNumber) {
      return false;
    }

    Set<BasicBlock> noPathTo = Sets.newIdentityHashSet();
    BasicBlock binopBlock = binop.getBlock();
    Iterable<InstructionOrPhi> users =
        value.debugUsers() != null
            ? Iterables.concat(value.uniqueUsers(), value.debugUsers(), value.uniquePhiUsers())
            : Iterables.concat(value.uniqueUsers(), value.uniquePhiUsers());
    for (InstructionOrPhi user : users) {
      BasicBlock userBlock = user.getBlock();
      if (userBlock == binopBlock) {
        // All users in the current block are either before the binop instruction or the
        // binop instruction itself.
        continue;
      }
      if (noPathTo.contains(userBlock)) {
        continue;
      }
      if (binopBlock.hasPathTo(userBlock)) {
        return false;
      }
      noPathTo.add(userBlock);
    }

    return true;
  }

  public void shortenLiveRanges(IRCode code, ConstantCanonicalizer canonicalizer) {
    if (options.testing.disableShortenLiveRanges) {
      return;
    }
    LazyBox<DominatorTree> dominatorTreeMemoization = new LazyBox<>(() -> new DominatorTree(code));
    Map<BasicBlock, LinkedHashMap<Value, Instruction>> addConstantInBlock = new IdentityHashMap<>();
    LinkedList<BasicBlock> blocks = code.blocks;
    for (BasicBlock block : blocks) {
      shortenLiveRangesInsideBlock(
          code,
          block,
          dominatorTreeMemoization,
          addConstantInBlock,
          canonicalizer::isConstantCanonicalizationCandidate);
    }

    // Heuristic to decide if constant instructions are shared in dominator block
    // of usages or moved to the usages.

    // Process all blocks in stable order to avoid non-determinism of hash map iterator.
    BasicBlockIterator blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      Map<Value, Instruction> movedInstructions = addConstantInBlock.get(block);
      if (movedInstructions == null) {
        continue;
      }
      assert !movedInstructions.isEmpty();
      if (!block.isEntry() && movedInstructions.size() > STOP_SHARED_CONSTANT_THRESHOLD) {
        // If there are too many constant numbers in the same block they are copied rather than
        // shared unless used by a phi.
        movedInstructions
            .values()
            .removeIf(
                movedInstruction -> {
                  if (movedInstruction.outValue().hasPhiUsers()
                      || !movedInstruction.isConstNumber()) {
                    return false;
                  }
                  ConstNumber constNumber = movedInstruction.asConstNumber();
                  Value constantValue = movedInstruction.outValue();
                  for (Instruction user : constantValue.uniqueUsers()) {
                    ConstNumber newCstNum = ConstNumber.copyOf(code, constNumber);
                    newCstNum.setPosition(user.getPosition());
                    InstructionListIterator iterator = user.getBlock().listIterator(code, user);
                    iterator.previous();
                    iterator.add(newCstNum);
                    user.replaceValue(constantValue, newCstNum.outValue());
                  }
                  constantValue.clearUsers();
                  return true;
                });
      }

      // Add constant into the dominator block of usages.
      boolean hasCatchHandlers = block.hasCatchHandlers();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction insertionPoint = instructionIterator.next();
        if (insertionPoint.isJumpInstruction()
            || (hasCatchHandlers && insertionPoint.instructionTypeCanThrow())
            || (options.canHaveCmpIfFloatBug() && insertionPoint.isCmp())) {
          break;
        }
        for (Value use :
            Iterables.concat(insertionPoint.inValues(), insertionPoint.getDebugValues())) {
          Instruction movedInstruction = movedInstructions.remove(use);
          if (movedInstruction != null) {
            instructionIterator =
                insertInstructionWithShortenedLiveRange(
                    code, blockIterator, instructionIterator, movedInstruction, insertionPoint);
          }
        }
      }

      // Insert remaining constant instructions prior to the "exit".
      Instruction insertionPoint = instructionIterator.peekPrevious();
      for (Instruction movedInstruction : movedInstructions.values()) {
        instructionIterator =
            insertInstructionWithShortenedLiveRange(
                code, blockIterator, instructionIterator, movedInstruction, insertionPoint);
      }
    }

    assert code.isConsistentSSA(appView);
  }

  private InstructionListIterator insertInstructionWithShortenedLiveRange(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      Instruction movedInstruction,
      Instruction insertionPoint) {
    Instruction previous = instructionIterator.previous();
    assert previous == insertionPoint;
    movedInstruction.setPosition(
        getPositionForMovedNonThrowingInstruction(movedInstruction, insertionPoint));
    if (movedInstruction.instructionTypeCanThrow()
        && insertionPoint.getBlock().hasCatchHandlers()) {
      // Split the block and reset the block iterator.
      BasicBlock splitBlock =
          instructionIterator.splitCopyCatchHandlers(code, blockIterator, appView.options());
      BasicBlock previousBlock = blockIterator.previousUntil(b -> b == splitBlock);
      assert previousBlock == splitBlock;
      blockIterator.next();

      // Add the constant instruction before the exit instruction.
      assert !instructionIterator.hasNext();
      instructionIterator.previous();
      instructionIterator.add(movedInstruction);

      // Continue insertion at the entry of the split block.
      instructionIterator = splitBlock.listIterator(code);
    } else {
      instructionIterator.add(movedInstruction);
    }
    Instruction next = instructionIterator.next();
    assert next == insertionPoint;
    return instructionIterator;
  }

  private Position getPositionForMovedNonThrowingInstruction(
      Instruction movedInstruction, Instruction insertionPoint) {
    // If the type of the moved instruction is throwing and we don't have a position at the
    // insertion point, we use the special synthetic-none position, which is OK as the moved
    // instruction instance is known not to throw (or we would not be allowed the move it).
    if (movedInstruction.instructionTypeCanThrow() && !insertionPoint.getPosition().isSome()) {
      return Position.syntheticNone();
    }
    return insertionPoint.getPosition();
  }

  private void shortenLiveRangesInsideBlock(
      IRCode code,
      BasicBlock block,
      LazyBox<DominatorTree> dominatorTreeMemoization,
      Map<BasicBlock, LinkedHashMap<Value, Instruction>> addConstantInBlock,
      Predicate<Instruction> selector) {
    InstructionListIterator iterator = block.listIterator(code);
    boolean seenCompareExit = false;
    while (iterator.hasNext()) {
      Instruction instruction = iterator.next();
      if (options.canHaveCmpIfFloatBug() && instruction.isCmp()) {
        seenCompareExit = true;
      }

      if (instruction.hasUnusedOutValue() || instruction.outValue().hasLocalInfo()) {
        continue;
      }

      if (!selector.test(instruction)) {
        continue;
      }

      // Here we try to stop wasting time in the common case where constants are used immediately
      // after their definition.
      //
      // This is especially important for the creation of large arrays, which has the following code
      // pattern repeated many times, where the two loaded constants are only used by the ArrayPut
      // instruction.
      //
      //   Const number (the array index)
      //   Const (the array entry value)
      //   ArrayPut
      //
      // The heuristic is therefore to check for constants used only once if the use is within the
      // next two instructions, and only swap them if that is the case (cannot shorten the live
      // range anyway).
      if (instruction.outValue().hasSingleUniqueUser() && !instruction.outValue().hasPhiUsers()) {
        Instruction uniqueUse = instruction.outValue().singleUniqueUser();
        Instruction next = iterator.next();
        if (uniqueUse == next) {
          iterator.previous();
          continue;
        }
        if (next.hasOutValue()
            && next.outValue().hasSingleUniqueUser()
            && !next.outValue().hasPhiUsers()
            && iterator.hasNext()) {
          Instruction nextNext = iterator.peekNext();
          Instruction uniqueUseNext = next.outValue().singleUniqueUser();
          if (uniqueUse == nextNext && uniqueUseNext == nextNext) {
            iterator.previous();
            continue;
          }
        }
        iterator.previous();
        // The call to removeOrReplaceByDebugLocalRead() at the end of this method will remove the
        // last returned element of this iterator. Therefore, we re-read this element from the
        // iterator.
        iterator.previous();
        iterator.next();
      }
      // Collect the blocks for all users of the constant.
      Set<BasicBlock> userBlocks = new LinkedHashSet<>();
      for (Instruction user : instruction.outValue().uniqueUsers()) {
        userBlocks.add(user.getBlock());
      }
      for (Phi phi : instruction.outValue().uniquePhiUsers()) {
        int predecessorIndex = 0;
        for (Value operand : phi.getOperands()) {
          if (operand == instruction.outValue()) {
            userBlocks.add(phi.getBlock().getPredecessors().get(predecessorIndex));
          }
          predecessorIndex++;
        }
      }
      // Locate the closest dominator block for all user blocks.
      DominatorTree dominatorTree = dominatorTreeMemoization.computeIfAbsent();
      BasicBlock dominator = dominatorTree.closestDominator(userBlocks);

      if (instruction.instructionTypeCanThrow()) {
        if (block.hasCatchHandlers() || dominator.hasCatchHandlers()) {
          // Do not move the constant if the constant instruction can throw
          // and the dominator or the original block has catch handlers.
          continue;
        }
      }

      // If the dominator block has a potential compare exit we will chose that as the insertion
      // point. Uniquely for instructions having invalues this can be before the definition of them.
      // Bail-out when this is the case. See b/251015885 for more information.
      if (seenCompareExit
          && Iterables.any(instruction.inValues(), x -> x.getBlock() == dominator)) {
        continue;
      }

      Instruction copy;
      switch (instruction.opcode()) {
        case CONST_CLASS:
          copy = ConstClass.copyOf(code, instruction.asConstClass());
          break;
        case CONST_NUMBER:
          copy = ConstNumber.copyOf(code, instruction.asConstNumber());
          break;
        case CONST_STRING:
          copy = ConstString.copyOf(code, instruction.asConstString());
          break;
        case DEX_ITEM_BASED_CONST_STRING:
          copy = DexItemBasedConstString.copyOf(code, instruction.asDexItemBasedConstString());
          break;
        case INSTANCE_GET:
          copy = InstanceGet.copyOf(code, instruction.asInstanceGet());
          break;
        case STATIC_GET:
          copy = StaticGet.copyOf(code, instruction.asStaticGet());
          break;
        default:
          throw new Unreachable();
      }
      instruction.outValue().replaceUsers(copy.outValue());
      addConstantInBlock
          .computeIfAbsent(dominator, k -> new LinkedHashMap<>())
          .put(copy.outValue(), copy);
      assert iterator.peekPrevious() == instruction;
      iterator.removeOrReplaceByDebugLocalRead();
    }
  }

  // TODO(mikaelpeltier) Manage that from and to instruction do not belong to the same block.
  private static boolean hasLocalOrLineChangeBetween(
      Instruction from, Instruction to, DexString localVar) {
    if (from.getBlock() != to.getBlock()) {
      return true;
    }
    if (from.getPosition().isSome()
        && to.getPosition().isSome()
        && !from.getPosition().equals(to.getPosition())) {
      return true;
    }
    Position position = null;
    for (Instruction instruction : from.getBlock().instructionsAfter(from)) {
      if (position == null) {
        if (instruction.getPosition().isSome()) {
          position = instruction.getPosition();
        }
      } else if (instruction.getPosition().isSome()
          && !position.equals(instruction.getPosition())) {
        return true;
      }
      if (instruction == to) {
        return false;
      }
      if (instruction.outValue() != null && instruction.outValue().hasLocalInfo()) {
        if (instruction.outValue().getLocalInfo().name == localVar) {
          return true;
        }
      }
    }
    throw new Unreachable();
  }

  public void simplifyDebugLocals(IRCode code) {
    for (BasicBlock block : code.blocks) {
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction prevInstruction = iterator.peekPrevious();
        Instruction instruction = iterator.next();
        if (instruction.isDebugLocalWrite()) {
          assert instruction.inValues().size() == 1;
          Value inValue = instruction.inValues().get(0);
          DebugLocalInfo localInfo = instruction.outValue().getLocalInfo();
          DexString localName = localInfo.name;
          if (!inValue.hasLocalInfo() &&
              inValue.numberOfAllUsers() == 1 &&
              inValue.definition != null &&
              !hasLocalOrLineChangeBetween(inValue.definition, instruction, localName)) {
            inValue.setLocalInfo(localInfo);
            instruction.outValue().replaceUsers(inValue);
            Value overwrittenLocal = instruction.removeDebugValue(localInfo);
            if (overwrittenLocal != null) {
              overwrittenLocal.addDebugLocalEnd(inValue.definition);
            }
            if (prevInstruction != null &&
                (prevInstruction.outValue() == null
                    || !prevInstruction.outValue().hasLocalInfo()
                    || !instruction.getDebugValues().contains(prevInstruction.outValue()))) {
              instruction.moveDebugValues(prevInstruction);
            }
            iterator.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
  }

  /**
   * This optimization exploits that we can sometimes learn the constant value of an SSA value that
   * flows into an if-eq of if-neq instruction.
   *
   * <p>Consider the following example:
   *
   * <pre>
   * 1. if (obj != null) {
   * 2.  return doStuff();
   * 3. }
   * 4. return null;
   * </pre>
   *
   * <p>Since we know that `obj` is null in all blocks that are dominated by the false-target of the
   * if-instruction in line 1, we can safely replace the null-constant in line 4 by `obj`, and
   * thereby save a const-number instruction.
   */
  public void redundantConstNumberRemoval(IRCode code) {
    if (appView.options().canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug()
        && !appView.options().testing.forceRedundantConstNumberRemoval) {
      // See also b/124152497.
      return;
    }

    if (!code.metadata().mayHaveConstNumber()) {
      return;
    }

    LazyBox<Long2ReferenceMap<List<ConstNumber>>> constantsByValue =
        new LazyBox<>(() -> getConstantsByValue(code));
    LazyBox<DominatorTree> dominatorTree = new LazyBox<>(() -> new DominatorTree(code));

    boolean changed = false;
    for (BasicBlock block : code.blocks) {
      Instruction lastInstruction = block.getInstructions().getLast();
      if (!lastInstruction.isIf()) {
        continue;
      }

      If ifInstruction = lastInstruction.asIf();
      IfType type = ifInstruction.getType();

      Value lhs = ifInstruction.inValues().get(0);
      Value rhs = !ifInstruction.isZeroTest() ? ifInstruction.inValues().get(1) : null;

      if (!ifInstruction.isZeroTest() && !lhs.isConstNumber() && !rhs.isConstNumber()) {
        // We can only conclude anything from an if-instruction if it is a zero-test or if one of
        // the two operands is a constant.
        continue;
      }

      // If the type is neither EQ nor NE, we cannot conclude anything about any of the in-values
      // of the if-instruction from the outcome of the if-instruction.
      if (type != IfType.EQ && type != IfType.NE) {
        continue;
      }

      BasicBlock trueTarget, falseTarget;
      if (type == IfType.EQ) {
        trueTarget = ifInstruction.getTrueTarget();
        falseTarget = ifInstruction.fallthroughBlock();
      } else {
        falseTarget = ifInstruction.getTrueTarget();
        trueTarget = ifInstruction.fallthroughBlock();
      }

      if (ifInstruction.isZeroTest()) {
        changed |=
            replaceDominatedConstNumbers(0, lhs, trueTarget, constantsByValue, code, dominatorTree);
        if (lhs.knownToBeBoolean()) {
          changed |=
              replaceDominatedConstNumbers(
                  1, lhs, falseTarget, constantsByValue, code, dominatorTree);
        }
      } else {
        assert rhs != null;
        if (lhs.isConstNumber()) {
          ConstNumber lhsAsNumber = lhs.getConstInstruction().asConstNumber();
          changed |=
              replaceDominatedConstNumbers(
                  lhsAsNumber.getRawValue(),
                  rhs,
                  trueTarget,
                  constantsByValue,
                  code,
                  dominatorTree);
          if (lhs.knownToBeBoolean() && rhs.knownToBeBoolean()) {
            changed |=
                replaceDominatedConstNumbers(
                    negateBoolean(lhsAsNumber),
                    rhs,
                    falseTarget,
                    constantsByValue,
                    code,
                    dominatorTree);
          }
        } else {
          assert rhs.isConstNumber();
          ConstNumber rhsAsNumber = rhs.getConstInstruction().asConstNumber();
          changed |=
              replaceDominatedConstNumbers(
                  rhsAsNumber.getRawValue(),
                  lhs,
                  trueTarget,
                  constantsByValue,
                  code,
                  dominatorTree);
          if (lhs.knownToBeBoolean() && rhs.knownToBeBoolean()) {
            changed |=
                replaceDominatedConstNumbers(
                    negateBoolean(rhsAsNumber),
                    lhs,
                    falseTarget,
                    constantsByValue,
                    code,
                    dominatorTree);
          }
        }
      }

      if (constantsByValue.computeIfAbsent().isEmpty()) {
        break;
      }
    }

    if (changed) {
      code.removeAllDeadAndTrivialPhis();
    }
    assert code.isConsistentSSA(appView);
  }

  private static Long2ReferenceMap<List<ConstNumber>> getConstantsByValue(IRCode code) {
    // A map from the raw value of constants in `code` to the const number instructions that define
    // the given raw value (irrespective of the type of the raw value).
    Long2ReferenceMap<List<ConstNumber>> constantsByValue = new Long2ReferenceOpenHashMap<>();

    // Initialize `constantsByValue`.
    for (Instruction instruction : code.instructions()) {
      if (instruction.isConstNumber()) {
        ConstNumber constNumber = instruction.asConstNumber();
        if (constNumber.outValue().hasLocalInfo()) {
          // Not necessarily constant, because it could be changed in the debugger.
          continue;
        }
        long rawValue = constNumber.getRawValue();
        if (constantsByValue.containsKey(rawValue)) {
          constantsByValue.get(rawValue).add(constNumber);
        } else {
          List<ConstNumber> list = new ArrayList<>();
          list.add(constNumber);
          constantsByValue.put(rawValue, list);
        }
      }
    }
    return constantsByValue;
  }

  private static int negateBoolean(ConstNumber number) {
    assert number.outValue().knownToBeBoolean();
    return number.getRawValue() == 0 ? 1 : 0;
  }

  private boolean replaceDominatedConstNumbers(
      long withValue,
      Value newValue,
      BasicBlock dominator,
      LazyBox<Long2ReferenceMap<List<ConstNumber>>> constantsByValueSupplier,
      IRCode code,
      LazyBox<DominatorTree> dominatorTree) {
    if (newValue.hasLocalInfo()) {
      // We cannot replace a constant with a value that has local info, because that could change
      // debugging behavior.
      return false;
    }

    Long2ReferenceMap<List<ConstNumber>> constantsByValue =
        constantsByValueSupplier.computeIfAbsent();
    List<ConstNumber> constantsWithValue = constantsByValue.get(withValue);
    if (constantsWithValue == null || constantsWithValue.isEmpty()) {
      return false;
    }

    boolean changed = false;

    ListIterator<ConstNumber> constantWithValueIterator = constantsWithValue.listIterator();
    while (constantWithValueIterator.hasNext()) {
      ConstNumber constNumber = constantWithValueIterator.next();
      Value value = constNumber.outValue();
      assert !value.hasLocalInfo();
      assert constNumber.getRawValue() == withValue;

      BasicBlock block = constNumber.getBlock();

      // If the following condition does not hold, then the if-instruction does not dominate the
      // block containing the constant, although the true or false target does.
      if (block == dominator && block.getPredecessors().size() != 1) {
        // This should generally not happen, but it is possible to write bytecode where it does.
        assert false;
        continue;
      }

      if (value.knownToBeBoolean() && !newValue.knownToBeBoolean()) {
        // We cannot replace a boolean by a none-boolean since that can lead to verification
        // errors. For example, the following code fails with "register v1 has type Imprecise
        // Constant: 127 but expected Boolean return-1nr".
        //
        //   public boolean convertIntToBoolean(int v1) {
        //       const/4 v0, 0x1
        //       if-eq v1, v0, :eq_true
        //       const/4 v1, 0x0
        //     :eq_true
        //       return v1
        //   }
        continue;
      }

      if (dominatorTree.computeIfAbsent().dominatedBy(block, dominator)) {
        if (newValue.getType().lessThanOrEqual(value.getType(), appView)) {
          value.replaceUsers(newValue);
          block.listIterator(code, constNumber).removeOrReplaceByDebugLocalRead();
          constantWithValueIterator.remove();
          changed = true;
        } else if (value.getType().isNullType()) {
          // TODO(b/120257211): Need a mechanism to determine if `newValue` can be used at all of
          // the use sites of `value` without introducing a type error.
        }
      }
    }

    if (constantsWithValue.isEmpty()) {
      constantsByValue.remove(withValue);
    }

    return changed;
  }

  private boolean isPotentialTrivialRethrowValue(Value exceptionValue) {
    if (exceptionValue.hasDebugUsers()) {
      return false;
    }
    if (exceptionValue.hasUsers()) {
      if (exceptionValue.hasPhiUsers()
          || !exceptionValue.hasSingleUniqueUser()
          || !exceptionValue.singleUniqueUser().isThrow()) {
        return false;
      }
    } else if (exceptionValue.numberOfPhiUsers() != 1) {
      return false;
    }
    return true;
  }

  private boolean isSingleHandlerTrivial(BasicBlock firstBlock, IRCode code) {
    InstructionListIterator instructionIterator = firstBlock.listIterator(code);
    Instruction instruction = instructionIterator.next();
    if (!instruction.isMoveException()) {
      // A catch handler which doesn't use its exception is not going to be a trivial rethrow.
      return false;
    }
    Value exceptionValue = instruction.outValue();
    if (!isPotentialTrivialRethrowValue(exceptionValue)) {
      return false;
    }
    while (instructionIterator.hasNext()) {
      instruction = instructionIterator.next();
      BasicBlock currentBlock = instruction.getBlock();
      if (instruction.isGoto()) {
        BasicBlock nextBlock = instruction.asGoto().getTarget();
        int predecessorIndex = nextBlock.getPredecessors().indexOf(currentBlock);
        Value phiAliasOfExceptionValue = null;
        for (Phi phi : nextBlock.getPhis()) {
          Value operand = phi.getOperand(predecessorIndex);
          if (exceptionValue == operand) {
            phiAliasOfExceptionValue = phi;
            break;
          }
        }
        if (phiAliasOfExceptionValue != null) {
          if (!isPotentialTrivialRethrowValue(phiAliasOfExceptionValue)) {
            return false;
          }
          exceptionValue = phiAliasOfExceptionValue;
        }
        instructionIterator = nextBlock.listIterator(code);
      } else if (instruction.isThrow()) {
        List<Value> throwValues = instruction.inValues();
        assert throwValues.size() == 1;
        if (throwValues.get(0) != exceptionValue) {
          return false;
        }
        CatchHandlers<BasicBlock> currentHandlers = currentBlock.getCatchHandlers();
        if (!currentHandlers.isEmpty()) {
          // This is the case where our trivial catch handler has catch handler(s). For now, we
          // will only treat our block as trivial if all its catch handlers are also trivial.
          // Note: it is possible that we could "bridge" a trivial handler, where we take the
          // handlers of the handler and bring them up to replace the trivial handler. Example:
          //   catch (Throwable t) {
          //     try { throw t; } catch(Throwable abc) { foo(abc); }
          //   }
          // could turn into:
          //   catch (Throwable abc) {
          //     foo(abc);
          //   }
          // However this gets significantly harder when you have to consider non-matching guard
          // types.
          for (CatchHandler<BasicBlock> handler : currentHandlers) {
            if (!isSingleHandlerTrivial(handler.getTarget(), code)) {
              return false;
            }
          }
        }
        return true;
      } else {
        // Any other instructions in the catch handler means it's not trivial, and thus we can't
        // elide.
        return false;
      }
    }
    throw new Unreachable("Triviality check should always return before the loop terminates");
  }

  // Find any case where we have a catch followed immediately and only by a rethrow. This is extra
  // code doing what the JVM does automatically and can be safely elided.
  public void optimizeRedundantCatchRethrowInstructions(IRCode code) {
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    boolean hasUnlinkedCatchHandlers = false;
    while (blockIterator.hasNext()) {
      BasicBlock blockWithHandlers = blockIterator.next();
      if (blockWithHandlers.hasCatchHandlers()) {
        boolean allHandlersAreTrivial = true;
        for (CatchHandler<BasicBlock> handler : blockWithHandlers.getCatchHandlers()) {
          if (!isSingleHandlerTrivial(handler.target, code)) {
            allHandlersAreTrivial = false;
            break;
          }
        }
        // We need to ensure all handlers are trivial to unlink, since if one is non-trivial, and
        // its guard is a parent type to a trivial one, removing the trivial catch will result in
        // us hitting the non-trivial catch. This could be avoided by more sophisticated type
        // analysis.
        if (allHandlersAreTrivial) {
          hasUnlinkedCatchHandlers = true;
          for (CatchHandler<BasicBlock> handler : blockWithHandlers.getCatchHandlers()) {
            handler.getTarget().unlinkCatchHandler();
          }
        }
      }
    }
    if (hasUnlinkedCatchHandlers) {
      code.removeUnreachableBlocks();
    }
  }

  // Find all instructions that always throw, split the block after each such instruction and follow
  // it with a block throwing a null value (which should result in NPE). Note that this throw is not
  // expected to be ever reached, but is intended to satisfy verifier.
  public void optimizeAlwaysThrowingInstructions(IRCode code) {
    Set<Value> affectedValues = Sets.newIdentityHashSet();
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    ProgramMethod context = code.context();
    boolean hasUnlinkedCatchHandlers = false;
    // For cyclic phis we sometimes do not propagate the dynamic upper type after rewritings.
    // The inValue.isAlwaysNull(appView) check below will not recompute the dynamic type of phi's
    // so we recompute all phis here if they are always null.
    AppView<AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.hasClassHierarchy() ? appView.withClassHierarchy() : null;
    if (appViewWithClassHierarchy != null) {
      code.blocks.forEach(
          block ->
              block
                  .getPhis()
                  .forEach(
                      phi -> {
                        if (!phi.getType().isDefinitelyNull()) {
                          TypeElement dynamicUpperBoundType =
                              phi.getDynamicUpperBoundType(appViewWithClassHierarchy);
                          if (dynamicUpperBoundType.isDefinitelyNull()) {
                            affectedValues.add(phi);
                            phi.setType(dynamicUpperBoundType);
                          }
                        }
                      }));
    }
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (block.getNumber() != 0 && block.getPredecessors().isEmpty()) {
        continue;
      }
      if (blocksToRemove.contains(block)) {
        continue;
      }
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.throwsOnNullInput()) {
          Value inValue = instruction.getNonNullInput();
          if (inValue.isAlwaysNull(appView)) {
            // Insert `throw null` after the instruction if it is not guaranteed to throw an NPE.
            if (instruction.isAssume()) {
              // If this assume is in a block with catch handlers, then the out-value can have
              // usages in the catch handler if the block's throwing instruction comes after the
              // assume instruction. In this case, the catch handler is also guaranteed to be dead,
              // so we detach it from the current block.
              if (block.hasCatchHandlers()
                  && block.isInstructionBeforeThrowingInstruction(instruction)) {
                for (CatchHandler<BasicBlock> catchHandler : block.getCatchHandlers()) {
                  catchHandler.getTarget().unlinkCatchHandler();
                }
                hasUnlinkedCatchHandlers = true;
              }
            } else if (instruction.isInstanceFieldInstruction()) {
              InstanceFieldInstruction instanceFieldInstruction =
                  instruction.asInstanceFieldInstruction();
              if (instanceFieldInstruction.instructionInstanceCanThrow(
                  appView, context, SideEffectAssumption.RECEIVER_NOT_NULL)) {
                instructionIterator.next();
              }
            } else if (instruction.isInvokeMethodWithReceiver()) {
              InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
              SideEffectAssumption assumption =
                  SideEffectAssumption.RECEIVER_NOT_NULL.join(
                      SideEffectAssumption.INVOKED_METHOD_DOES_NOT_HAVE_SIDE_EFFECTS);
              if (invoke.instructionMayHaveSideEffects(appView, context, assumption)) {
                instructionIterator.next();
              }
            }
            instructionIterator.replaceCurrentInstructionWithThrowNull(
                appView, code, blockIterator, blocksToRemove, affectedValues);
            continue;
          }
        }

        if (!instruction.isInvokeMethod()) {
          continue;
        }

        InvokeMethod invoke = instruction.asInvokeMethod();
        DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
        if (singleTarget == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo =
            singleTarget.getDefinition().getOptimizationInfo();

        // If the invoke instruction is a null check, we can remove it.
        boolean isNullCheck = false;
        if (optimizationInfo.hasNonNullParamOrThrow()) {
          BitSet nonNullParamOrThrow = optimizationInfo.getNonNullParamOrThrow();
          for (int i = 0; i < invoke.arguments().size(); i++) {
            Value argument = invoke.arguments().get(i);
            if (argument.isAlwaysNull(appView) && nonNullParamOrThrow.get(i)) {
              isNullCheck = true;
              break;
            }
          }
        }
        // If the invoke instruction never returns normally, we can insert a throw null instruction
        // after the invoke.
        if (isNullCheck || optimizationInfo.neverReturnsNormally()) {
          instructionIterator.setInsertionPosition(invoke.getPosition());
          instructionIterator.next();
          instructionIterator.replaceCurrentInstructionWithThrowNull(
              appView, code, blockIterator, blocksToRemove, affectedValues);
          instructionIterator.unsetInsertionPosition();
        }
      }
    }
    code.removeBlocks(blocksToRemove);
    if (hasUnlinkedCatchHandlers) {
      affectedValues.addAll(code.removeUnreachableBlocks());
    }
    assert code.getUnreachableBlocks().isEmpty();
    if (!affectedValues.isEmpty()) {
      new TypeAnalysis(appView).narrowing(affectedValues);
    }
    assert code.isConsistentSSA(appView);
  }


  private void insertNotNullCheck(
      BasicBlock block,
      InstructionListIterator iterator,
      If theIf,
      BasicBlock target,
      BasicBlock deadTarget,
      Position position) {
    deadTarget.unlinkSinglePredecessorSiblingsAllowed();
    assert theIf == block.exit();
    iterator.previous();
    Instruction instruction;
    DexMethod getClassMethod = appView.dexItemFactory().objectMembers.getClass;
    instruction = new InvokeVirtual(getClassMethod, null, ImmutableList.of(theIf.lhs()));
    instruction.setPosition(position);
    iterator.add(instruction);
    iterator.next();
    iterator.replaceCurrentInstruction(new Goto());
    assert block.exit().isGoto();
    assert block.exit().asGoto().getTarget() == target;
  }

  /**
   * Remove moves that are not actually used by instructions in exiting paths. These moves can arise
   * due to debug local info needing a particular value and the live-interval for it then moves it
   * back into the properly assigned register. If the register is only used for debug purposes, it
   * is safe to just remove the move and update the local information accordingly.
   */
  public static void removeUnneededMovesOnExitingPaths(
      IRCode code, LinearScanRegisterAllocator allocator) {
    if (!allocator.options().debug) {
      return;
    }
    for (BasicBlock block : code.blocks) {
      // Skip non-exit blocks.
      if (!block.getSuccessors().isEmpty()) {
        continue;
      }
      // Skip blocks with no locals at entry.
      Int2ReferenceMap<DebugLocalInfo> localsAtEntry = block.getLocalsAtEntry();
      if (localsAtEntry == null || localsAtEntry.isEmpty()) {
        continue;
      }
      // Find the locals state after spill moves.
      DebugLocalsChange postSpillLocalsChange = null;
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.getNumber() != -1 || postSpillLocalsChange != null) {
          break;
        }
        postSpillLocalsChange = instruction.asDebugLocalsChange();
      }
      // Skip if the locals state did not change.
      if (postSpillLocalsChange == null
          || !postSpillLocalsChange.apply(new Int2ReferenceOpenHashMap<>(localsAtEntry))) {
        continue;
      }
      // Collect the moves that can safely be removed.
      Set<Move> unneededMoves = computeUnneededMoves(block, postSpillLocalsChange, allocator);
      if (unneededMoves.isEmpty()) {
        continue;
      }
      Int2IntMap previousMapping = new Int2IntOpenHashMap();
      Int2IntMap mapping = new Int2IntOpenHashMap();
      InstructionListIterator it = block.listIterator(code);
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isMove()) {
          Move move = instruction.asMove();
          if (unneededMoves.contains(move)) {
            int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
            int src = allocator.getRegisterForValue(move.src(), move.getNumber());
            int mappedSrc = mapping.getOrDefault(src, src);
            mapping.put(dst, mappedSrc);
            it.removeInstructionIgnoreOutValue();
          }
        } else if (instruction.isDebugLocalsChange()) {
          DebugLocalsChange change = instruction.asDebugLocalsChange();
          updateDebugLocalsRegisterMap(previousMapping, change.getEnding());
          updateDebugLocalsRegisterMap(mapping, change.getStarting());
          previousMapping = mapping;
          mapping = new Int2IntOpenHashMap(previousMapping);
        }
      }
    }
  }

  private static Set<Move> computeUnneededMoves(
      BasicBlock block,
      DebugLocalsChange postSpillLocalsChange,
      LinearScanRegisterAllocator allocator) {
    Set<Move> unneededMoves = Sets.newIdentityHashSet();
    IntSet usedRegisters = new IntOpenHashSet();
    IntSet clobberedRegisters = new IntOpenHashSet();
    // Backwards instruction scan collecting the registers used by actual instructions.
    boolean inEntrySpillMoves = false;
    InstructionIterator it = block.iterator(block.getInstructions().size());
    while (it.hasPrevious()) {
      Instruction instruction = it.previous();
      if (instruction == postSpillLocalsChange) {
        inEntrySpillMoves = true;
      }
      // If this is a move in the block-entry spill moves check if it is unneeded.
      if (inEntrySpillMoves && instruction.isMove()) {
        Move move = instruction.asMove();
        int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
        int src = allocator.getRegisterForValue(move.src(), move.getNumber());
        if (!usedRegisters.contains(dst) && !clobberedRegisters.contains(src)) {
          unneededMoves.add(move);
          continue;
        }
      }
      if (instruction.outValue() != null && instruction.outValue().needsRegister()) {
        int register =
            allocator.getRegisterForValue(instruction.outValue(), instruction.getNumber());
        // The register is defined anew, so uses before this are on distinct values.
        usedRegisters.remove(register);
        // Mark it clobbered to avoid any uses in locals after this point to become invalid.
        clobberedRegisters.add(register);
      }
      if (!instruction.inValues().isEmpty()) {
        for (Value inValue : instruction.inValues()) {
          if (inValue.needsRegister()) {
            int register = allocator.getRegisterForValue(inValue, instruction.getNumber());
            // Record the register as being used.
            usedRegisters.add(register);
          }
        }
      }
    }
    return unneededMoves;
  }

  private static void updateDebugLocalsRegisterMap(
      Int2IntMap mapping, Int2ReferenceMap<DebugLocalInfo> locals) {
    // If nothing is mapped nothing needs to be changed.
    if (mapping.isEmpty()) {
      return;
    }
    // Locals is final, so we copy and clear it during update.
    Int2ReferenceMap<DebugLocalInfo> copy = new Int2ReferenceOpenHashMap<>(locals);
    locals.clear();
    for (Entry<DebugLocalInfo> entry : copy.int2ReferenceEntrySet()) {
      int oldRegister = entry.getIntKey();
      int newRegister = mapping.getOrDefault(oldRegister, oldRegister);
      locals.put(newRegister, entry.getValue());
    }
  }

  private Value addConstString(IRCode code, InstructionListIterator iterator, String s) {
    TypeElement typeLattice = TypeElement.stringClassType(appView, definitelyNotNull());
    Value value = code.createValue(typeLattice);
    iterator.add(new ConstString(value, dexItemFactory.createString(s)));
    return value;
  }

  /**
   * Insert code into <code>method</code> to log the argument types to System.out.
   *
   * The type is determined by calling getClass() on the argument.
   */
  public void logArgumentTypes(DexEncodedMethod method, IRCode code) {
    List<Value> arguments = code.collectArguments();
    BasicBlock block = code.entryBlock();
    InstructionListIterator iterator = block.listIterator(code);

    // Attach some synthetic position to all inserted code.
    Position position =
        SyntheticPosition.builder().setLine(1).setMethod(method.getReference()).build();
    iterator.setInsertionPosition(position);

    // Split arguments into their own block.
    iterator.nextUntil(instruction -> !instruction.isArgument());
    iterator.previous();
    iterator.split(code);
    iterator.previous();

    // Now that the block is split there should not be any catch handlers in the block.
    assert !block.hasCatchHandlers();
    DexType javaLangSystemType = dexItemFactory.javaLangSystemType;
    DexType javaIoPrintStreamType = dexItemFactory.javaIoPrintStreamType;
    Value out =
        code.createValue(
            TypeElement.fromDexType(javaIoPrintStreamType, definitelyNotNull(), appView));

    DexProto proto = dexItemFactory.createProto(dexItemFactory.voidType, dexItemFactory.objectType);
    DexMethod print = dexItemFactory.createMethod(javaIoPrintStreamType, proto, "print");
    DexMethod printLn = dexItemFactory.createMethod(javaIoPrintStreamType, proto, "println");

    iterator.add(
        new StaticGet(
            out, dexItemFactory.createField(javaLangSystemType, javaIoPrintStreamType, "out")));

    Value value = addConstString(code, iterator, "INVOKE ");
    iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, value)));

    value = addConstString(code, iterator, method.getReference().qualifiedName());
    iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, value)));

    Value openParenthesis = addConstString(code, iterator, "(");
    Value comma = addConstString(code, iterator, ",");
    Value closeParenthesis = addConstString(code, iterator, ")");
    Value indent = addConstString(code, iterator, "  ");
    Value nul = addConstString(code, iterator, "(null)");
    Value primitive = addConstString(code, iterator, "(primitive)");
    Value empty = addConstString(code, iterator, "");

    iterator.add(new InvokeVirtual(printLn, null, ImmutableList.of(out, openParenthesis)));
    for (int i = 0; i < arguments.size(); i++) {
      iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, indent)));

      // Add a block for end-of-line printing.
      BasicBlock eol =
          BasicBlock.createGotoBlock(code.getNextBlockNumber(), position, code.metadata());
      code.blocks.add(eol);

      BasicBlock successor = block.unlinkSingleSuccessor();
      block.link(eol);
      eol.link(successor);

      Value argument = arguments.get(i);
      if (!argument.getType().isReferenceType()) {
        iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, primitive)));
      } else {
        // Insert "if (argument != null) ...".
        successor = block.unlinkSingleSuccessor();
        If theIf = new If(IfType.NE, argument);
        theIf.setPosition(position);
        BasicBlock ifBlock =
            BasicBlock.createIfBlock(code.getNextBlockNumber(), theIf, code.metadata());
        code.blocks.add(ifBlock);
        // Fallthrough block must be added right after the if.
        BasicBlock isNullBlock =
            BasicBlock.createGotoBlock(code.getNextBlockNumber(), position, code.metadata());
        code.blocks.add(isNullBlock);
        BasicBlock isNotNullBlock =
            BasicBlock.createGotoBlock(code.getNextBlockNumber(), position, code.metadata());
        code.blocks.add(isNotNullBlock);

        // Link the added blocks together.
        block.link(ifBlock);
        ifBlock.link(isNotNullBlock);
        ifBlock.link(isNullBlock);
        isNotNullBlock.link(successor);
        isNullBlock.link(successor);

        // Fill code into the blocks.
        iterator = isNullBlock.listIterator(code);
        iterator.setInsertionPosition(position);
        iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, nul)));
        iterator = isNotNullBlock.listIterator(code);
        iterator.setInsertionPosition(position);
        value = code.createValue(TypeElement.classClassType(appView, definitelyNotNull()));
        iterator.add(
            new InvokeVirtual(
                dexItemFactory.objectMembers.getClass, value, ImmutableList.of(arguments.get(i))));
        iterator.add(new InvokeVirtual(print, null, ImmutableList.of(out, value)));
      }

      iterator = eol.listIterator(code);
      iterator.setInsertionPosition(position);
      if (i == arguments.size() - 1) {
        iterator.add(new InvokeVirtual(printLn, null, ImmutableList.of(out, closeParenthesis)));
      } else {
        iterator.add(new InvokeVirtual(printLn, null, ImmutableList.of(out, comma)));
      }
      block = eol;
    }
    // When we fall out of the loop the iterator is in the last eol block.
    iterator.add(new InvokeVirtual(printLn, null, ImmutableList.of(out, empty)));
  }

  // The javac fix for JDK-8272564 has to be rewritten back to invoke-virtual on Object if the
  // method with an Object signature is not defined on the interface. See
  // https://bugs.openjdk.java.net/browse/JDK-8272564
  public static void rewriteJdk8272564Fix(IRCode code, ProgramMethod context, AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();
      if (instruction.isInvokeInterface()) {
        InvokeInterface invoke = instruction.asInvokeInterface();
        DexMethod method = invoke.getInvokedMethod();
        DexClass clazz = appView.definitionFor(method.getHolderType(), context);
        if (clazz == null || clazz.isInterface()) {
          DexMethod objectMember = dexItemFactory.objectMembers.matchingPublicObjectMember(method);
          // javac before JDK-8272564 would still use invoke interface if the method is defined
          // directly on the interface reference, so mimic that by not rewriting.
          if (objectMember != null && appView.definitionFor(method) == null) {
            it.replaceCurrentInstruction(
                new InvokeVirtual(objectMember, invoke.outValue(), invoke.arguments()));
          }
        }
      }
    }
  }
}
