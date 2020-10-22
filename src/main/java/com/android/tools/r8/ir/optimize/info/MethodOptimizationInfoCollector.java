// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import static com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query.DIRECTLY;
import static com.android.tools.r8.ir.code.DominatorTree.Assumption.MAY_HAVE_UNREACHABLE_BLOCKS;
import static com.android.tools.r8.ir.code.Opcodes.ADD;
import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
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
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_NEW_ARRAY;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MONITOR;
import static com.android.tools.r8.ir.code.Opcodes.MUL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.OR;
import static com.android.tools.r8.ir.code.Opcodes.REM;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.SHL;
import static com.android.tools.r8.ir.code.Opcodes.SHR;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.SUB;
import static com.android.tools.r8.ir.code.Opcodes.THROW;
import static com.android.tools.r8.ir.code.Opcodes.USHR;
import static com.android.tools.r8.ir.code.Opcodes.XOR;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.DeterminismAnalysis;
import com.android.tools.r8.ir.analysis.InitializedClassesOnNormalExitAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis.ClassInitializerSideEffect;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DominatorTree;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.DynamicTypeOptimization;
import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerEligibilityInfo;
import com.android.tools.r8.ir.optimize.classinliner.ClassInlinerReceiverAnalysis;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsage;
import com.android.tools.r8.ir.optimize.info.ParameterUsagesInfo.ParameterUsageBuilder;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeAnalyzer;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.initializer.DefaultInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.info.initializer.NonTrivialInstanceInitializerInfo;
import com.android.tools.r8.ir.optimize.typechecks.CheckCastAndInstanceOfMethodSpecialization;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.WorkList;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

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
      Timing timing) {
    DexEncodedMethod definition = method.getDefinition();
    identifyBridgeInfo(definition, code, feedback, timing);
    identifyClassInlinerEligibility(code, feedback, timing);
    identifyParameterUsages(definition, code, feedback, timing);
    analyzeReturns(code, feedback, timing);
    if (options.enableInlining) {
      identifyInvokeSemanticsForInlining(definition, code, feedback, timing);
    }
    computeDynamicReturnType(dynamicTypeOptimization, feedback, definition, code, timing);
    computeInitializedClassesOnNormalExit(feedback, definition, code, timing);
    computeInstanceInitializerInfo(
        definition, code, feedback, instanceFieldInitializationInfos, timing);
    computeMayHaveSideEffects(feedback, definition, code, timing);
    computeReturnValueOnlyDependsOnArguments(feedback, definition, code, timing);
    computeNonNullParamOrThrow(feedback, definition, code, timing);
    computeNonNullParamOnNormalExits(feedback, code, timing);
  }

  private void identifyBridgeInfo(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify bridge info");
    feedback.setBridgeInfo(method, BridgeAnalyzer.analyzeMethod(method, code));
    timing.end();
  }

  private void identifyClassInlinerEligibility(
      IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify class inliner eligibility");
    identifyClassInlinerEligibility(code, feedback);
    timing.end();
  }

  private void identifyClassInlinerEligibility(IRCode code, OptimizationFeedback feedback) {
    // Method eligibility is calculated in similar way for regular method
    // and for the constructor. To be eligible method should only be using its
    // receiver in the following ways:
    //
    //  (1) as a receiver of reads/writes of instance fields of the holder class,
    //  (2) as a return value,
    //  (3) as a receiver of a call to the superclass initializer. Note that we don't
    //      check what is passed to superclass initializer as arguments, only check
    //      that it is not the instance being initialized,
    //  (4) as argument to a monitor instruction.
    //
    // Note that (4) can safely be removed as the receiver is guaranteed not to escape when we class
    // inline it, and hence any monitor instructions are no-ops.
    ProgramMethod context = code.context();
    DexEncodedMethod definition = context.getDefinition();
    boolean instanceInitializer = definition.isInstanceInitializer();
    if (definition.isNative()
        || (!definition.isNonAbstractVirtualMethod() && !instanceInitializer)) {
      return;
    }

    feedback.setClassInlinerEligibility(definition, null); // To allow returns below.

    Value receiver = code.getThis();
    if (receiver.numberOfPhiUsers() > 0) {
      return;
    }

    List<Pair<Invoke.Type, DexMethod>> callsReceiver = new ArrayList<>();
    boolean seenSuperInitCall = false;
    boolean seenMonitor = false;
    boolean modifiesInstanceFields = false;

    AliasedValueConfiguration configuration =
        AssumeAndCheckCastAliasedValueConfiguration.getInstance();
    Predicate<Value> isReceiverAlias = value -> value.getAliasedValue(configuration) == receiver;
    for (Instruction insn : receiver.aliasedUsers(configuration)) {
      switch (insn.opcode()) {
        case ASSUME:
        case CHECK_CAST:
        case RETURN:
          break;

        case MONITOR:
          seenMonitor = true;
          break;

        case INSTANCE_GET:
        case INSTANCE_PUT:
          {
            if (insn.isInstancePut()) {
              InstancePut instancePutInstruction = insn.asInstancePut();
              // Only allow field writes to the receiver.
              if (!isReceiverAlias.test(instancePutInstruction.object())) {
                return;
              }
              // Do not allow the receiver to escape via a field write.
              if (isReceiverAlias.test(instancePutInstruction.value())) {
                return;
              }
              modifiesInstanceFields = true;
            }
            DexField field = insn.asFieldInstruction().getField();
            if (appView.appInfo().resolveField(field).isFailedOrUnknownResolution()) {
              return;
            }
            break;
          }

        case INVOKE_DIRECT:
          {
            InvokeDirect invoke = insn.asInvokeDirect();
            DexMethod invokedMethod = invoke.getInvokedMethod();
            if (dexItemFactory.isConstructor(invokedMethod)
                && invokedMethod.holder == context.getHolder().superType
                && ListUtils.lastIndexMatching(invoke.arguments(), isReceiverAlias) == 0
                && !seenSuperInitCall
                && instanceInitializer) {
              seenSuperInitCall = true;
              break;
            }
            // We don't support other direct calls yet.
            return;
          }

        case INVOKE_STATIC:
          {
            InvokeStatic invoke = insn.asInvokeStatic();
            DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
            if (singleTarget == null) {
              return; // Not allowed.
            }
            if (singleTarget.getReference() == dexItemFactory.objectsMethods.requireNonNull) {
              if (!invoke.hasOutValue() || !invoke.outValue().hasAnyUsers()) {
                continue;
              }
            }
            return;
          }

        case INVOKE_VIRTUAL:
          {
            InvokeVirtual invoke = insn.asInvokeVirtual();
            if (ListUtils.lastIndexMatching(invoke.arguments(), isReceiverAlias) != 0) {
              return; // Not allowed.
            }
            DexMethod invokedMethod = invoke.getInvokedMethod();
            DexType returnType = invokedMethod.proto.returnType;
            if (returnType.isClassType()
                && appView.appInfo().inSameHierarchy(returnType, context.getHolderType())) {
              return; // Not allowed, could introduce an alias of the receiver.
            }
            callsReceiver.add(new Pair<>(Invoke.Type.VIRTUAL, invokedMethod));
          }
          break;

        default:
          // Other receiver usages make the method not eligible.
          return;
      }
    }

    if (instanceInitializer && !seenSuperInitCall) {
      // Call to super constructor not found?
      return;
    }

    boolean synchronizedVirtualMethod = definition.isSynchronized() && definition.isVirtualMethod();

    feedback.setClassInlinerEligibility(
        definition,
        new ClassInlinerEligibilityInfo(
            callsReceiver,
            new ClassInlinerReceiverAnalysis(appView, definition, code).computeReturnsReceiver(),
            seenMonitor || synchronizedVirtualMethod,
            modifiesInstanceFields));
  }

  private void identifyParameterUsages(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify parameter usages");
    identifyParameterUsages(method, code, feedback);
    timing.end();
  }

  private void identifyParameterUsages(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    List<ParameterUsage> usages = new ArrayList<>();
    List<Value> values = code.collectArguments();
    for (int i = 0; i < values.size(); i++) {
      Value value = values.get(i);
      ParameterUsage usage = collectParameterUsages(i, value);
      if (usage != null) {
        usages.add(usage);
      }
    }
    feedback.setParameterUsages(
        method,
        usages.isEmpty()
            ? DefaultMethodOptimizationInfo.UNKNOWN_PARAMETER_USAGE_INFO
            : new ParameterUsagesInfo(usages));
  }

  private ParameterUsage collectParameterUsages(int i, Value root) {
    ParameterUsageBuilder builder = new ParameterUsageBuilder(root, i, dexItemFactory);
    WorkList<Value> worklist = WorkList.newIdentityWorkList();
    worklist.addIfNotSeen(root);
    while (worklist.hasNext()) {
      Value value = worklist.next();
      if (value.hasPhiUsers()) {
        return null;
      }
      for (Instruction user : value.uniqueUsers()) {
        if (!builder.note(user)) {
          return null;
        }
        if (user.isAssume()) {
          worklist.addIfNotSeen(user.outValue());
        }
      }
    }
    return builder.build();
  }

  private void analyzeReturns(IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify returns argument");
    analyzeReturns(code, feedback);
    timing.end();
  }

  private void analyzeReturns(IRCode code, OptimizationFeedback feedback) {
    ProgramMethod context = code.context();
    DexEncodedMethod method = context.getDefinition();
    List<BasicBlock> normalExits = code.computeNormalExitBlocks();
    if (normalExits.isEmpty()) {
      feedback.methodNeverReturnsNormally(method);
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
                context, abstractReturnValue);
          }
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
    assert !appView.appInfo().isPinned(method.method);

    if (!method.isInstanceInitializer()) {
      return;
    }

    assert instanceFieldInitializationInfos != null;

    if (method.accessFlags.isNative()) {
      return;
    }

    if (appView.appInfo().mayHaveSideEffects.containsKey(method.method)) {
      return;
    }

    NonTrivialInstanceInitializerInfo.Builder builder =
        NonTrivialInstanceInitializerInfo.builder(instanceFieldInitializationInfos);
    InstanceInitializerInfo instanceInitializerInfo = analyzeInstanceInitializer(code, builder);
    feedback.setInstanceInitializerInfo(
        method,
        instanceInitializerInfo != null
            ? instanceInitializerInfo
            : DefaultInstanceInitializerInfo.getInstance());
  }

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
              DexEncodedField field =
                  appView.appInfo().resolveField(fieldGet.getField()).getResolvedField();
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
              if (singleTarget.isInstanceInitializer() && invoke.getReceiver() == receiver) {
                if (builder.hasParent()) {
                  return null;
                }
                // java.lang.Enum.<init>() and java.lang.Object.<init>() are considered trivial.
                if (invokedMethod == dexItemFactory.enumMembers.constructor
                    || invokedMethod == dexItemFactory.objectMembers.constructor) {
                  builder.setParent(invokedMethod);
                  break;
                }
                builder.merge(singleTarget.getOptimizationInfo().getInstanceInitializerInfo());
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

          case INVOKE_NEW_ARRAY:
            {
              InvokeNewArray invoke = instruction.asInvokeNewArray();
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

  private void identifyInvokeSemanticsForInlining(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Identify invoke semantics for inlining");
    identifyInvokeSemanticsForInlining(method, code, feedback);
    timing.end();
  }

  private void identifyInvokeSemanticsForInlining(
      DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    if (method.isStatic()) {
      // Identifies if the method preserves class initialization after inlining.
      feedback.markTriggerClassInitBeforeAnySideEffect(
          method, triggersClassInitializationBeforeSideEffect(code));
    } else {
      // Identifies if the method preserves null check of the receiver after inlining.
      final Value receiver = code.getThis();
      feedback.markCheckNullReceiverBeforeAnySideEffect(
          method, receiver.isUsed() && checksNullBeforeSideEffect(code, receiver));
    }
  }

  /**
   * Returns true if the given code unconditionally triggers class initialization before any other
   * side effecting instruction.
   *
   * <p>Note: we do not track phis so we may return false negative. This is a conservative approach.
   */
  private boolean triggersClassInitializationBeforeSideEffect(IRCode code) {
    return alwaysTriggerExpectedEffectBeforeAnythingElse(
        code,
        (instruction, it) -> {
          ProgramMethod context = code.context();
          if (instruction.definitelyTriggersClassInitialization(
              context.getHolderType(),
              context,
              appView,
              DIRECTLY,
              AnalysisAssumption.INSTRUCTION_DOES_NOT_THROW)) {
            // In order to preserve class initialization semantic, the exception must not be caught
            // by any handler. Therefore, we must ignore this instruction if it is covered by a
            // catch handler.
            // Note: this is a conservative approach where we consider that any catch handler could
            // catch the exception, even if it cannot catch an ExceptionInInitializerError.
            if (!instruction.getBlock().hasCatchHandlers()) {
              // We found an instruction that preserves initialization of the class.
              return InstructionEffect.DESIRED_EFFECT;
            }
          } else if (instruction.instructionMayHaveSideEffects(appView, context)) {
            // We found a side effect before class initialization.
            return InstructionEffect.OTHER_EFFECT;
          }
          return InstructionEffect.NO_EFFECT;
        });
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
          if (isKotlinNullCheck(instr, value, appView)) {
            DexMethod invokedMethod = instr.asInvokeStatic().getInvokedMethod();
            // Kotlin specific way of throwing NPE: throwParameterIsNullException.
            // Similarly, combined with the above CONDITIONAL_EFFECT, the code checks on NPE on
            // the value.
            if (invokedMethod.name
                == appView.dexItemFactory().kotlin.intrinsics.throwParameterIsNullException.name) {
              // We found a NPE (or similar exception) throwing code.
              // Combined with the above CONDITIONAL_EFFECT, the code checks NPE on the value.
              for (BasicBlock predecessor : currentBlock.getPredecessors()) {
                if (isNullCheck(predecessor.exit(), value)) {
                  return InstructionEffect.DESIRED_EFFECT;
                }
              }
              // Hitting here means that this call might be used for other parameters. If we don't
              // bail out, it will be regarded as side effects for the current value.
              return InstructionEffect.NO_EFFECT;
            } else {
              // Kotlin specific way of checking parameter nullness: checkParameterIsNotNull.
              assert invokedMethod.name
                  == appView.dexItemFactory().kotlin.intrinsics.checkParameterIsNotNull.name;
              return InstructionEffect.DESIRED_EFFECT;
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
            if (instr.isConstString() && !instr.instructionInstanceCanThrow()) {
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
  // declare a method called checkParameterIsNotNull(parameter, message) or
  // throwParameterIsNullException(parameterName) in a package that starts with "kotlin".
  private static boolean isKotlinNullCheck(Instruction instr, Value value, AppView<?> appView) {
    if (appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      return false;
    }
    if (!instr.isInvokeStatic()) {
      return false;
    }
    // We need to strip the holder, since Kotlin adds different versions of null-check machinery,
    // e.g., kotlin.collections.ArraysKt___ArraysKt... or kotlin.jvm.internal.ArrayIteratorKt...
    MethodSignatureEquivalence wrapper = MethodSignatureEquivalence.get();
    Wrapper<DexMethod> checkParameterIsNotNull =
        wrapper.wrap(appView.dexItemFactory().kotlin.intrinsics.checkParameterIsNotNull);
    Wrapper<DexMethod> throwParamIsNullException =
        wrapper.wrap(appView.dexItemFactory().kotlin.intrinsics.throwParameterIsNullException);
    DexMethod invokedMethod =
        appView.graphLens().getOriginalMethodSignature(instr.asInvokeStatic().getInvokedMethod());
    Wrapper<DexMethod> methodWrap = wrapper.wrap(invokedMethod);
    if (methodWrap.equals(throwParamIsNullException)
        || (methodWrap.equals(checkParameterIsNotNull) && instr.inValues().get(0).equals(value))) {
      if (invokedMethod.holder.getPackageDescriptor().startsWith(Kotlin.NAME)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isNullCheck(Instruction instr, Value value) {
    return instr.isIf() && instr.asIf().isZeroTest()
        && instr.inValues().get(0).equals(value)
        && (instr.asIf().getType() == If.Type.EQ || instr.asIf().getType() == If.Type.NE);
  }

  /**
   * Returns true if the given instruction is {@code v <- new-instance NullPointerException}, and
   * the next instruction is {@code invoke-direct v, NullPointerException.<init>()}.
   */
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

  private void computeDynamicReturnType(
      DynamicTypeOptimization dynamicTypeOptimization,
      OptimizationFeedback feedback,
      DexEncodedMethod method,
      IRCode code,
      Timing timing) {
    timing.begin("Compute dynamic return type");
    computeDynamicReturnType(dynamicTypeOptimization, feedback, method, code);
    timing.end();
  }

  private void computeDynamicReturnType(
      DynamicTypeOptimization dynamicTypeOptimization,
      OptimizationFeedback feedback,
      DexEncodedMethod method,
      IRCode code) {
    if (dynamicTypeOptimization != null) {
      DexType staticReturnTypeRaw = method.method.proto.returnType;
      if (!staticReturnTypeRaw.isReferenceType()) {
        return;
      }
      TypeElement dynamicUpperBoundReturnType =
          dynamicTypeOptimization.computeDynamicReturnType(method, code);
      if (dynamicUpperBoundReturnType != null) {
        if (dynamicUpperBoundReturnType.isReferenceType()
            && dynamicUpperBoundReturnType.isDefinitelyNull()) {
          feedback.methodReturnsAbstractValue(
              method, appView, appView.abstractValueFactory().createSingleNumberValue(0));
          feedback.methodReturnsObjectWithUpperBoundType(method, appView, TypeElement.getNull());
        } else {
          TypeElement staticReturnType =
              TypeElement.fromDexType(staticReturnTypeRaw, Nullability.maybeNull(), appView);
          // If the dynamic return type is not more precise than the static return type there is no
          // need to record it.
          if (dynamicUpperBoundReturnType.strictlyLessThan(staticReturnType, appView)) {
            feedback.methodReturnsObjectWithUpperBoundType(
                method, appView, dynamicUpperBoundReturnType);
          }
        }
      }

      if (dynamicUpperBoundReturnType != null && dynamicUpperBoundReturnType.isNullType()) {
        return;
      }

      ClassTypeElement dynamicLowerBoundReturnType =
          dynamicTypeOptimization.computeDynamicLowerBoundType(method, code);
      if (dynamicLowerBoundReturnType != null) {
        assert dynamicUpperBoundReturnType == null
            || dynamicUpperBoundReturnType
                .nullability()
                .lessThanOrEqual(dynamicLowerBoundReturnType.nullability());
        feedback.methodReturnsObjectWithLowerBoundType(method, dynamicLowerBoundReturnType);
      }
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
    if (appView.appInfo().mayHaveSideEffects.containsKey(method.method)) {
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
        assert !context.getHolderType().isD8R8SynthesizedLambdaClassType()
                || options.debug
                || appView.appInfo().hasPinnedInstanceInitializer(context.getHolderType())
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
        if (instruction.instructionMayHaveSideEffects(appView, context)) {
          mayHaveSideEffects = true;
          break;
        }
      }
    }
    if (!mayHaveSideEffects) {
      feedback.methodMayNotHaveSideEffects(method);
    }
  }

  // Returns true if the given class overrides the method `void java.lang.Object.finalize()`.
  private boolean hasNonTrivialFinalizeMethod(DexProgramClass clazz) {
    if (clazz.isInterface()) {
      return false;
    }
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    ResolutionResult resolutionResult =
        appView
            .appInfo()
            .resolveMethodOnClass(appView.dexItemFactory().objectMembers.finalize, clazz);
    DexEncodedMethod target = resolutionResult.getSingleTarget();
    return target != null
        && target.method != dexItemFactory.enumMembers.finalize
        && target.method != dexItemFactory.objectMembers.finalize;
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

  private void computeNonNullParamOrThrow(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code, Timing timing) {
    timing.begin("Compute non-null-param-or-throw");
    computeNonNullParamOrThrow(feedback, method, code);
    timing.end();
  }

  // Track usage of parameters and compute their nullability and possibility of NPE.
  private void computeNonNullParamOrThrow(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (method.getOptimizationInfo().getNonNullParamOrThrow() != null) {
      return;
    }
    List<Value> arguments = code.collectArguments();
    BitSet paramsCheckedForNull = new BitSet();
    for (int index = 0; index < arguments.size(); index++) {
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
    if (paramsCheckedForNull.length() > 0) {
      feedback.setNonNullParamOrThrow(method, paramsCheckedForNull);
    }
  }

  private void computeNonNullParamOnNormalExits(
      OptimizationFeedback feedback, IRCode code, Timing timing) {
    timing.begin("Compute non-null-param-on-normal-exits");
    computeNonNullParamOnNormalExits(feedback, code);
    timing.end();
  }

  private void computeNonNullParamOnNormalExits(OptimizationFeedback feedback, IRCode code) {
    Set<BasicBlock> normalExits = Sets.newIdentityHashSet();
    normalExits.addAll(code.computeNormalExitBlocks());
    DominatorTree dominatorTree = new DominatorTree(code, MAY_HAVE_UNREACHABLE_BLOCKS);
    List<Value> arguments = code.collectArguments();
    BitSet facts = new BitSet();
    Set<BasicBlock> nullCheckedBlocks = Sets.newIdentityHashSet();
    for (int index = 0; index < arguments.size(); index++) {
      Value argument = arguments.get(index);
      // Consider reference-type parameter only.
      if (!argument.getType().isReferenceType()) {
        continue;
      }
      // The receiver is always non-null on normal exits.
      if (argument.isThis()) {
        facts.set(index);
        continue;
      }
      // Collect basic blocks that check nullability of the parameter.
      nullCheckedBlocks.clear();
      for (Instruction user : argument.uniqueUsers()) {
        if (user.isAssumeWithNonNullAssumption()) {
          nullCheckedBlocks.add(user.asAssume().getBlock());
        }
        if (user.isIf()
            && user.asIf().isZeroTest()
            && (user.asIf().getType() == If.Type.EQ || user.asIf().getType() == If.Type.NE)) {
          nullCheckedBlocks.add(user.asIf().targetFromNonNullObject());
        }
      }
      if (!nullCheckedBlocks.isEmpty()) {
        boolean allExitsCovered = true;
        for (BasicBlock normalExit : normalExits) {
          if (!isNormalExitDominated(normalExit, code, dominatorTree, nullCheckedBlocks)) {
            allExitsCovered = false;
            break;
          }
        }
        if (allExitsCovered) {
          facts.set(index);
        }
      }
    }
    if (facts.length() > 0) {
      feedback.setNonNullParamOnNormalExits(code.method(), facts);
    }
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
}
