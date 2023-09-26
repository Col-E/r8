// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;
import static com.android.tools.r8.ir.code.Opcodes.ADD;
import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_GET;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_LENGTH;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CMP;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DEX_ITEM_BASED_CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DIV;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INIT_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_OF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INT_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MUL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_FILLED;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.OR;
import static com.android.tools.r8.ir.code.Opcodes.REM;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.SHL;
import static com.android.tools.r8.ir.code.Opcodes.SHR;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STRING_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.SUB;
import static com.android.tools.r8.ir.code.Opcodes.THROW;
import static com.android.tools.r8.ir.code.Opcodes.USHR;
import static com.android.tools.r8.ir.code.Opcodes.XOR;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.DeterminismAnalysis;
import com.android.tools.r8.ir.analysis.InitializedClassesOnNormalExitAnalysis;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraintAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis.ClassInitializerSideEffect;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.StatefulObjectValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectStateAnalysis;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.DynamicTypeOptimization;
import com.android.tools.r8.ir.optimize.classinliner.analysis.ClassInlinerMethodConstraintAnalysis;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassificationAnalysis;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeAnalyzer;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.ir.optimize.info.initializer.NonTrivialInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.typechecks.CheckCastAndInstanceOfMethodSpecialization;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.kotlin.Kotlin.Intrinsics;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public class MethodOptimizationInfoCollector {

  private final AppView<AppInfoWithLiveness> appView;
  private final CheckCastAndInstanceOfMethodSpecialization
      checkCastAndInstanceOfMethodSpecialization;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public MethodOptimizationInfoCollector(
      AppView<AppInfoWithLiveness> appView, IRConverter converter) {
    this.appView = appView;
    this.checkCastAndInstanceOfMethodSpecialization =
        appView.options().isRelease()
            ? new CheckCastAndInstanceOfMethodSpecialization(appView, converter)
            : null;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  public void collectMethodOptimizationInfo(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      DynamicTypeOptimization dynamicTypeOptimization,
      InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos,
      MethodProcessor methodProcessor,
      Timing timing) {
    DexEncodedMethod definition = method.getDefinition();
    identifyBridgeInfo(definition, code, feedback, timing);
    analyzeReturns(code, feedback, methodProcessor, timing);
    if (options.enableClassInlining) {
      computeClassInlinerMethodConstraint(method, code, feedback, timing);
    }
    computeEnumUnboxerMethodClassification(method, code, feedback, methodProcessor, timing);
    computeSimpleInliningConstraint(method, code, feedback, timing);
    computeDynamicReturnType(dynamicTypeOptimization, feedback, method, code, timing);
    if (options.enableInitializedClassesAnalysis) {
      computeInitializedClassesOnNormalExit(feedback, definition, code, timing);
    }
    computeInstanceInitializerInfo(
        definition, code, feedback, instanceFieldInitializationInfos, timing);
    computeMayHaveSideEffects(feedback, definition, code, timing);
    computeReturnValueOnlyDependsOnArguments(feedback, definition, code, timing);
    BitSet nonNullParamOrThrow = computeNonNullParamOrThrow(feedback, definition, code, timing);
    computeNonNullParamOnNormalExits(feedback, code, nonNullParamOrThrow, timing);
    computeParametersWithBitwiseOperations(method, code, feedback, timing);
    computeUnusedArguments(method, code, feedback, timing);
  }

  private void identifyBridgeInfo(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify bridge info");
    feedback.setBridgeInfo(method, BridgeAnalyzer.analyzeMethod(method, code));
    timing.end();
  }

  private void analyzeReturns(
      IRCode code, OptimizationFeedback feedback, MethodProcessor methodProcessor, Timing timing) {
    timing.begin("Identify returns argument");
    analyzeReturns(code, feedback, methodProcessor);
    timing.end();
  }

  private void analyzeReturns(
      IRCode code, OptimizationFeedback feedback, MethodProcessor methodProcessor) {
    ProgramMethod context = code.context();
    DexEncodedMethod method = context.getDefinition();
    List<BasicBlock> normalExits = code.computeNormalExitBlocks();
    if (normalExits.isEmpty()) {
      feedback.methodNeverReturnsNormally(context);
      return;
    }
    Return firstExit = normalExits.get(0).exit().asReturn();
    if (firstExit.isReturnVoid()) {
      return;
    }
    Value returnValue = firstExit.returnValue();
    for (int i = 1; i < normalExits.size(); i++) {
      Return exit = normalExits.get(i).exit().asReturn();
      Value value = exit.returnValue();
      if (value != returnValue) {
        returnValue = null;
      }
    }
    if (returnValue != null) {
      Value aliasedValue = returnValue.getAliasedValue();
      if (!aliasedValue.isPhi()) {
        Instruction definition = aliasedValue.definition;
        if (definition.isArgument()) {
          feedback.methodReturnsArgument(method, definition.asArgument().getIndex());
        }
        AbstractValue abstractReturnValue = definition.getAbstractValue(appView, context);
        if (abstractReturnValue.isNonTrivial()) {
          feedback.methodReturnsAbstractValue(method, appView, abstractReturnValue);
          if (checkCastAndInstanceOfMethodSpecialization != null) {
            checkCastAndInstanceOfMethodSpecialization.addCandidateForOptimization(
                context, abstractReturnValue, methodProcessor);
          }
        } else if (returnValue.getType().isReferenceType()) {
          // TODO(b/204159267): Move this logic into Instruction#getAbstractValue in NewInstance.
          ObjectState objectState =
              ObjectStateAnalysis.computeObjectState(aliasedValue, appView, context);
          // TODO(b/204272377): Avoid wrapping and unwrapping the object state.
          feedback.methodReturnsAbstractValue(
              method, appView, StatefulObjectValue.create(objectState));
        }
      }
    }
  }

  private void computeInstanceInitializerInfo(
      DexEncodedMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos,
      Timing timing) {
    timing.begin("Compute instance initializer info");
    computeInstanceInitializerInfo(method, code, feedback, instanceFieldInitializationInfos);
    timing.end();
  }

  private void computeInstanceInitializerInfo(
      DexEncodedMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos) {
    assert !appView.appInfo().isPinned(method);

    if (!method.isInstanceInitializer()) {
      return;
    }

    assert instanceFieldInitializationInfos != null;

    if (method.accessFlags.isNative()) {
      return;
    }

    if (appView.appInfo().mayHaveSideEffects.containsKey(method.getReference())) {
      return;
    }

    NonTrivialInstanceInitializerInfo.Builder builder =
        NonTrivialInstanceInitializerInfo.builder(instanceFieldInitializationInfos);
    InstanceInitializerInfo instanceInitializerInfo = analyzeInstanceInitializer(code, builder);
    feedback.setInstanceInitializerInfoCollection(
        method, InstanceInitializerInfoCollection.of(instanceInitializerInfo));
  }

  @SuppressWarnings("ReferenceEquality")
  // This method defines trivial instance initializer as follows:
  //
  // ** The holder class must not define a finalize method.
  //
  // ** The initializer may call the initializer of the base class, which
  //    itself must be trivial.
  //
  // ** java.lang.Object.<init>() is considered trivial.
  //
  // ** all arguments passed to a super-class initializer must be non-throwing
  //    constants or arguments.
  //
  // ** Assigns arguments or non-throwing constants to fields of this class.
  //
  // (Note that this initializer does not have to have zero arguments.)
  private InstanceInitializerInfo analyzeInstanceInitializer(
      IRCode code, NonTrivialInstanceInitializerInfo.Builder builder) {
    ProgramMethod context = code.context();
    if (context.getHolder().definesFinalizer(options.itemFactory)) {
      // Defining a finalize method can observe the side-effect of Object.<init> GC registration.
      return null;
    }

    AliasedValueConfiguration aliasesThroughAssumeAndCheckCasts =
        AssumeAndCheckCastAliasedValueConfiguration.getInstance();
    Value receiver = code.getThis();
    boolean hasCatchHandler = false;
    for (BasicBlock block : code.blocks) {
      if (block.hasCatchHandlers()) {
        hasCatchHandler = true;
      }

      for (Instruction instruction : block.getInstructions()) {
        switch (instruction.opcode()) {
          case ARGUMENT:
          case ASSUME:
          case CONST_NUMBER:
          case GOTO:
          case RETURN:
            break;

          case IF:
          case INT_SWITCH:
          case STRING_SWITCH:
            builder.setInstanceFieldInitializationMayDependOnEnvironment();
            break;

          case ADD:
          case AND:
          case ARRAY_LENGTH:
          case CHECK_CAST:
          case CMP:
          case CONST_CLASS:
          case CONST_STRING:
          case DEX_ITEM_BASED_CONST_STRING:
          case DIV:
          case INIT_CLASS:
          case INSTANCE_OF:
          case MUL:
          case NEW_ARRAY_EMPTY:
          case OR:
          case REM:
          case SHL:
          case SHR:
          case SUB:
          case THROW:
          case USHR:
          case XOR:
            // These instructions types may raise an exception, which is a side effect. None of the
            // instructions can trigger class initialization side effects, hence it is not necessary
            // to mark all fields as potentially being read. Also, none of the instruction types
            // can cause the receiver to escape.
            if (instruction.instructionMayHaveSideEffects(appView, context)) {
              builder.setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
            }
            break;

          case INSTANCE_GET:
          case STATIC_GET:
            {
              FieldInstruction fieldGet = instruction.asFieldInstruction();
              DexClassAndField field =
                  appView.appInfo().resolveField(fieldGet.getField()).getResolutionPair();
              if (field == null) {
                return null;
              }
              builder.markFieldAsRead(field);
              if (fieldGet.instructionMayHaveSideEffects(appView, context)) {
                builder.setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
                if (fieldGet.isStaticGet()) {
                  // It could trigger a class initializer.
                  builder.markAllFieldsAsRead();
                }
              }
            }
            break;

          case INSTANCE_PUT:
            {
              InstancePut instancePut = instruction.asInstancePut();
              DexEncodedField field =
                  appView.appInfo().resolveField(instancePut.getField()).getResolvedField();
              if (field == null) {
                return null;
              }
              Value object =
                  instancePut.object().getAliasedValue(aliasesThroughAssumeAndCheckCasts);
              if (object != receiver || instancePut.instructionInstanceCanThrow(appView, context)) {
                builder.setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
              }

              Value value = instancePut.value().getAliasedValue(aliasesThroughAssumeAndCheckCasts);
              // TODO(b/142762134): Replace the use of onlyDependsOnArgument() by
              //  ValueMayDependOnEnvironmentAnalysis.
              if (!value.onlyDependsOnArgument()) {
                builder.setInstanceFieldInitializationMayDependOnEnvironment();
              }
              if (couldBeReceiverValue(value, receiver, aliasesThroughAssumeAndCheckCasts)) {
                builder.setReceiverMayEscapeOutsideConstructorChain();
              }
            }
            break;

          case INVOKE_DIRECT:
            {
              InvokeDirect invoke = instruction.asInvokeDirect();
              DexMethod invokedMethod = invoke.getInvokedMethod();
              DexClass holder = appView.definitionForHolder(invokedMethod);
              DexEncodedMethod singleTarget = invokedMethod.lookupOnClass(holder);
              if (singleTarget == null) {
                return null;
              }
              if (singleTarget.isInstanceInitializer()
                  && invoke.getReceiver().getAliasedValue() == receiver) {
                if (builder.hasParent() && builder.getParent() != singleTarget.getReference()) {
                  return null;
                }
                // java.lang.Enum.<init>() and java.lang.Object.<init>() are considered trivial.
                if (invokedMethod == dexItemFactory.enumMembers.constructor
                    || invokedMethod == dexItemFactory.objectMembers.constructor) {
                  builder.setParent(invokedMethod);
                  break;
                }
                builder.merge(
                    singleTarget.getOptimizationInfo().getInstanceInitializerInfo(invoke));
                for (int i = 1; i < invoke.arguments().size(); i++) {
                  Value argument =
                      invoke.arguments().get(i).getAliasedValue(aliasesThroughAssumeAndCheckCasts);
                  if (couldBeReceiverValue(argument, receiver, aliasesThroughAssumeAndCheckCasts)) {
                    // In the analysis of the parent constructor, we don't consider the non-receiver
                    // arguments as being aliases of the receiver. Therefore, we explicitly mark
                    // that the receiver escapes from this constructor.
                    builder.setReceiverMayEscapeOutsideConstructorChain();
                  }
                  if (!argument.onlyDependsOnArgument()) {
                    // If the parent constructor assigns this argument into a field, then the value
                    // of the field may depend on the environment.
                    builder.setInstanceFieldInitializationMayDependOnEnvironment();
                  }
                }
                builder.setParent(invokedMethod);
              } else {
                builder
                    .markAllFieldsAsRead()
                    .setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
                for (Value inValue : invoke.inValues()) {
                  if (couldBeReceiverValue(inValue, receiver, aliasesThroughAssumeAndCheckCasts)) {
                    builder.setReceiverMayEscapeOutsideConstructorChain();
                    break;
                  }
                }
              }
            }
            break;

          case NEW_ARRAY_FILLED:
            {
              NewArrayFilled invoke = instruction.asNewArrayFilled();
              if (invoke.instructionMayHaveSideEffects(appView, context)) {
                builder.setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
              }
              for (Value argument : invoke.arguments()) {
                if (couldBeReceiverValue(argument, receiver, aliasesThroughAssumeAndCheckCasts)) {
                  builder.setReceiverMayEscapeOutsideConstructorChain();
                  break;
                }
              }
            }
            break;

          case INVOKE_INTERFACE:
          case INVOKE_STATIC:
          case INVOKE_VIRTUAL:
            {
              InvokeMethod invoke = instruction.asInvokeMethod();
              builder
                  .markAllFieldsAsRead()
                  .setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
              for (Value argument : invoke.arguments()) {
                if (couldBeReceiverValue(argument, receiver, aliasesThroughAssumeAndCheckCasts)) {
                  builder.setReceiverMayEscapeOutsideConstructorChain();
                  break;
                }
              }
            }
            break;

          case NEW_INSTANCE:
            {
              NewInstance newInstance = instruction.asNewInstance();
              if (newInstance.instructionMayHaveSideEffects(appView, context)) {
                // It could trigger a class initializer.
                builder
                    .markAllFieldsAsRead()
                    .setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
              }
            }
            break;

          case ARRAY_GET:
            {
              builder.setMayHaveOtherSideEffectsThanInstanceFieldAssignments();
              break;
            }

          default:
            builder
                .markAllFieldsAsRead()
                .setInstanceFieldInitializationMayDependOnEnvironment()
                .setMayHaveOtherSideEffectsThanInstanceFieldAssignments()
                .setReceiverMayEscapeOutsideConstructorChain();
            break;
        }
      }
    }

    // In presence of exceptional control flow, the assignments to the instance fields could depend
    // on the environment, if there is an instruction that could throw.
    //
    // Example:
    //   void <init>() {
    //     try {
    //       throwIfTrue(Environment.STATIC_FIELD);
    //     } catch (Exception e) {
    //       this.f = 42;
    //     }
    //   }
    if (hasCatchHandler && builder.mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
      builder.setInstanceFieldInitializationMayDependOnEnvironment();
    }

    return builder.build();
  }

  private static boolean couldBeReceiverValue(
      Value value, Value receiver, AliasedValueConfiguration aliasing) {
    if (value.isPhi() && receiver.hasPhiUsers()) {
      // Conservatively assume that the receiver might be an input dependency of the phi value.
      return true;
    }
    if (value.getAliasedValue(aliasing) == receiver) {
      return true;
    }
    return false;
  }

  /**
   * Returns true if the given code unconditionally triggers an expected effect before anything
   * else, false otherwise.
   *
   * <p>Note: we do not track phis so we may return false negative. This is a conservative approach.
   */
  private static boolean alwaysTriggerExpectedEffectBeforeAnythingElse(
      IRCode code, BiFunction<Instruction, InstructionIterator, InstructionEffect> function) {
    final int color = code.reserveMarkingColor();
    try {
      ArrayDeque<BasicBlock> worklist = new ArrayDeque<>();
      final BasicBlock entry = code.entryBlock();
      worklist.add(entry);
      entry.mark(color);

      while (!worklist.isEmpty()) {
        BasicBlock currentBlock = worklist.poll();
        assert currentBlock.isMarked(color);

        InstructionEffect result = InstructionEffect.NO_EFFECT;
        InstructionIterator it = currentBlock.iterator();
        while (result == InstructionEffect.NO_EFFECT && it.hasNext()) {
          result = function.apply(it.next(), it);
        }
        if (result == InstructionEffect.OTHER_EFFECT) {
          // We found an instruction that is causing an unexpected side effect.
          return false;
        } else if (result == InstructionEffect.DESIRED_EFFECT) {
          // The current path is causing the expected effect. No need to go deeper in this path,
          // go to the next block in the work list.
          continue;
        } else if (result == InstructionEffect.CONDITIONAL_EFFECT) {
          assert !currentBlock.getNormalSuccessors().isEmpty();
          Instruction lastInstruction = currentBlock.getInstructions().getLast();
          assert lastInstruction.isIf();
          // The current path is checking if the value of interest is null. Go deeper into the path
          // that corresponds to the null value.
          BasicBlock targetIfReceiverIsNull = lastInstruction.asIf().targetFromCondition(0);
          if (!targetIfReceiverIsNull.isMarked(color)) {
            worklist.add(targetIfReceiverIsNull);
            targetIfReceiverIsNull.mark(color);
          }
        } else {
          assert result == InstructionEffect.NO_EFFECT;
          // The block did not cause any particular effect.
          if (currentBlock.getNormalSuccessors().isEmpty()) {
            // This is the end of the current non-exceptional path and we did not find any expected
            // effect. It means there is at least one path where the expected effect does not
            // happen.
            Instruction lastInstruction = currentBlock.getInstructions().getLast();
            assert lastInstruction.isReturn() || lastInstruction.isThrow();
            return false;
          } else {
            // Look into successors
            for (BasicBlock successor : currentBlock.getSuccessors()) {
              if (!successor.isMarked(color)) {
                worklist.add(successor);
                successor.mark(color);
              }
            }
          }
        }
      }
      // If we reach this point, we checked that the expected effect happens in every possible path.
      return true;
    } finally {
      code.returnMarkingColor(color);
    }
  }

  /**
   * Returns true if the given code unconditionally throws if value is null before any other side
   * effect instruction.
   *
   * <p>Note: we do not track phis so we may return false negative. This is a conservative approach.
   */
  private boolean checksNullBeforeSideEffect(IRCode code, Value value) {
    return alwaysTriggerExpectedEffectBeforeAnythingElse(
        code,
        (instr, it) -> {
          BasicBlock currentBlock = instr.getBlock();
          // If the code explicitly checks the nullability of the value, we should visit the next
          // block that corresponds to the null value where NPE semantic could be preserved.
          if (!currentBlock.hasCatchHandlers() && isNullCheck(instr, value)) {
            return InstructionEffect.CONDITIONAL_EFFECT;
          }
          if (instr.isInvokeStatic()) {
            InvokeStatic invoke = instr.asInvokeStatic();
            if (isKotlinCheckParameterIsNotNull(appView, invoke, value)) {
              return InstructionEffect.DESIRED_EFFECT;
            }
            if (isKotlinThrowParameterIsNullException(appView, invoke)) {
              // Kotlin specific way of throwing NPE. Combined with the above CONDITIONAL_EFFECT,
              // the code acts as a null check for the given value.
              for (BasicBlock predecessor : currentBlock.getPredecessors()) {
                if (isNullCheck(predecessor.exit(), value)) {
                  return InstructionEffect.DESIRED_EFFECT;
                }
              }
              // Hitting here means that this call might be used for other parameters. If we don't
              // bail out, it will be regarded as side effects for the current value.
              return InstructionEffect.NO_EFFECT;
            }
          }
          if (isInstantiationOfNullPointerException(instr, it, appView.dexItemFactory())) {
            it.next(); // Skip call to NullPointerException.<init>.
            return InstructionEffect.NO_EFFECT;
          } else if (instr.throwsNpeIfValueIsNull(value, appView, code.context())) {
            // In order to preserve NPE semantic, the exception must not be caught by any handler.
            // Therefore, we must ignore this instruction if it is covered by a catch handler.
            // Note: this is a conservative approach where we consider that any catch handler could
            // catch the exception, even if it cannot catch a NullPointerException.
            if (!currentBlock.hasCatchHandlers()) {
              // We found a NPE check on the value.
              return InstructionEffect.DESIRED_EFFECT;
            }
          } else if (instr.instructionMayHaveSideEffects(appView, code.context())) {
            // If the current instruction is const-string, this could load the parameter name.
            // Just make sure it is indeed not throwing.
            if (instr.isConstString()
                && !instr.instructionInstanceCanThrow(appView, code.context())) {
              return InstructionEffect.NO_EFFECT;
            }
            // We found a side effect before a NPE check.
            return InstructionEffect.OTHER_EFFECT;
          }
          return InstructionEffect.NO_EFFECT;
        });
  }

  /**
   * An enum used to classify instructions according to a particular effect that they produce.
   *
   * The "effect" of an instruction can be seen as a program state change (or semantic change) at
   * runtime execution. For example, an instruction could cause the initialization of a class,
   * change the value of a field, ... while other instructions do not.
   *
   * This classification also depends on the type of analysis that is using it. For instance, an
   * analysis can look for instructions that cause class initialization while another look for
   * instructions that check nullness of a particular object.
   *
   * On the other hand, some instructions may provide a non desired effect which is a signal for
   * the analysis to stop.
   */
  private enum InstructionEffect {
    DESIRED_EFFECT,
    CONDITIONAL_EFFECT,
    OTHER_EFFECT,
    NO_EFFECT
  }

  // Note that this method may have false positives, since the application could in principle
  // declare a method called checkParameterIsNotNull(parameter, message) in a package that starts
  // with "kotlin".
  private static boolean isKotlinCheckParameterIsNotNull(
      AppView<?> appView, InvokeStatic invoke, Value value) {
    if (appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      return false;
    }
    // We need to ignore the holder, since Kotlin adds different versions of null-check machinery,
    // e.g., kotlin.collections.ArraysKt___ArraysKt... or kotlin.jvm.internal.ArrayIteratorKt...
    Intrinsics intrinsics = appView.dexItemFactory().kotlin.intrinsics;
    DexMethod originalInvokedMethod =
        appView.graphLens().getOriginalMethodSignature(invoke.getInvokedMethod());
    boolean isCheckNotNullMethod =
        originalInvokedMethod.match(intrinsics.checkParameterIsNotNull)
            || originalInvokedMethod.match(intrinsics.checkNotNullParameter);
    return isCheckNotNullMethod
        && invoke.getFirstArgument() == value
        && originalInvokedMethod.getHolderType().getPackageDescriptor().startsWith(Kotlin.NAME);
  }

  // Note that this method may have false positives, since the application could in principle
  // declare a method called throwParameterIsNullException(parameterName) in a package that starts
  // with "kotlin".
  private static boolean isKotlinThrowParameterIsNullException(
      AppView<?> appView, InvokeStatic invoke) {
    if (appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      return false;
    }
    // We need to ignore the holder, since Kotlin adds different versions of null-check machinery,
    // e.g., kotlin.collections.ArraysKt___ArraysKt... or kotlin.jvm.internal.ArrayIteratorKt...
    Intrinsics intrinsics = appView.dexItemFactory().kotlin.intrinsics;
    DexMethod originalInvokedMethod =
        appView.graphLens().getOriginalMethodSignature(invoke.getInvokedMethod());
    return (originalInvokedMethod.match(intrinsics.throwParameterIsNullException)
            || originalInvokedMethod.match(intrinsics.throwParameterIsNullNPE))
        && originalInvokedMethod.getHolderType().getPackageDescriptor().startsWith(Kotlin.NAME);
  }

  private static boolean isNullCheck(Instruction instr, Value value) {
    return instr.isIf()
        && instr.asIf().isZeroTest()
        && instr.inValues().get(0).equals(value)
        && (instr.asIf().getType() == IfType.EQ || instr.asIf().getType() == IfType.NE);
  }

  /**
   * Returns true if the given instruction is {@code v <- new-instance NullPointerException}, and
   * the next instruction is {@code invoke-direct v, NullPointerException.<init>()}.
   */
  @SuppressWarnings("ReferenceEquality")
  private static boolean isInstantiationOfNullPointerException(
      Instruction instruction, InstructionIterator it, DexItemFactory dexItemFactory) {
    if (!instruction.isNewInstance()
        || instruction.asNewInstance().clazz != dexItemFactory.npeType) {
      return false;
    }
    Instruction next = it.peekNext();
    if (next == null
        || !next.isInvokeDirect()
        || next.asInvokeDirect().getInvokedMethod() != dexItemFactory.npeMethods.init) {
      return false;
    }
    return true;
  }

  private void computeClassInlinerMethodConstraint(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      Timing timing) {
    timing.begin("Compute class inlining constraint");
    ClassInlinerMethodConstraint classInlinerMethodConstraint =
        ClassInlinerMethodConstraintAnalysis.analyze(appView, method, code, timing);
    feedback.setClassInlinerMethodConstraint(method, classInlinerMethodConstraint);
    timing.end();
  }

  private void computeEnumUnboxerMethodClassification(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      Timing timing) {
    timing.begin("Compute enum unboxer method classification");
    computeEnumUnboxerMethodClassification(method, code, feedback, methodProcessor);
    timing.end();
  }

  private void computeEnumUnboxerMethodClassification(
      ProgramMethod method,
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor) {
    if (appView.hasUnboxedEnums()) {
      if (appView.unboxedEnums().isEmpty()) {
        feedback.unsetEnumUnboxerMethodClassification(method);
      } else {
        assert verifyEnumUnboxerMethodClassificationCorrect(method, code, methodProcessor);
      }
    } else {
      EnumUnboxerMethodClassification enumUnboxerMethodClassification =
          EnumUnboxerMethodClassificationAnalysis.analyze(appView, method, code, methodProcessor);
      feedback.setEnumUnboxerMethodClassification(method, enumUnboxerMethodClassification);
    }
  }

  private boolean verifyEnumUnboxerMethodClassificationCorrect(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor) {
    EnumUnboxerMethodClassification existingClassification =
        method.getOptimizationInfo().getEnumUnboxerMethodClassification();
    if (existingClassification.isCheckNotNullClassification()) {
      EnumUnboxerMethodClassification computedClassification =
          EnumUnboxerMethodClassificationAnalysis.analyze(appView, method, code, methodProcessor);
      assert computedClassification.isCheckNotNullClassification();
      assert computedClassification.asCheckNotNullClassification().getArgumentIndex()
          == existingClassification.asCheckNotNullClassification().getArgumentIndex();
    } else {
      assert existingClassification.isUnknownClassification();
    }
    return true;
  }

  private void computeSimpleInliningConstraint(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    if (appView.options().enableSimpleInliningConstraints) {
      timing.begin("Compute simple inlining constraint");
      computeSimpleInliningConstraint(method, code, feedback);
      timing.end();
    }
  }

  private void computeSimpleInliningConstraint(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback) {
    feedback.setSimpleInliningConstraint(
        method, new SimpleInliningConstraintAnalysis(appView, method).analyzeCode(code));
  }

  private void computeDynamicReturnType(
      DynamicTypeOptimization dynamicTypeOptimization,
      OptimizationFeedback feedback,
      ProgramMethod method,
      IRCode code,
      Timing timing) {
    timing.begin("Compute dynamic return type");
    computeDynamicReturnType(dynamicTypeOptimization, feedback, method, code);
    timing.end();
  }

  private void computeDynamicReturnType(
      DynamicTypeOptimization dynamicTypeOptimization,
      OptimizationFeedback feedback,
      ProgramMethod method,
      IRCode code) {
    if (dynamicTypeOptimization == null) {
      return;
    }
    if (!method.getReturnType().isReferenceType()) {
      return;
    }

    DynamicType dynamicReturnType = dynamicTypeOptimization.computeDynamicReturnType(method, code);
    if (dynamicReturnType.isBottom() || dynamicReturnType.isUnknown()) {
      return;
    }

    if (dynamicReturnType.isNullType()) {
      feedback.methodReturnsAbstractValue(
          method.getDefinition(), appView, appView.abstractValueFactory().createNullValue());
      feedback.setDynamicReturnType(method, appView, dynamicReturnType);
      return;
    }

    if (dynamicReturnType.isNotNullType()) {
      feedback.setDynamicReturnType(method, appView, dynamicReturnType);
      return;
    }

    // If the dynamic return type is not more precise than the static return type there is no
    // need to record it.
    DynamicTypeWithUpperBound staticReturnType = method.getReturnType().toDynamicType(appView);
    if (dynamicReturnType
        .asDynamicTypeWithUpperBound()
        .strictlyLessThan(staticReturnType, appView)) {
      feedback.setDynamicReturnType(method, appView, dynamicReturnType);
    }
  }

  private void computeInitializedClassesOnNormalExit(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code, Timing timing) {
    timing.begin("Compute initialized classes on normal exits");
    computeInitializedClassesOnNormalExit(feedback, method, code);
    timing.end();
  }

  private void computeInitializedClassesOnNormalExit(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (options.enableInitializedClassesAnalysis && appView.appInfo().hasLiveness()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      Set<DexType> initializedClasses =
          InitializedClassesOnNormalExitAnalysis.computeInitializedClassesOnNormalExit(
              appViewWithLiveness, code);
      if (initializedClasses != null && !initializedClasses.isEmpty()) {
        feedback.methodInitializesClassesOnNormalExit(method, initializedClasses);
      }
    }
  }

  private void computeMayHaveSideEffects(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code, Timing timing) {
    timing.begin("Compute may have side effects");
    computeMayHaveSideEffects(feedback, method, code);
    timing.end();
  }

  private void computeMayHaveSideEffects(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    // If the method is native, we don't know what could happen.
    assert !method.accessFlags.isNative();
    if (!options.enableSideEffectAnalysis) {
      return;
    }
    if (appView.appInfo().mayHaveSideEffects.containsKey(method.getReference())) {
      return;
    }
    ProgramMethod context = code.context();
    if (method.isClassInitializer()) {
      // For class initializers, we also wish to compute if the class initializer has observable
      // side effects.
      ClassInitializerSideEffect classInitializerSideEffect =
          ClassInitializerSideEffectAnalysis.classInitializerCanBePostponed(appView, code);
      if (classInitializerSideEffect.isNone()) {
        feedback.methodMayNotHaveSideEffects(method);
        feedback.classInitializerMayBePostponed(method);
      } else if (classInitializerSideEffect.canBePostponed()) {
        feedback.classInitializerMayBePostponed(method);
      } else {
        assert options.debug
                || appView
                    .getSyntheticItems()
                    .verifySyntheticLambdaProperty(
                        context.getHolder(),
                        lambdaClass ->
                            appView.appInfo().hasPinnedInstanceInitializer(lambdaClass.getType()),
                        nonLambdaClass -> true)
            : "Unexpected observable side effects from lambda `" + context.toSourceString() + "`";
      }
      return;
    }
    boolean mayHaveSideEffects;
    if (method.isSynchronized()) {
      // If the method is synchronized then it acquires a lock.
      mayHaveSideEffects = true;
    } else if (method.isInstanceInitializer() && hasNonTrivialFinalizeMethod(context.getHolder())) {
      // If a class T overrides java.lang.Object.finalize(), then treat the constructor as having
      // side effects. This ensures that we won't remove instructions on the form `new-instance
      // {v0}, T`.
      mayHaveSideEffects = true;
    } else {
      mayHaveSideEffects = false;
      // Otherwise, check if there is an instruction that has side effects.
      for (Instruction instruction : code.instructions()) {
        if (instruction.isInvokeConstructor(appView.dexItemFactory())
            && instruction
                .asInvokeDirect()
                .getReceiver()
                .getAliasedValue()
                .isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
          if (instruction.instructionMayHaveSideEffects(
              appView, context, SideEffectAssumption.IGNORE_RECEIVER_FIELD_ASSIGNMENTS)) {
            mayHaveSideEffects = true;
            break;
          }
        } else if (instruction.instructionMayHaveSideEffects(appView, context)) {
          mayHaveSideEffects = true;
          break;
        }
      }
    }
    if (!mayHaveSideEffects) {
      feedback.methodMayNotHaveSideEffects(method);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  // Returns true if the given class overrides the method `void java.lang.Object.finalize()`.
  private boolean hasNonTrivialFinalizeMethod(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      return false;
    }
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    MethodResolutionResult resolutionResult =
        appView
            .appInfo()
            .resolveMethodOnClassLegacy(clazz, appView.dexItemFactory().objectMembers.finalize);
    DexEncodedMethod target = resolutionResult.getSingleTarget();
    return target != null
        && target.getReference() != dexItemFactory.enumMembers.finalize
        && target.getReference() != dexItemFactory.objectMembers.finalize;
  }

  private void computeReturnValueOnlyDependsOnArguments(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code, Timing timing) {
    timing.begin("Return value only depends on argument");
    computeReturnValueOnlyDependsOnArguments(feedback, method, code);
    timing.end();
  }

  private void computeReturnValueOnlyDependsOnArguments(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (!options.enableDeterminismAnalysis) {
      return;
    }
    boolean returnValueOnlyDependsOnArguments =
        DeterminismAnalysis.returnValueOnlyDependsOnArguments(appView.withLiveness(), code);
    if (returnValueOnlyDependsOnArguments) {
      feedback.methodReturnValueOnlyDependsOnArguments(method);
    }
  }

  private BitSet computeNonNullParamOrThrow(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code, Timing timing) {
    timing.begin("Compute non-null-param-or-throw");
    BitSet nonNullParamOrThrow = computeNonNullParamOrThrow(feedback, method, code);
    timing.end();
    return nonNullParamOrThrow;
  }

  // Track usage of parameters and compute their nullability and possibility of NPE.
  private BitSet computeNonNullParamOrThrow(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (method.getOptimizationInfo().getNonNullParamOrThrow() != null) {
      return null;
    }
    List<Value> arguments = code.collectArguments();
    BitSet paramsCheckedForNull = new BitSet();
    for (int index = method.getFirstNonReceiverArgumentIndex(); index < arguments.size(); index++) {
      Value argument = arguments.get(index);
      // This handles cases where the parameter is checked via Kotlin Intrinsics:
      //
      //   kotlin.jvm.internal.Intrinsics.checkParameterIsNotNull(param, message)
      //
      // or its inlined version:
      //
      //   if (param != null) return;
      //   invoke-static throwParameterIsNullException(msg)
      //
      // or some other variants, e.g., throw null or NPE after the direct null check.
      if (argument.isUsed() && checksNullBeforeSideEffect(code, argument)) {
        paramsCheckedForNull.set(index);
      }
    }
    if (!paramsCheckedForNull.isEmpty()) {
      feedback.setNonNullParamOrThrow(method, paramsCheckedForNull);
      return paramsCheckedForNull;
    }
    return null;
  }

  private void computeNonNullParamOnNormalExits(
      OptimizationFeedback feedback, IRCode code, BitSet nonNullParamOrThrow, Timing timing) {
    timing.begin("Compute non-null-param-on-normal-exits");
    computeNonNullParamOnNormalExits(feedback, code, nonNullParamOrThrow);
    timing.end();
  }

  private void computeNonNullParamOnNormalExits(
      OptimizationFeedback feedback, IRCode code, BitSet nonNullParamOrThrow) {
    Set<BasicBlock> normalExits = Sets.newIdentityHashSet();
    normalExits.addAll(code.computeNormalExitBlocks());
    DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
    List<Value> arguments = code.collectArguments();
    BitSet facts = new BitSet();
    if (nonNullParamOrThrow != null) {
      facts.or(nonNullParamOrThrow);
    }
    for (int index = code.context().getDefinition().getFirstNonReceiverArgumentIndex();
        index < arguments.size();
        index++) {
      if (facts.get(index)) {
        continue;
      }
      Value argument = arguments.get(index);
      if (argument.getType().isReferenceType()
          && isNonNullOnNormalExit(code, argument, dominatorTree, normalExits)) {
        facts.set(index);
      }
    }
    if (!facts.isEmpty()) {
      feedback.setNonNullParamOnNormalExits(code.method(), facts);
    }
  }

  private boolean isNonNullOnNormalExit(
      IRCode code, Value value, DominatorTree dominatorTree, Set<BasicBlock> normalExits) {
    assert value.getType().isReferenceType();

    // The receiver is always non-null on normal exits.
    if (value.isThis()) {
      return true;
    }

    // Collect basic blocks that check nullability of the parameter.
    Set<BasicBlock> nullCheckedBlocks = Sets.newIdentityHashSet();
    for (Instruction user : value.aliasedUsers()) {
      if (user.isAssumeWithNonNullAssumption()) {
        // We don't allow assume instructions after throwing instructions, thus this block is either
        // non-throwing or the assume instruction is before the throwing instruction.
        assert !user.getBlock().hasCatchHandlers()
            || user.getBlock().getInstructions().stream()
                    .filter(
                        instruction -> instruction == user || instruction.instructionTypeCanThrow())
                    .findFirst()
                    .get()
                == user;
        nullCheckedBlocks.add(user.getBlock());
        continue;
      }

      if (user.throwsNpeIfValueIsNull(value, appView, code.context())) {
        if (user.getBlock().hasCatchHandlers()) {
          nullCheckedBlocks.addAll(user.getBlock().getNormalSuccessors());
        } else {
          nullCheckedBlocks.add(user.getBlock());
        }
        continue;
      }

      if (user.isIf()
          && user.asIf().isZeroTest()
          && (user.asIf().getType() == IfType.EQ || user.asIf().getType() == IfType.NE)) {
        nullCheckedBlocks.add(user.asIf().targetFromNonNullObject());
      }
    }

    if (nullCheckedBlocks.isEmpty()) {
      return false;
    }

    for (BasicBlock normalExit : normalExits) {
      if (!isNormalExitDominated(normalExit, code, dominatorTree, nullCheckedBlocks)) {
        return false;
      }
    }
    return true;
  }

  private boolean isNormalExitDominated(
      BasicBlock normalExit,
      IRCode code,
      DominatorTree dominatorTree,
      Set<BasicBlock> nullCheckedBlocks) {
    // Each normal exit should be...
    for (BasicBlock nullCheckedBlock : nullCheckedBlocks) {
      // A) ...directly dominated by any null-checked block.
      if (dominatorTree.dominatedBy(normalExit, nullCheckedBlock)) {
        return true;
      }
    }
    // B) ...or indirectly dominated by null-checked blocks.
    // Although the normal exit is not dominated by any of null-checked blocks (because of other
    // paths to the exit), it could be still the case that all possible paths to that exit should
    // pass some of null-checked blocks.
    Set<BasicBlock> visited = Sets.newIdentityHashSet();
    // Initial fan-out of predecessors.
    Deque<BasicBlock> uncoveredPaths = new ArrayDeque<>(normalExit.getPredecessors());
    while (!uncoveredPaths.isEmpty()) {
      BasicBlock uncoveredPath = uncoveredPaths.poll();
      // Stop traversing upwards if we hit the entry block: if the entry block has an non-null,
      // this case should be handled already by A) because the entry block surely dominates all
      // normal exits.
      if (uncoveredPath == code.entryBlock()) {
        return false;
      }
      // Make sure we're not visiting the same block over and over again.
      if (!visited.add(uncoveredPath)) {
        // But, if that block is the last one in the queue, the normal exit is not fully covered.
        if (uncoveredPaths.isEmpty()) {
          return false;
        } else {
          continue;
        }
      }
      boolean pathCovered = false;
      for (BasicBlock nullCheckedBlock : nullCheckedBlocks) {
        if (dominatorTree.dominatedBy(uncoveredPath, nullCheckedBlock)) {
          pathCovered = true;
          break;
        }
      }
      if (!pathCovered) {
        // Fan out predecessors one more level.
        // Note that remaining, unmatched null-checked blocks should cover newly added paths.
        uncoveredPaths.addAll(uncoveredPath.getPredecessors());
      }
    }
    // Reaching here means that every path to the given normal exit is covered by the set of
    // null-checked blocks.
    assert uncoveredPaths.isEmpty();
    return true;
  }

  private void computeParametersWithBitwiseOperations(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Compute parameters with bitwise operations");
    computeParametersWithBitwiseOperations(method, code, feedback);
    timing.end();
  }

  private void computeParametersWithBitwiseOperations(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback) {
    BitSet parametersWithBitwiseOperations = new BitSet(method.getParameters().size());
    InstructionIterator instructionIterator = code.entryBlock().iterator();
    Argument argument = instructionIterator.next().asArgument();
    while (argument != null) {
      if (hasBitwiseOperation(argument)) {
        int parameterIndex =
            argument.getIndex() - BooleanUtils.intValue(method.getDefinition().isInstance());
        parametersWithBitwiseOperations.set(parameterIndex);
      }
      argument = instructionIterator.next().asArgument();
    }
    if (!parametersWithBitwiseOperations.isEmpty()) {
      feedback.setParametersWithBitwiseOperations(method, parametersWithBitwiseOperations);
    }
  }

  private boolean hasBitwiseOperation(Argument argument) {
    return argument.getOutType().isInt()
        && argument.outValue().hasUserThatMatches(Instruction::isAnd);
  }

  private void computeUnusedArguments(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Compute unused arguments");
    computeUnusedArguments(method, code, feedback);
    timing.end();
  }

  private void computeUnusedArguments(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback) {
    BitSet unusedArguments = new BitSet(method.getDefinition().getNumberOfArguments());
    InstructionIterator instructionIterator = code.entryBlock().iterator();
    Argument argument = instructionIterator.next().asArgument();
    while (argument != null) {
      if (!argument.outValue().hasAnyUsers()) {
        unusedArguments.set(argument.getIndex());
      }
      argument = instructionIterator.next().asArgument();
    }
    if (!unusedArguments.isEmpty()) {
      feedback.setUnusedArguments(method, unusedArguments);
    }
  }
}
