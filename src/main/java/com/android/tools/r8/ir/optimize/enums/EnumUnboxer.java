// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_GET;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_LENGTH;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues.EnumStaticFieldValues;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.EnumValuesObjectState;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Opcodes;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap.EnumData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldMappingData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldOrdinalData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldUnknownData;
import com.android.tools.r8.ir.optimize.enums.classification.CheckNotNullEnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.IllegalInvokeWithImpreciseParameterTypeReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingContentsForEnumValuesArrayReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingEnumStaticFieldValuesReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingInstanceFieldValueForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingObjectStateForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.UnsupportedInstanceFieldValueForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.UnsupportedLibraryInvokeReason;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

public class EnumUnboxer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  // Map the enum candidates with their dependencies, i.e., the methods to reprocess for the given
  // enum if the optimization eventually decides to unbox it.
  private final EnumUnboxingCandidateInfoCollection enumUnboxingCandidatesInfo;
  private final Set<DexProgramClass> candidatesToRemoveInWave = Sets.newConcurrentHashSet();
  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMap =
      new ConcurrentHashMap<>();
  private final ProgramMethodSet methodsDependingOnLibraryModelisation =
      ProgramMethodSet.createConcurrent();

  // Map from checkNotNull() methods to the enums that use the given method.
  private final ProgramMethodMap<Set<DexProgramClass>> checkNotNullMethods =
      ProgramMethodMap.createConcurrent();

  private final DexEncodedField ordinalField;

  private EnumUnboxingRewriter enumUnboxerRewriter;

  private final boolean debugLogEnabled;
  private final Map<DexType, List<Reason>> debugLogs;

  public EnumUnboxer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    if (appView.options().testing.enableEnumUnboxingDebugLogs) {
      debugLogEnabled = true;
      debugLogs = new ConcurrentHashMap<>();
    } else {
      debugLogEnabled = false;
      debugLogs = null;
    }
    assert !appView.options().debug;
    enumUnboxingCandidatesInfo = new EnumUnboxingCandidateAnalysis(appView, this).findCandidates();

    ordinalField =
        appView.appInfo().resolveField(factory.enumMembers.ordinalField).getResolvedField();
    if (ordinalField == null) {
      // This can happen when compiling for non standard libraries, in that case, this effectively
      // disables the enum unboxer.
      enumUnboxingCandidatesInfo.clear();
    }
  }

  public static int ordinalToUnboxedInt(int ordinal) {
    return ordinal + 1;
  }

  public void updateEnumUnboxingCandidatesInfo() {
    for (DexProgramClass candidate : candidatesToRemoveInWave) {
      enumUnboxingCandidatesInfo.removeCandidate(candidate);
    }
    candidatesToRemoveInWave.clear();
  }

  /**
   * Returns true if {@param enumClass} was marked as being unboxable.
   *
   * <p>Note that, if debug logging is enabled, {@param enumClass} is not marked unboxable until the
   * enum unboxing analysis has finished. This is to ensure completeness of the reason reporting.
   */
  private boolean markEnumAsUnboxable(Reason reason, DexProgramClass enumClass) {
    assert enumClass.isEnum();
    if (!reportFailure(enumClass, reason)) {
      // The failure was not reported, meaning debug logging is disabled.
      candidatesToRemoveInWave.add(enumClass);
      return true;
    }
    return false;
  }

  private void markMethodDependsOnLibraryModelisation(ProgramMethod method) {
    methodsDependingOnLibraryModelisation.add(method);
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(TypeElement lattice) {
    if (lattice.isClassType()) {
      DexType classType = lattice.asClassType().getClassType();
      return getEnumUnboxingCandidateOrNull(classType);
    }
    if (lattice.isArrayType()) {
      ArrayTypeElement arrayType = lattice.asArrayType();
      if (arrayType.getBaseType().isClassType()) {
        return getEnumUnboxingCandidateOrNull(arrayType.getBaseType());
      }
    }
    return null;
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(DexType type) {
    return enumUnboxingCandidatesInfo.getCandidateClassOrNull(type);
  }

  public void analyzeEnums(IRCode code, MutableMethodConversionOptions conversionOptions) {
    Set<DexType> eligibleEnums = Sets.newIdentityHashSet();
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        Value outValue = instruction.outValue();
        if (outValue != null) {
          DexProgramClass enumClass =
              getEnumUnboxingCandidateOrNull(outValue.getDynamicUpperBoundType(appView));
          if (enumClass != null) {
            Reason reason = validateEnumUsages(code, outValue, enumClass);
            if (reason == Reason.ELIGIBLE) {
              eligibleEnums.add(enumClass.type);
            }
          }
          if (outValue.getType().isNullType()) {
            addNullDependencies(code, outValue.uniqueUsers(), eligibleEnums);
          }
        } else {
          if (instruction.isInvokeMethod()) {
            DexProgramClass enumClass =
                getEnumUnboxingCandidateOrNull(instruction.asInvokeMethod().getReturnType());
            if (enumClass != null) {
              eligibleEnums.add(enumClass.type);
            }
          }
        }
        switch (instruction.opcode()) {
          case Opcodes.CONST_CLASS:
            analyzeConstClass(instruction.asConstClass(), eligibleEnums, code.context());
            break;
          case Opcodes.CHECK_CAST:
            analyzeCheckCast(instruction.asCheckCast(), eligibleEnums);
            break;
          case INVOKE_STATIC:
            analyzeInvokeStatic(instruction.asInvokeStatic(), eligibleEnums, code.context());
            break;
          case Opcodes.STATIC_GET:
          case Opcodes.INSTANCE_GET:
          case Opcodes.STATIC_PUT:
          case INSTANCE_PUT:
            analyzeFieldInstruction(
                instruction.asFieldInstruction(), eligibleEnums, code.context());
            break;
          default: // Nothing to do for other instructions.
        }
      }
      for (Phi phi : block.getPhis()) {
        DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(phi.getType());
        if (enumClass != null) {
          Reason reason = validateEnumUsages(code, phi, enumClass);
          if (reason == Reason.ELIGIBLE) {
            eligibleEnums.add(enumClass.type);
          }
        }
        if (phi.getType().isNullType()) {
          addNullDependencies(code, phi.uniqueUsers(), eligibleEnums);
        }
      }
    }
    if (!eligibleEnums.isEmpty()) {
      for (DexType eligibleEnum : eligibleEnums) {
        enumUnboxingCandidatesInfo.addMethodDependency(eligibleEnum, code.context());
      }
    }
    if (methodsDependingOnLibraryModelisation.contains(code.context())) {
      conversionOptions.disablePeepholeOptimizations();
    }
  }

  private void analyzeFieldInstruction(
      FieldInstruction fieldInstruction, Set<DexType> eligibleEnums, ProgramMethod context) {
    DexField field = fieldInstruction.getField();
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(field.holder);
    if (enumClass != null) {
      FieldResolutionResult resolutionResult = appView.appInfo().resolveField(field, context);
      if (resolutionResult.isSuccessfulResolution()) {
        eligibleEnums.add(enumClass.getType());
      } else {
        markEnumAsUnboxable(Reason.UNRESOLVABLE_FIELD, enumClass);
      }
    }
  }

  private void analyzeInvokeStatic(
      InvokeStatic invokeStatic, Set<DexType> eligibleEnums, ProgramMethod context) {
    DexMethod invokedMethod = invokeStatic.getInvokedMethod();
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
    if (enumClass != null) {
      DexClassAndMethod method = invokeStatic.lookupSingleTarget(appView, context);
      if (method != null) {
        eligibleEnums.add(enumClass.type);
      } else {
        markEnumAsUnboxable(Reason.INVALID_INVOKE, enumClass);
      }
    }
  }

  private void analyzeCheckCast(CheckCast checkCast, Set<DexType> eligibleEnums) {
    // Casts to enum array types are fine as long all enum array creations are valid and have valid
    // usages. Since creations of enum arrays are rewritten to primitive int arrays, enum array
    // casts will continue to work after rewriting to int[] casts. Casts that failed with
    // ClassCastException: "T[] cannot be cast to MyEnum[]" will continue to fail, but with "T[]
    // cannot be cast to int[]".
    //
    // Note that strictly speaking, the rewriting from MyEnum[] to int[] could change the semantics
    // of code that would fail with "int[] cannot be cast to MyEnum[]" in the input. However, javac
    // does not allow such code ("incompatible types"), so we should generally not see such code.
    if (checkCast.getType().isArrayType()) {
      return;
    }

    // We are doing a type check, which typically means the in-value is of an upper
    // type and cannot be dealt with.
    // If the cast is on a dynamically typed object, the checkCast can be simply removed.
    // This allows enum array clone and valueOf to work correctly.
    DexProgramClass enumClass =
        getEnumUnboxingCandidateOrNull(checkCast.getType().toBaseType(factory));
    if (enumClass == null) {
      return;
    }
    if (allowCheckCast(checkCast)) {
      eligibleEnums.add(enumClass.type);
      return;
    }
    markEnumAsUnboxable(Reason.DOWN_CAST, enumClass);
  }

  private boolean allowCheckCast(CheckCast checkCast) {
    TypeElement objectType = checkCast.object().getDynamicUpperBoundType(appView);
    return objectType.equalUpToNullability(
        TypeElement.fromDexType(checkCast.getType(), definitelyNotNull(), appView));
  }

  private void analyzeConstClass(
      ConstClass constClass, Set<DexType> eligibleEnums, ProgramMethod context) {
    // We are using the ConstClass of an enum, which typically means the enum cannot be unboxed.
    // We however allow unboxing if the ConstClass is used only:
    // - as an argument to java.lang.reflect.Array#newInstance(java.lang.Class, int[]), to allow
    //   unboxing of:
    //    MyEnum[][] a = new MyEnum[x][y];
    // - as an argument to Enum#valueOf, to allow unboxing of:
    //    MyEnum a = Enum.valueOf(MyEnum.class, "A");
    // - as a receiver for a name method, to allow unboxing of:
    //    MyEnum.class.getName();
    DexType enumType = constClass.getValue();
    if (!enumUnboxingCandidatesInfo.isCandidate(enumType)) {
      return;
    }
    if (constClass.outValue() == null) {
      eligibleEnums.add(enumType);
      return;
    }
    DexProgramClass enumClass = appView.definitionFor(enumType).asProgramClass();
    if (constClass.outValue().hasPhiUsers()) {
      markEnumAsUnboxable(Reason.CONST_CLASS, enumClass);
      return;
    }
    for (Instruction user : constClass.outValue().aliasedUsers()) {
      if (!isLegitimateConstClassUser(user, context, enumClass)) {
        markEnumAsUnboxable(Reason.CONST_CLASS, enumClass);
        return;
      }
    }
    eligibleEnums.add(enumType);
  }

  private boolean isLegitimateConstClassUser(
      Instruction user, ProgramMethod context, DexProgramClass enumClass) {
    if (user.isAssume()) {
      if (user.outValue().hasPhiUsers()) {
        return false;
      }
      return true;
    }

    if (user.isInvokeStatic()) {
      DexClassAndMethod singleTarget = user.asInvokeStatic().lookupSingleTarget(appView, context);
      if (singleTarget == null) {
        return false;
      }
      if (singleTarget.getReference() == factory.enumMembers.valueOf) {
        // The name data is required for the correct mapping from the enum name to the ordinal
        // in the valueOf utility method.
        addRequiredNameData(enumClass);
        markMethodDependsOnLibraryModelisation(context);
        return true;
      }
      if (singleTarget.getReference()
          == factory.javaLangReflectArrayMembers.newInstanceMethodWithDimensions) {
        markMethodDependsOnLibraryModelisation(context);
        return true;
      }
    }

    if (user.isInvokeVirtual()) {
      InvokeVirtual invoke = user.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod == factory.classMethods.desiredAssertionStatus) {
        // Only valid in the enum's class initializer, since the class constant must be rewritten
        // to LocalEnumUtility.class instead of int.class.
        return context.getDefinition().isClassInitializer() && context.getHolder() == enumClass;
      }
      if (isUnboxableNameMethod(invokedMethod)) {
        return true;
      }
    }

    return false;
  }

  private void addRequiredNameData(DexProgramClass enumClass) {
    enumUnboxingCandidatesInfo.addRequiredEnumInstanceFieldData(
        enumClass, factory.enumMembers.nameField);
  }

  private boolean isUnboxableNameMethod(DexMethod method) {
    return method == factory.classMethods.getName
        || method == factory.classMethods.getCanonicalName
        || method == factory.classMethods.getSimpleName;
  }

  private void addNullDependencies(IRCode code, Set<Instruction> uses, Set<DexType> eligibleEnums) {
    for (Instruction use : uses) {
      if (use.isInvokeMethod()) {
        InvokeMethod invokeMethod = use.asInvokeMethod();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        for (DexType paramType : invokedMethod.proto.parameters.values) {
          if (enumUnboxingCandidatesInfo.isCandidate(paramType)) {
            eligibleEnums.add(paramType);
          }
        }
        if (invokeMethod.isInvokeMethodWithReceiver()) {
          DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
          if (enumClass != null) {
            markEnumAsUnboxable(Reason.ENUM_METHOD_CALLED_WITH_NULL_RECEIVER, enumClass);
          }
        }
      } else if (use.isFieldPut()) {
        DexType type = use.asFieldInstruction().getField().type;
        if (enumUnboxingCandidatesInfo.isCandidate(type)) {
          eligibleEnums.add(type);
        }
      } else if (use.isReturn()) {
        DexType returnType = code.method().getReference().proto.returnType;
        if (enumUnboxingCandidatesInfo.isCandidate(returnType)) {
          eligibleEnums.add(returnType);
        }
      }
    }
  }

  private Reason validateEnumUsages(IRCode code, Value value, DexProgramClass enumClass) {
    Reason result = Reason.ELIGIBLE;
    for (Instruction user : value.uniqueUsers()) {
      Reason reason = instructionAllowEnumUnboxing(user, code, enumClass, value);
      if (reason != Reason.ELIGIBLE) {
        if (markEnumAsUnboxable(reason, enumClass)) {
          return reason;
        }
        // Record that the enum is ineligible, and continue analysis to collect all reasons for
        // debugging.
        result = reason;
      }
    }
    for (Phi phi : value.uniquePhiUsers()) {
      for (Value operand : phi.getOperands()) {
        if (!operand.getType().isNullType()
            && getEnumUnboxingCandidateOrNull(operand.getType()) != enumClass) {
          // All reported reasons from here will be the same (INVALID_PHI), so just return
          // immediately.
          markEnumAsUnboxable(Reason.INVALID_PHI, enumClass);
          return Reason.INVALID_PHI;
        }
      }
    }
    return result;
  }

  public void unboxEnums(
      IRConverter converter,
      PostMethodProcessor.Builder postBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback)
      throws ExecutionException {
    assert candidatesToRemoveInWave.isEmpty();
    EnumDataMap enumDataMap = finishAnalysis();
    assert candidatesToRemoveInWave.isEmpty();

    // At this point the enum unboxing candidates are no longer candidates, they will all be
    // unboxed. We extract the now immutable enums to unbox information and clear the candidate
    // info.
    if (enumUnboxingCandidatesInfo.isEmpty()) {
      assert enumDataMap.isEmpty();
      appView.setUnboxedEnums(enumDataMap);
      return;
    }

    ImmutableSet<DexType> enumsToUnbox = enumUnboxingCandidatesInfo.candidates();
    ImmutableSet<DexProgramClass> enumClassesToUnbox =
        enumUnboxingCandidatesInfo.candidateClasses();
    ProgramMethodSet dependencies = enumUnboxingCandidatesInfo.allMethodDependencies();
    enumUnboxingCandidatesInfo.clear();
    // Update keep info on any of the enum methods of the removed classes.
    updateKeepInfo(enumsToUnbox);

    EnumUnboxingUtilityClasses utilityClasses =
        EnumUnboxingUtilityClasses.builder(appView)
            .synthesizeEnumUnboxingUtilityClasses(enumClassesToUnbox, enumDataMap)
            .build(converter, executorService);

    EnumUnboxingTreeFixer.Result treeFixerResult =
        new EnumUnboxingTreeFixer(
                appView, checkNotNullMethods, enumDataMap, enumClassesToUnbox, utilityClasses)
            .fixupTypeReferences(converter, executorService);
    EnumUnboxingLens enumUnboxingLens = treeFixerResult.getLens();
    enumUnboxerRewriter =
        new EnumUnboxingRewriter(
            appView,
            treeFixerResult.getCheckNotNullToCheckNotZeroMapping(),
            converter,
            enumUnboxingLens,
            enumDataMap,
            utilityClasses);
    appView.setUnboxedEnums(enumDataMap);
    GraphLens previousLens = appView.graphLens();
    appView.rewriteWithLens(enumUnboxingLens);
    updateOptimizationInfos(executorService, feedback, treeFixerResult.getPrunedItems());
    postBuilder.put(dependencies);
    // Methods depending on library modelisation need to be reprocessed so they are peephole
    // optimized.
    postBuilder.put(methodsDependingOnLibraryModelisation);
    methodsDependingOnLibraryModelisation.clear();
    postBuilder.removePrunedMethods(treeFixerResult.getPrunedItems().getRemovedMethods());
    postBuilder.rewrittenWithLens(appView, previousLens);
  }

  private void updateOptimizationInfos(
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      PrunedItems prunedItems)
      throws ExecutionException {
    feedback.fixupOptimizationInfos(
        appView,
        executorService,
        new OptimizationInfoFixer() {
          @Override
          public void fixup(DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo) {
            optimizationInfo
                .fixupClassTypeReferences(appView, appView.graphLens())
                .fixupAbstractValue(appView, appView.graphLens());
          }

          @Override
          public void fixup(
              DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo) {
            optimizationInfo
                .fixupClassTypeReferences(appView, appView.graphLens())
                .fixupAbstractReturnValue(appView, appView.graphLens())
                .fixupInstanceInitializerInfo(appView, appView.graphLens(), prunedItems);
          }
        });
  }

  private void updateKeepInfo(Set<DexType> enumsToUnbox) {
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    keepInfo.mutate(mutator -> mutator.removeKeepInfoForPrunedItems(enumsToUnbox));
  }

  public EnumDataMap finishAnalysis() {
    analyzeInitializers();
    updateEnumUnboxingCandidatesInfo();
    EnumDataMap enumDataMap = analyzeEnumInstances();
    if (debugLogEnabled) {
      // Remove all enums that have been reported as being unboxable.
      debugLogs.keySet().forEach(enumUnboxingCandidatesInfo::removeCandidate);
      reportEnumsAnalysis();
    }
    assert enumDataMap.getUnboxedEnums().size() == enumUnboxingCandidatesInfo.candidates().size();
    return enumDataMap;
  }

  private EnumDataMap analyzeEnumInstances() {
    ImmutableMap.Builder<DexType, EnumData> builder = ImmutableMap.builder();
    enumUnboxingCandidatesInfo.forEachCandidateAndRequiredInstanceFieldData(
        (enumClass, instanceFields) -> {
          EnumData data = buildData(enumClass, instanceFields);
          if (data == null) {
            // Reason is already reported at this point.
            enumUnboxingCandidatesInfo.removeCandidate(enumClass);
            return;
          }
          if (!debugLogEnabled || !debugLogs.containsKey(enumClass.getType())) {
            builder.put(enumClass.type, data);
          }
        });
    staticFieldValuesMap.clear();
    return new EnumDataMap(builder.build());
  }

  private EnumData buildData(DexProgramClass enumClass, Set<DexField> instanceFields) {
    if (!enumClass.hasStaticFields()) {
      return new EnumData(ImmutableMap.of(), ImmutableMap.of(), ImmutableSet.of(), -1);
    }

    // This map holds all the accessible fields to their unboxed value, so we can remap the field
    // read to the unboxed value.
    ImmutableMap.Builder<DexField, Integer> unboxedValues = ImmutableMap.builder();
    // This maps the ordinal to the object state, note that some fields may have been removed,
    // hence the entry is in this map but not the enumToOrdinalMap.
    Int2ReferenceMap<ObjectState> ordinalToObjectState = new Int2ReferenceArrayMap<>();
    // Any fields matching the expected $VALUES content can be recorded here, they have however
    // all the same content.
    ImmutableSet.Builder<DexField> valuesField = ImmutableSet.builder();
    EnumValuesObjectState valuesContents = null;

    EnumStaticFieldValues enumStaticFieldValues = staticFieldValuesMap.get(enumClass.type);
    if (enumStaticFieldValues == null) {
      reportFailure(enumClass, new MissingEnumStaticFieldValuesReason());
      return null;
    }

    // Step 1: We iterate over the field to find direct enum instance information and the values
    // fields.
    for (DexEncodedField staticField : enumClass.staticFields()) {
      if (factory.enumMembers.isEnumField(staticField, enumClass.type)) {
        ObjectState enumState =
            enumStaticFieldValues.getObjectStateForPossiblyPinnedField(staticField.getReference());
        if (enumState == null) {
          if (staticField.getOptimizationInfo().isDead()) {
            // We don't care about unused field data.
            continue;
          }
          // We could not track the content of that field. We bail out.
          reportFailure(
              enumClass, new MissingObjectStateForEnumInstanceReason(staticField.getReference()));
          return null;
        }
        OptionalInt optionalOrdinal = getOrdinal(enumState);
        if (!optionalOrdinal.isPresent()) {
          reportFailure(
              enumClass,
              new MissingInstanceFieldValueForEnumInstanceReason(
                  staticField.getReference(), factory.enumMembers.ordinalField));
          return null;
        }
        int ordinal = optionalOrdinal.getAsInt();
        unboxedValues.put(staticField.getReference(), ordinalToUnboxedInt(ordinal));
        ordinalToObjectState.put(ordinal, enumState);
      } else if (factory.enumMembers.isValuesFieldCandidate(staticField, enumClass.type)) {
        ObjectState valuesState =
            enumStaticFieldValues.getObjectStateForPossiblyPinnedField(staticField.getReference());
        if (valuesState == null) {
          if (staticField.getOptimizationInfo().isDead()) {
            // We don't care about unused field data.
            continue;
          }
          // We could not track the content of that field. We bail out.
          // We could not track the content of that field, and the field could be a values field.
          // We conservatively bail out.
          reportFailure(
              enumClass, new MissingContentsForEnumValuesArrayReason(staticField.getReference()));
          return null;
        }
        assert valuesState.isEnumValuesObjectState();
        assert valuesContents == null
            || valuesContents.equals(valuesState.asEnumValuesObjectState());
        valuesContents = valuesState.asEnumValuesObjectState();
        valuesField.add(staticField.getReference());
      }
    }

    // Step 2: We complete the information based on the values content, since some enum instances
    // may be reachable only though the $VALUES field.
    if (valuesContents != null) {
      for (int ordinal = 0; ordinal < valuesContents.getEnumValuesSize(); ordinal++) {
        if (!ordinalToObjectState.containsKey(ordinal)) {
          ObjectState enumState = valuesContents.getObjectStateForOrdinal(ordinal);
          if (enumState.isEmpty()) {
            // If $VALUES is used, we need data for all enums, at least the ordinal.
            return null;
          }
          assert getOrdinal(enumState).isPresent();
          assert getOrdinal(enumState).getAsInt() == ordinal;
          ordinalToObjectState.put(ordinal, enumState);
        }
      }
    }

    // The ordinalToObjectState map may have holes at this point, if some enum instances are never
    // used ($VALUES unused or removed, and enum instance field unused or removed), it contains
    // only data for reachable enum instance, that is what we're interested in.
    ImmutableMap<DexField, EnumInstanceFieldKnownData> instanceFieldsData =
        computeRequiredEnumInstanceFieldsData(enumClass, instanceFields, ordinalToObjectState);
    if (instanceFieldsData == null) {
      return null;
    }

    return new EnumData(
        instanceFieldsData,
        unboxedValues.build(),
        valuesField.build(),
        valuesContents == null ? EnumData.INVALID_VALUES_SIZE : valuesContents.getEnumValuesSize());
  }

  private ImmutableMap<DexField, EnumInstanceFieldKnownData> computeRequiredEnumInstanceFieldsData(
      DexProgramClass enumClass,
      Set<DexField> instanceFields,
      Int2ReferenceMap<ObjectState> ordinalToObjectState) {
    ImmutableMap.Builder<DexField, EnumInstanceFieldKnownData> builder = ImmutableMap.builder();
    for (DexField instanceField : instanceFields) {
      EnumInstanceFieldData fieldData =
          computeRequiredEnumInstanceFieldData(instanceField, enumClass, ordinalToObjectState);
      if (fieldData.isUnknown()) {
        if (!debugLogEnabled) {
          return null;
        }
        builder = null;
      }
      if (builder != null) {
        builder.put(instanceField, fieldData.asEnumFieldKnownData());
      }
    }
    return builder != null ? builder.build() : null;
  }

  private EnumInstanceFieldData computeRequiredEnumInstanceFieldData(
      DexField instanceField,
      DexProgramClass enumClass,
      Int2ReferenceMap<ObjectState> ordinalToObjectState) {
    DexEncodedField encodedInstanceField =
        appView.appInfo().resolveFieldOn(enumClass, instanceField).getResolvedField();
    assert encodedInstanceField != null;
    boolean canBeOrdinal = instanceField.type.isIntType();
    ImmutableInt2ReferenceSortedMap.Builder<AbstractValue> data =
        ImmutableInt2ReferenceSortedMap.builder();
    for (Integer ordinal : ordinalToObjectState.keySet()) {
      ObjectState state = ordinalToObjectState.get(ordinal);
      AbstractValue fieldValue = state.getAbstractFieldValue(encodedInstanceField);
      if (!fieldValue.isSingleValue()) {
        reportFailure(
            enumClass, new MissingInstanceFieldValueForEnumInstanceReason(ordinal, instanceField));
        return EnumInstanceFieldUnknownData.getInstance();
      }
      if (!(fieldValue.isSingleNumberValue() || fieldValue.isSingleStringValue())) {
        reportFailure(
            enumClass,
            new UnsupportedInstanceFieldValueForEnumInstanceReason(ordinal, instanceField));
        return EnumInstanceFieldUnknownData.getInstance();
      }
      data.put(ordinalToUnboxedInt(ordinal), fieldValue);
      if (canBeOrdinal) {
        assert fieldValue.isSingleNumberValue();
        int computedValue = fieldValue.asSingleNumberValue().getIntValue();
        if (computedValue != ordinal) {
          canBeOrdinal = false;
        }
      }
    }
    if (canBeOrdinal) {
      return new EnumInstanceFieldOrdinalData();
    }
    return new EnumInstanceFieldMappingData(data.build());
  }

  private OptionalInt getOrdinal(ObjectState state) {
    AbstractValue field = state.getAbstractFieldValue(ordinalField);
    if (field.isSingleNumberValue()) {
      return OptionalInt.of(field.asSingleNumberValue().getIntValue());
    }
    return OptionalInt.empty();
  }

  public void recordEnumState(DexProgramClass clazz, StaticFieldValues staticFieldValues) {
    if (staticFieldValues == null || !staticFieldValues.isEnumStaticFieldValues()) {
      return;
    }
    assert clazz.isEnum();
    EnumStaticFieldValues enumStaticFieldValues = staticFieldValues.asEnumStaticFieldValues();
    if (getEnumUnboxingCandidateOrNull(clazz.type) != null) {
      staticFieldValuesMap.put(clazz.type, enumStaticFieldValues);
    }
  }

  private class EnumAccessibilityUseRegistry extends UseRegistry {

    private ProgramMethod context;
    private Constraint constraint;

    public EnumAccessibilityUseRegistry(DexItemFactory factory) {
      super(factory);
    }

    public Constraint computeConstraint(ProgramMethod method) {
      constraint = Constraint.ALWAYS;
      context = method;
      method.registerCodeReferences(this);
      return constraint;
    }

    public Constraint deriveConstraint(DexType targetHolder, AccessFlags<?> flags) {
      DexProgramClass contextHolder = context.getHolder();
      if (targetHolder == contextHolder.type) {
        return Constraint.ALWAYS;
      }
      if (flags.isPublic()) {
        return Constraint.ALWAYS;
      }
      if (flags.isPrivate()) {
        // Enum unboxing is currently happening only cf to dex, and no class should be in a nest
        // at this point. If that is the case, we just don't unbox the enum, or we would need to
        // support Constraint.SAMENEST in the enum unboxer.
        assert !contextHolder.isInANest();
        // Only accesses within the enum are allowed since all enum methods and fields will be
        // moved to the same class, and the enum itself becomes an integer, which is
        // accessible everywhere.
        return Constraint.NEVER;
      }
      assert flags.isProtected() || flags.isPackagePrivate();
      // Protected is in practice equivalent to package private in this analysis since we are
      // accessing the member from an enum context where subclassing is limited.
      // At this point we don't support unboxing enums with subclasses, so we assume either
      // same package access, or we just don't unbox.
      // The only protected methods in java.lang.Enum are clone, finalize and the constructor.
      // Besides calls to the constructor in the instance initializer, Enums with calls to such
      // methods cannot be unboxed.
      return targetHolder.isSamePackage(contextHolder.type) ? Constraint.PACKAGE : Constraint.NEVER;
    }

    @Override
    public void registerTypeReference(DexType type) {
      if (type.isArrayType()) {
        registerTypeReference(type.toBaseType(factory));
        return;
      }

      if (type.isPrimitiveType()) {
        return;
      }

      DexClass definition = appView.definitionFor(type);
      if (definition == null) {
        constraint = Constraint.NEVER;
        return;
      }
      constraint = constraint.meet(deriveConstraint(type, definition.accessFlags));
    }

    @Override
    public void registerInitClass(DexType type) {
      registerTypeReference(type);
    }

    @Override
    public void registerInstanceOf(DexType type) {
      registerTypeReference(type);
    }

    @Override
    public void registerNewInstance(DexType type) {
      registerTypeReference(type);
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerVirtualInvoke(method, false);
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerVirtualInvoke(method, true);
    }

    private void registerVirtualInvoke(DexMethod method, boolean isInterface) {
      if (method.holder.isArrayType()) {
        return;
      }
      // Perform resolution and derive unboxing constraints based on the accessibility of the
      // resolution result.
      MethodResolutionResult resolutionResult =
          appView.appInfo().resolveMethod(method, isInterface);
      if (!resolutionResult.isVirtualTarget()) {
        constraint = Constraint.NEVER;
        return;
      }
      registerTarget(
          resolutionResult.getInitialResolutionHolder(), resolutionResult.getSingleTarget());
    }

    private void registerTarget(DexClass initialResolutionHolder, DexEncodedMember<?, ?> target) {
      if (target == null) {
        // This will fail at runtime.
        constraint = Constraint.NEVER;
        return;
      }
      DexType resolvedHolder = target.getHolderType();
      if (initialResolutionHolder == null) {
        constraint = Constraint.NEVER;
        return;
      }
      Constraint memberConstraint = deriveConstraint(resolvedHolder, target.getAccessFlags());
      // We also have to take the constraint of the initial resolution holder into account.
      Constraint classConstraint =
          deriveConstraint(initialResolutionHolder.type, initialResolutionHolder.accessFlags);
      Constraint instructionConstraint = memberConstraint.meet(classConstraint);
      constraint = instructionConstraint.meet(constraint);
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerSingleTargetInvoke(method, DexEncodedMethod::isDirectMethod);
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerSingleTargetInvoke(method, DexEncodedMethod::isStatic);
    }

    private void registerSingleTargetInvoke(
        DexMethod method, Predicate<DexEncodedMethod> methodValidator) {
      if (method.holder.isArrayType()) {
        return;
      }
      MethodResolutionResult resolutionResult =
          appView.appInfo().unsafeResolveMethodDueToDexFormat(method);
      DexEncodedMethod target = resolutionResult.getSingleTarget();
      if (target == null || !methodValidator.test(target)) {
        constraint = Constraint.NEVER;
        return;
      }
      registerTarget(resolutionResult.getInitialResolutionHolder(), target);
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      // Invoke-super can only target java.lang.Enum methods since we do not unbox enums with
      // subclasses. Calls to java.lang.Object methods would have resulted in the enum to be marked
      // as unboxable. The methods of java.lang.Enum called are already analyzed in the enum
      // unboxer analysis, so invoke-super is always valid.
      assert method.holder == factory.enumType;
    }

    @Override
    public void registerCallSite(DexCallSite callSite) {
      // This is reached after lambda desugaring, so this should not be a lambda call site.
      // We do not unbox enums with invoke custom since it's not clear the accessibility
      // constraints would be correct if the method holding the invoke custom is moved to
      // another class.
      assert appView.options().isGeneratingClassFiles()
          || !factory.isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());
      constraint = Constraint.NEVER;
    }

    private void registerFieldInstruction(DexField field) {
      FieldResolutionResult fieldResolutionResult = appView.appInfo().resolveField(field, context);
      registerTarget(
          fieldResolutionResult.getInitialResolutionHolder(),
          fieldResolutionResult.getResolvedField());
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldInstruction(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldInstruction(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldInstruction(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldInstruction(field);
    }
  }

  private void analyzeInitializers() {
    enumUnboxingCandidatesInfo.forEachCandidate(
        enumClass -> {
          for (DexEncodedMethod directMethod : enumClass.directMethods()) {
            if (directMethod.isInstanceInitializer()) {
              if (directMethod
                  .getOptimizationInfo()
                  .getContextInsensitiveInstanceInitializerInfo()
                  .mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
                if (markEnumAsUnboxable(Reason.INVALID_INIT, enumClass)) {
                  break;
                }
              }
            }
          }
          if (enumClass.classInitializationMayHaveSideEffects(appView)) {
            markEnumAsUnboxable(Reason.INVALID_CLINIT, enumClass);
          }
        });
  }

  private Reason instructionAllowEnumUnboxing(
      Instruction instruction, IRCode code, DexProgramClass enumClass, Value enumValue) {
    ProgramMethod context = code.context();
    switch (instruction.opcode()) {
      case ASSUME:
        return analyzeAssumeUser(instruction.asAssume(), code, context, enumClass, enumValue);
      case ARRAY_GET:
        return analyzeArrayGetUser(instruction.asArrayGet(), code, context, enumClass, enumValue);
      case ARRAY_LENGTH:
        return analyzeArrayLengthUser(
            instruction.asArrayLength(), code, context, enumClass, enumValue);
      case ARRAY_PUT:
        return analyzeArrayPutUser(instruction.asArrayPut(), code, context, enumClass, enumValue);
      case CHECK_CAST:
        return analyzeCheckCastUser(instruction.asCheckCast(), code, context, enumClass, enumValue);
      case IF:
        return analyzeIfUser(instruction.asIf(), code, context, enumClass, enumValue);
      case INSTANCE_GET:
        return analyzeInstanceGetUser(
            instruction.asInstanceGet(), code, context, enumClass, enumValue);
      case INSTANCE_PUT:
        return analyzeFieldPutUser(
            instruction.asInstancePut(), code, context, enumClass, enumValue);
      case INVOKE_DIRECT:
      case INVOKE_INTERFACE:
      case INVOKE_STATIC:
      case INVOKE_SUPER:
      case INVOKE_VIRTUAL:
        return analyzeInvokeUser(instruction.asInvokeMethod(), code, context, enumClass, enumValue);
      case RETURN:
        return analyzeReturnUser(instruction.asReturn(), code, context, enumClass, enumValue);
      case STATIC_PUT:
        return analyzeFieldPutUser(instruction.asStaticPut(), code, context, enumClass, enumValue);
      default:
        return Reason.OTHER_UNSUPPORTED_INSTRUCTION;
    }
  }

  private Reason analyzeAssumeUser(
      Assume assume,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    return validateEnumUsages(code, assume.outValue(), enumClass);
  }

  private Reason analyzeArrayGetUser(
      ArrayGet arrayGet,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    // MyEnum[] array = ...; array[0]; is valid.
    return Reason.ELIGIBLE;
  }

  private Reason analyzeArrayLengthUser(
      ArrayLength arrayLength,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    // MyEnum[] array = ...; array.length; is valid.
    return Reason.ELIGIBLE;
  }

  private Reason analyzeArrayPutUser(
      ArrayPut arrayPut,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    // MyEnum[] array; array[0] = MyEnum.A; is valid.
    // MyEnum[][] array2d; MyEnum[] array; array2d[0] = array; is valid.
    // MyEnum[]^N array; MyEnum[]^(N-1) element; array[0] = element; is valid.
    // We need to prove that the value to put in and the array have correct types.
    assert arrayPut.getMemberType() == MemberType.OBJECT;
    TypeElement arrayType = arrayPut.array().getType();
    assert arrayType.isArrayType();
    assert arrayType.asArrayType().getBaseType().isClassType();
    ClassTypeElement arrayBaseType = arrayType.asArrayType().getBaseType().asClassType();
    TypeElement valueBaseType = arrayPut.value().getType();
    if (valueBaseType.isArrayType()) {
      assert valueBaseType.asArrayType().getBaseType().isClassType();
      assert valueBaseType.asArrayType().getNesting() == arrayType.asArrayType().getNesting() - 1;
      valueBaseType = valueBaseType.asArrayType().getBaseType();
    }
    if (arrayBaseType.equalUpToNullability(valueBaseType)
        && arrayBaseType.getClassType() == enumClass.type) {
      return Reason.ELIGIBLE;
    }
    return Reason.INVALID_ARRAY_PUT;
  }

  private Reason analyzeCheckCastUser(
      CheckCast checkCast,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    if (allowCheckCast(checkCast)) {
      return Reason.ELIGIBLE;
    }
    return Reason.DOWN_CAST;
  }

  // A field put is valid only if the field is not on an enum, and the field type and the valuePut
  // have identical enum type.
  private Reason analyzeFieldPutUser(
      FieldInstruction fieldPut,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    assert fieldPut.isInstancePut() || fieldPut.isStaticPut();
    DexEncodedField field = appView.appInfo().resolveField(fieldPut.getField()).getResolvedField();
    if (field == null) {
      return Reason.INVALID_FIELD_PUT;
    }
    DexProgramClass dexClass = appView.programDefinitionFor(field.getHolderType(), code.context());
    if (dexClass == null) {
      return Reason.INVALID_FIELD_PUT;
    }
    if (fieldPut.isInstancePut() && fieldPut.asInstancePut().object() == enumValue) {
      return Reason.ELIGIBLE;
    }
    // The put value has to be of the field type.
    if (field.getReference().type.toBaseType(factory) != enumClass.type) {
      return Reason.TYPE_MISMATCH_FIELD_PUT;
    }
    return Reason.ELIGIBLE;
  }

  // An If using enum as inValue is valid if it matches e == null
  // or e == X with X of same enum type as e. Ex: if (e == MyEnum.A).
  private Reason analyzeIfUser(
      If theIf, IRCode code, ProgramMethod context, DexProgramClass enumClass, Value enumValue) {
    assert (theIf.getType() == If.Type.EQ || theIf.getType() == If.Type.NE)
        : "Comparing a reference with " + theIf.getType().toString();
    // e == null.
    if (theIf.isZeroTest()) {
      return Reason.ELIGIBLE;
    }
    // e == MyEnum.X
    TypeElement leftType = theIf.lhs().getType();
    TypeElement rightType = theIf.rhs().getType();
    if (leftType.equalUpToNullability(rightType)) {
      assert leftType.isClassType();
      assert leftType.asClassType().getClassType() == enumClass.type;
      return Reason.ELIGIBLE;
    }
    return Reason.INVALID_IF_TYPES;
  }

  private Reason analyzeInstanceGetUser(
      InstanceGet instanceGet,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    assert instanceGet.getField().holder == enumClass.type;
    DexField field = instanceGet.getField();
    enumUnboxingCandidatesInfo.addRequiredEnumInstanceFieldData(enumClass, field);
    return Reason.ELIGIBLE;
  }

  // All invokes in the library are invalid, besides a few cherry picked cases such as ordinal().
  private Reason analyzeInvokeUser(
      InvokeMethod invoke,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    if (invoke.getInvokedMethod().holder.isArrayType()) {
      // The only valid methods is clone for values() to be correct.
      if (invoke.getInvokedMethod().name == factory.cloneMethodName) {
        return Reason.ELIGIBLE;
      }
      return Reason.INVALID_INVOKE_ON_ARRAY;
    }
    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
    if (singleTarget == null) {
      return Reason.INVALID_INVOKE;
    }
    DexMethod singleTargetReference = singleTarget.getReference();
    DexClass targetHolder = singleTarget.getHolder();
    if (targetHolder.isProgramClass()) {
      if (targetHolder.isEnum() && singleTarget.getDefinition().isInstanceInitializer()) {
        if (code.context().getHolder() == targetHolder && code.method().isClassInitializer()) {
          // The enum instance initializer is allowed to be called only from the enum clinit.
          return Reason.ELIGIBLE;
        } else {
          return Reason.INVALID_INIT;
        }
      }

      // Check if this is a checkNotNull() user. In this case, we can create a copy of the method
      // that takes an int instead of java.lang.Object and call that method instead.
      EnumUnboxerMethodClassification classification =
          singleTarget.getOptimizationInfo().getEnumUnboxerMethodClassification();
      if (classification.isCheckNotNullClassification()) {
        CheckNotNullEnumUnboxerMethodClassification checkNotNullClassification =
            classification.asCheckNotNullClassification();
        if (checkNotNullClassification.isUseEligibleForUnboxing(
            invoke.asInvokeStatic(), enumValue)) {
          checkNotNullMethods
              .computeIfAbsent(
                  singleTarget.asProgramMethod(), ignoreKey(Sets::newConcurrentHashSet))
              .add(enumClass);
          return Reason.ELIGIBLE;
        }
      }

      // Check that the enum-value only flows into parameters whose type exactly matches the
      // enum's type.
      for (int i = 0; i < singleTarget.getParameters().size(); i++) {
        if (invoke.getArgumentForParameter(i) == enumValue
            && singleTarget.getParameter(i).toBaseType(factory) != enumClass.getType()) {
          return new IllegalInvokeWithImpreciseParameterTypeReason(singleTargetReference);
        }
      }
      if (invoke.isInvokeMethodWithReceiver()) {
        Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
        if (receiver == enumValue && targetHolder.isInterface()) {
          return Reason.DEFAULT_METHOD_INVOKE;
        }
      }
      return Reason.ELIGIBLE;
    }

    if (targetHolder.isClasspathClass()) {
      return Reason.INVALID_INVOKE_CLASSPATH;
    }

    assert targetHolder.isLibraryClass();

    Reason reason =
        analyzeLibraryInvoke(
            invoke, code, context, enumClass, enumValue, singleTargetReference, targetHolder);

    if (reason == Reason.ELIGIBLE) {
      markMethodDependsOnLibraryModelisation(context);
    }

    return reason;
  }

  private Reason analyzeLibraryInvoke(
      InvokeMethod invoke,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue,
      DexMethod singleTargetReference,
      DexClass targetHolder) {
    // Calls to java.lang.Enum.
    if (targetHolder.getType() == factory.enumType) {
      // TODO(b/147860220): EnumSet and EnumMap may be interesting to model.
      if (singleTargetReference == factory.enumMembers.compareTo
          || singleTargetReference == factory.enumMembers.compareToWithObject) {
        DexProgramClass otherEnumClass =
            getEnumUnboxingCandidateOrNull(invoke.getLastArgument().getType());
        if (otherEnumClass == enumClass || invoke.getLastArgument().getType().isNullType()) {
          return Reason.ELIGIBLE;
        }
      } else if (singleTargetReference == factory.enumMembers.equals) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.nameMethod
          || singleTargetReference == factory.enumMembers.toString) {
        assert invoke.asInvokeMethodWithReceiver().getReceiver() == enumValue;
        addRequiredNameData(enumClass);
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.ordinalMethod) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.hashCode) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.constructor) {
        // Enum constructor call is allowed only if called from an enum initializer.
        if (code.method().isInstanceInitializer() && code.context().getHolder() == enumClass) {
          return Reason.ELIGIBLE;
        }
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Calls to java.lang.Object.
    if (targetHolder.getType() == factory.objectType) {
      // Object#getClass without outValue is important since R8 rewrites explicit null checks to
      // such instructions.
      if (singleTargetReference == factory.objectMembers.getClass && invoke.hasUnusedOutValue()) {
        // This is a hidden null check.
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Calls to java.lang.Objects.
    if (targetHolder.getType() == factory.objectsType) {
      // Objects#requireNonNull is important since R8 rewrites explicit null checks to such
      // instructions.
      if (singleTargetReference == factory.objectsMethods.requireNonNull
          || singleTargetReference == factory.objectsMethods.requireNonNullWithMessage) {
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Calls to java.lang.String.
    if (targetHolder.getType() == factory.stringType) {
      if (singleTargetReference == factory.stringMembers.valueOf) {
        addRequiredNameData(enumClass);
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Calls to java.lang.StringBuilder and java.lang.StringBuffer.
    if (targetHolder.getType() == factory.stringBuilderType
        || targetHolder.getType() == factory.stringBufferType) {
      if (singleTargetReference == factory.stringBuilderMethods.appendObject
          || singleTargetReference == factory.stringBufferMethods.appendObject) {
        addRequiredNameData(enumClass);
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Calls to java.lang.System.
    if (targetHolder.getType() == factory.javaLangSystemType) {
      if (singleTargetReference == factory.javaLangSystemMethods.arraycopy) {
        // Important for Kotlin 1.5 enums, which use arraycopy to create a copy of $VALUES instead
        // of int[].clone().
        return Reason.ELIGIBLE;
      }
      if (singleTargetReference == factory.javaLangSystemMethods.identityHashCode) {
        // Important for proto enum unboxing.
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Unsupported holder.
    return new UnsupportedLibraryInvokeReason(singleTargetReference);
  }

  // Return is used for valueOf methods.
  private Reason analyzeReturnUser(
      Return theReturn,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    DexType returnType = context.getReturnType();
    if (returnType != enumClass.type && returnType.toBaseType(factory) != enumClass.type) {
      return Reason.IMPLICIT_UP_CAST_IN_RETURN;
    }
    return Reason.ELIGIBLE;
  }

  private void reportEnumsAnalysis() {
    assert debugLogEnabled;
    Reporter reporter = appView.reporter();
    Set<DexType> candidates = enumUnboxingCandidatesInfo.candidates();
    reporter.info(
        new StringDiagnostic(
            "Unboxed " + candidates.size() + " enums: " + Arrays.toString(candidates.toArray())));

    StringBuilder sb =
        new StringBuilder("Unable to unbox ")
            .append(debugLogs.size())
            .append(" enums.")
            .append(System.lineSeparator())
            .append(System.lineSeparator());

    // Sort by the number of reasons that prevent enum unboxing.
    TreeMap<DexType, List<Reason>> sortedDebugLogs =
        new TreeMap<>(
            Comparator.<DexType>comparingInt(x -> debugLogs.get(x).size())
                .thenComparing(Function.identity()));
    sortedDebugLogs.putAll(debugLogs);

    // Print the pinned enums and remove them from further reporting.
    List<DexType> pinned = new ArrayList<>();
    Iterator<Entry<DexType, List<Reason>>> sortedDebugLogIterator =
        sortedDebugLogs.entrySet().iterator();
    while (sortedDebugLogIterator.hasNext()) {
      Entry<DexType, List<Reason>> entry = sortedDebugLogIterator.next();
      List<Reason> reasons = entry.getValue();
      if (reasons.size() > 1) {
        break;
      }
      if (reasons.get(0) == Reason.PINNED) {
        pinned.add(entry.getKey());
        sortedDebugLogIterator.remove();
      }
    }
    if (!pinned.isEmpty()) {
      sb.append("Pinned: ").append(Arrays.toString(pinned.toArray()));
    }

    // Print the reasons for each unboxable enum.
    sortedDebugLogs.forEach(
        (type, reasons) -> {
          sb.append(type).append(" (").append(reasons.size()).append(" reasons):");
          HashMultiset.create(reasons)
              .forEachEntry(
                  (reason, count) ->
                      sb.append(System.lineSeparator())
                          .append(" - ")
                          .append(reason)
                          .append(" (")
                          .append(count)
                          .append(")"));
          sb.append(System.lineSeparator());
        });

    sb.append(System.lineSeparator());

    // Print information about how often a given Reason kind prevents enum unboxing.
    Object2IntMap<Object> reasonKindCount = new Object2IntOpenHashMap<>();
    debugLogs.forEach(
        (type, reasons) ->
            reasons.forEach(
                reason ->
                    reasonKindCount.put(reason.getKind(), reasonKindCount.getInt(reason) + 1)));
    List<Object> differentReasonKinds = new ArrayList<>(reasonKindCount.keySet());
    differentReasonKinds.sort(
        (reasonKind, other) -> {
          int freq = reasonKindCount.getInt(reasonKind) - reasonKindCount.getInt(other);
          return freq != 0
              ? freq
              : System.identityHashCode(reasonKind) - System.identityHashCode(other);
        });
    differentReasonKinds.forEach(
        reasonKind ->
            sb.append(reasonKind)
                .append(" (")
                .append(reasonKindCount.getInt(reasonKind))
                .append(")")
                .append(System.lineSeparator()));

    reporter.info(new StringDiagnostic(sb.toString()));
  }

  boolean reportFailure(DexProgramClass enumClass, Reason reason) {
    return reportFailure(enumClass.getType(), reason);
  }

  /** Returns true if the failure was reported. */
  boolean reportFailure(DexType enumType, Reason reason) {
    if (debugLogEnabled) {
      debugLogs
          .computeIfAbsent(enumType, ignore -> Collections.synchronizedList(new ArrayList<>()))
          .add(reason);
      return true;
    }
    return false;
  }

  public Set<Phi> rewriteCode(IRCode code, MethodProcessor methodProcessor) {
    // This has no effect during primary processing since the enumUnboxerRewriter is set
    // in between primary and post processing.
    if (enumUnboxerRewriter != null) {
      return enumUnboxerRewriter.rewriteCode(code, methodProcessor);
    }
    return Sets.newIdentityHashSet();
  }

  public void unsetRewriter() {
    enumUnboxerRewriter = null;
  }
}
