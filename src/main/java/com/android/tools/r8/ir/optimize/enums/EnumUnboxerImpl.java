// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_GET;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_LENGTH;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INIT_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_CUSTOM;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_FILLED;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues.EnumStaticFieldValues;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.objectstate.EnumValuesObjectState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
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
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor.Builder;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
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
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingExactDynamicEnumTypeForEnumWithSubtypesReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingInstanceFieldValueForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.MissingObjectStateForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.UnboxedValueNonComparable;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.UnsupportedInstanceFieldValueForEnumInstanceReason;
import com.android.tools.r8.ir.optimize.enums.eligibility.Reason.UnsupportedLibraryInvokeReason;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import com.android.tools.r8.utils.collections.LongLivedClassSetBuilder;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodMapBuilder;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
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
import java.util.function.Consumer;
import java.util.function.Function;

public class EnumUnboxerImpl extends EnumUnboxer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  // Map the enum candidates with their dependencies, i.e., the methods to reprocess for the given
  // enum if the optimization eventually decides to unbox it.
  private EnumUnboxingCandidateInfoCollection enumUnboxingCandidatesInfo;
  private final Set<DexProgramClass> candidatesToRemoveInWave = SetUtils.newConcurrentHashSet();
  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMap =
      new ConcurrentHashMap<>();

  // Methods depending on library modelisation need to be reprocessed so they are peephole
  // optimized.
  private LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodsDependingOnLibraryModelisation;

  // Map from checkNotNull() methods to the enums that use the given method.
  private LongLivedProgramMethodMapBuilder<LongLivedClassSetBuilder<DexProgramClass>>
      checkNotNullMethodsBuilder;

  private final DexClassAndField ordinalField;

  private EnumUnboxingRewriter enumUnboxerRewriter;

  private final boolean debugLogEnabled;
  private final Map<DexType, List<Reason>> debugLogs;

  EnumUnboxerImpl(AppView<AppInfoWithLiveness> appView) {
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
    ordinalField =
        appView.appInfo().resolveField(factory.enumMembers.ordinalField).getResolutionPair();
  }

  public static int unboxedIntToOrdinal(int unboxedInt) {
    return unboxedInt - 1;
  }

  public static int ordinalToUnboxedInt(int ordinal) {
    return ordinal + 1;
  }

  public DexClassAndField getOrdinalField() {
    return ordinalField;
  }

  @Override
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
    methodsDependingOnLibraryModelisation.add(method, appView.graphLens());
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
    if (type.isArrayType()) {
      return getEnumUnboxingCandidateOrNull(type.toBaseType(appView.dexItemFactory()));
    }
    if (type.isPrimitiveType() || type.isVoidType()) {
      return null;
    }
    assert type.isClassType();
    return enumUnboxingCandidatesInfo.getCandidateClassOrNull(type);
  }

  @Override
  public void analyzeEnums(IRCode code, MethodProcessor methodProcessor) {
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
            addNullDependencies(code, outValue, eligibleEnums);
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
          case CONST_CLASS:
            analyzeConstClass(instruction.asConstClass(), eligibleEnums, code.context());
            break;
          case CHECK_CAST:
            analyzeCheckCast(instruction.asCheckCast(), eligibleEnums);
            break;
          case INIT_CLASS:
            analyzeInitClass(instruction.asInitClass(), eligibleEnums);
            break;
          case INVOKE_CUSTOM:
            analyzeInvokeCustom(instruction.asInvokeCustom(), eligibleEnums, code.context());
            break;
          case INVOKE_STATIC:
            analyzeInvokeStatic(instruction.asInvokeStatic(), eligibleEnums, code.context());
            break;
          case STATIC_GET:
          case INSTANCE_GET:
          case STATIC_PUT:
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
          addNullDependencies(code, phi, eligibleEnums);
        }
      }
    }
    if (!eligibleEnums.isEmpty()) {
      for (DexType eligibleEnum : eligibleEnums) {
        enumUnboxingCandidatesInfo.addMethodDependency(eligibleEnum, code.context());
      }
    }
    // TODO(b/225838009): Remove this when always using LIR.
    if (!appView.testing().canUseLir(appView)) {
      if (methodsDependingOnLibraryModelisation.contains(code.context(), appView.graphLens())) {
        code.mutateConversionOptions(
            conversionOptions -> conversionOptions.disablePeepholeOptimizations(methodProcessor));
      }
    }
  }

  private void markEnumEligible(DexType type, Set<DexType> eligibleEnums) {
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(type);
    if (enumClass != null) {
      eligibleEnums.add(enumClass.getType());
    }
  }

  private void invalidateEnum(DexType type) {
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(type);
    if (enumClass != null) {
      markEnumAsUnboxable(Reason.INVALID_INVOKE_CUSTOM, enumClass);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void analyzeInvokeCustom(
      InvokeCustom invoke, Set<DexType> eligibleEnums, ProgramMethod context) {
    invoke.getCallSite().getMethodProto().forEachType(t -> markEnumEligible(t, eligibleEnums));
    LambdaDescriptor lambdaDescriptor =
        LambdaDescriptor.tryInfer(invoke.getCallSite(), appView, appView.appInfo(), context);
    if (lambdaDescriptor == null) {
      // Based on lambda we can see that enums cannot be unboxed if used in call site bootstrap
      // arguments, since there might be expectations on overrides. Enums used directly in the
      // method proto should be fine.
      analyzeInvokeCustomParameters(invoke, this::invalidateEnum);
      return;
    }

    analyzeInvokeCustomParameters(invoke, t -> markEnumEligible(t, eligibleEnums));

    lambdaDescriptor.forEachErasedAndEnforcedTypes(
        (erasedType, enforcedType) -> {
          if (erasedType != enforcedType) {
            invalidateEnum(erasedType);
            invalidateEnum(enforcedType);
          }
        });
  }

  private void analyzeInvokeCustomParameters(InvokeCustom invoke, Consumer<DexType> nonHolder) {
    invoke
        .getCallSite()
        .getBootstrapArgs()
        .forEach(
            bootstrapArgument -> {
              if (bootstrapArgument.isDexValueMethodHandle()) {
                DexMethodHandle methodHandle =
                    bootstrapArgument.asDexValueMethodHandle().getValue();
                if (methodHandle.isMethodHandle()) {
                  DexMethod method = methodHandle.asMethod();
                  invalidateEnum(method.getHolderType());
                  method.getProto().forEachType(nonHolder);
                } else {
                  assert methodHandle.isFieldHandle();
                  DexField field = methodHandle.asField();
                  invalidateEnum(field.getHolderType());
                  nonHolder.accept(field.type);
                }
              } else if (bootstrapArgument.isDexValueMethodType()) {
                DexProto proto = bootstrapArgument.asDexValueMethodType().getValue();
                proto.forEachType(nonHolder);
              }
            });
  }

  private void analyzeFieldInstruction(
      FieldInstruction fieldInstruction, Set<DexType> eligibleEnums, ProgramMethod context) {
    DexField field = fieldInstruction.getField();
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(field.getHolderType());
    if (enumClass != null) {
      FieldResolutionResult resolutionResult = appView.appInfo().resolveField(field, context);
      if (resolutionResult.isSingleFieldResolutionResult()) {
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

  private void analyzeInitClass(InitClass initClass, Set<DexType> eligibleEnums) {
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(initClass.getClassValue());
    if (enumClass != null) {
      eligibleEnums.add(enumClass.getType());
    }
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

  @SuppressWarnings("ReferenceEquality")
  private boolean isLegitimateConstClassUser(
      Instruction user, ProgramMethod context, DexProgramClass enumClass) {
    if (user.isAssume()) {
      if (user.outValue().hasPhiUsers()) {
        return false;
      }
      return true;
    }

    if (user.isInvokeStatic()) {
      InvokeStatic invoke = user.asInvokeStatic();
      DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
      if (singleTarget == null) {
        return false;
      }
      if (singleTarget.getReference() == factory.enumMembers.valueOf) {
        // The name data is required for the correct mapping from the enum name to the ordinal
        // in the valueOf utility method.
        addRequiredNameData(enumClass);
        markMethodDependsOnLibraryModelisation(context);
        // The out-value must be cast before it is used, or an assume instruction must strengthen
        // its dynamic type, so that the out-value is analyzed by the enum unboxing analysis.
        if (invoke.hasOutValue()) {
          if (invoke.outValue().hasPhiUsers()) {
            return false;
          }
          for (Instruction enumUser : invoke.outValue().uniqueUsers()) {
            if (enumUser.isAssumeWithDynamicTypeAssumption()) {
              Assume assume = enumUser.asAssume();
              if (assume
                  .getDynamicTypeAssumption()
                  .getDynamicUpperBoundType()
                  .equalUpToNullability(enumClass.getType().toTypeElement(appView))) {
                // OK.
                continue;
              }
            } else if (enumUser.isCheckCast()) {
              CheckCast checkCast = enumUser.asCheckCast();
              if (checkCast.getType() == enumClass.getType()) {
                // OK.
                continue;
              }
            }
            return false;
          }
        }
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

  @SuppressWarnings("ReferenceEquality")
  private boolean isUnboxableNameMethod(DexMethod method) {
    return method == factory.classMethods.getName
        || method == factory.classMethods.getCanonicalName
        || method == factory.classMethods.getSimpleName;
  }

  private void addNullDependencies(IRCode code, Value nullValue, Set<DexType> eligibleEnums) {
    for (Instruction use : nullValue.uniqueUsers()) {
      if (use.isInvokeMethod()) {
        InvokeMethod invokeMethod = use.asInvokeMethod();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        for (DexType paramType : invokedMethod.proto.parameters.values) {
          if (enumUnboxingCandidatesInfo.isCandidate(paramType)) {
            eligibleEnums.add(paramType);
          }
        }
        if (invokeMethod.isInvokeMethodWithReceiver()
            && invokeMethod.asInvokeMethodWithReceiver().getReceiver() == nullValue) {
          DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
          if (enumClass != null) {
            markEnumAsUnboxable(Reason.ENUM_METHOD_CALLED_WITH_NULL_RECEIVER, enumClass);
          }
        }
      } else if (use.isNewArrayFilled()) {
        DexProgramClass enumClass =
            getEnumUnboxingCandidateOrNull(
                use.asNewArrayFilled().getArrayType().toBaseType(factory));
        if (enumClass != null) {
          eligibleEnums.add(enumClass.getType());
        }
      } else if (use.isFieldPut()) {
        DexProgramClass enumClass =
            getEnumUnboxingCandidateOrNull(use.asFieldInstruction().getField().getType());
        if (enumClass != null) {
          eligibleEnums.add(enumClass.getType());
        }
      } else if (use.isReturn()) {
        DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(code.context().getReturnType());
        if (enumClass != null) {
          eligibleEnums.add(enumClass.getType());
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

  @Override
  public void prepareForPrimaryOptimizationPass(GraphLens graphLensForPrimaryOptimizationPass) {
    assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
    initializeCheckNotNullMethods(graphLensForPrimaryOptimizationPass);
    initializeEnumUnboxingCandidates(graphLensForPrimaryOptimizationPass);
  }

  private void initializeCheckNotNullMethods(GraphLens graphLensForPrimaryOptimizationPass) {
    assert checkNotNullMethodsBuilder == null;
    checkNotNullMethodsBuilder =
        LongLivedProgramMethodMapBuilder.createConcurrentBuilderForNonConcurrentMap(
            graphLensForPrimaryOptimizationPass);
  }

  private void initializeEnumUnboxingCandidates(GraphLens graphLensForPrimaryOptimizationPass) {
    assert enumUnboxingCandidatesInfo == null;
    enumUnboxingCandidatesInfo =
        new EnumUnboxingCandidateAnalysis(appView, this)
            .findCandidates(graphLensForPrimaryOptimizationPass);
    methodsDependingOnLibraryModelisation =
        LongLivedProgramMethodSetBuilder.createConcurrentForIdentitySet(
            graphLensForPrimaryOptimizationPass);
  }

  @Override
  public void rewriteWithLens() {
    methodsDependingOnLibraryModelisation =
        methodsDependingOnLibraryModelisation.rewrittenWithLens(appView.graphLens());
  }

  @Override
  public void unboxEnums(
      AppView<AppInfoWithLiveness> appView,
      IRConverter converter,
      Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    timing.begin("Unbox enums");
    assert feedback.noUpdatesLeft();

    assert candidatesToRemoveInWave.isEmpty();
    EnumDataMap enumDataMap = finishAnalysis();
    assert candidatesToRemoveInWave.isEmpty();

    // At this point the enum unboxing candidates are no longer candidates, they will all be
    // unboxed. We extract the now immutable enums to unbox information and clear the candidate
    // info.
    appView.setUnboxedEnums(enumDataMap);

    if (enumUnboxingCandidatesInfo.isEmpty()) {
      assert enumDataMap.isEmpty();
      timing.end();
      return;
    }

    GraphLens previousLens = appView.graphLens();
    ImmutableSet<DexType> enumsToUnbox = enumUnboxingCandidatesInfo.candidates();
    ImmutableMap<DexProgramClass, Set<DexProgramClass>> enumClassesToUnbox =
        enumUnboxingCandidatesInfo.candidateClassesWithSubclasses();
    LongLivedProgramMethodSetBuilder<ProgramMethodSet> dependencies =
        enumUnboxingCandidatesInfo.allMethodDependencies();
    enumUnboxingCandidatesInfo.clear();
    // Update keep info on any of the enum methods of the removed classes.
    updateKeepInfo(enumsToUnbox);

    EnumUnboxingUtilityClasses utilityClasses =
        EnumUnboxingUtilityClasses.builder(appView)
            .synthesizeEnumUnboxingUtilityClasses(enumClassesToUnbox.keySet(), enumDataMap)
            .build(converter, executorService);

    // Fixup the application.
    ProgramMethodMap<Set<DexProgramClass>> checkNotNullMethods =
        checkNotNullMethodsBuilder
            .rewrittenWithLens(appView, (enumClasses, appliedGraphLens) -> enumClasses)
            .build(appView, builder -> builder.build(appView));
    checkNotNullMethods.removeIf(
        (checkNotNullMethod, ignore) ->
            !checkNotNullMethod
                .getOptimizationInfo()
                .getEnumUnboxerMethodClassification()
                .isCheckNotNullClassification());

    EnumUnboxingTreeFixer.Result treeFixerResult =
        new EnumUnboxingTreeFixer(
                appView, checkNotNullMethods, enumDataMap, enumClassesToUnbox, utilityClasses)
            .fixupTypeReferences(converter, executorService, timing);
    EnumUnboxingLens enumUnboxingLens = treeFixerResult.getLens();

    // Enqueue the (lens rewritten) methods that require reprocessing.
    //
    // Note that the reprocessing set must be rewritten to the new enum unboxing lens before pruning
    // the builders with the methods removed by the tree fixer (since these methods references are
    // already fully lens rewritten).
    postMethodProcessorBuilder
        .rewrittenWithLens(appView)
        .removeAll(treeFixerResult.getPrunedItems().getRemovedMethods())
        .merge(
            dependencies
                .rewrittenWithLens(appView)
                .removeAll(treeFixerResult.getPrunedItems().getRemovedMethods()))
        .merge(
            methodsDependingOnLibraryModelisation
                .rewrittenWithLens(appView)
                .removeAll(treeFixerResult.getPrunedItems().getRemovedMethods()))
        .addAll(treeFixerResult.getMethodsToProcess(), appView.graphLens());
    methodsDependingOnLibraryModelisation.clear();

    updateOptimizationInfos(executorService, feedback, treeFixerResult, previousLens);

    enumUnboxerRewriter =
        new EnumUnboxingRewriter(
            appView,
            treeFixerResult.getCheckNotNullToCheckNotZeroMapping(),
            enumUnboxingLens,
            enumDataMap,
            utilityClasses);

    // Ensure determinism of method-to-reprocess set.
    appView.testing().checkDeterminism(postMethodProcessorBuilder::dump);

    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private void updateOptimizationInfos(
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      EnumUnboxingTreeFixer.Result treeFixerResult,
      GraphLens previousLens)
      throws ExecutionException {
    NonIdentityGraphLens graphLens = appView.graphLens().asNonIdentityLens();
    assert graphLens.isEnumUnboxerLens();

    GraphLens codeLens = graphLens.getPrevious();
    assert codeLens == previousLens;

    feedback.fixupOptimizationInfos(
        appView,
        executorService,
        new OptimizationInfoFixer() {
          @Override
          public void fixup(DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo) {
            optimizationInfo
                .fixupAbstractValue(appView, graphLens, codeLens)
                .fixupClassTypeReferences(appView, graphLens);
          }

          @Override
          public void fixup(
              DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo) {
            optimizationInfo
                .fixupArgumentInfos(
                    method,
                    new DefaultMethodOptimizationInfoFixer() {
                      @Override
                      public CallSiteOptimizationInfo fixupCallSiteOptimizationInfo(
                          ConcreteCallSiteOptimizationInfo callSiteOptimizationInfo) {
                        RewrittenPrototypeDescription prototypeChanges =
                            graphLens.lookupPrototypeChangesForMethodDefinition(
                                method.getReference(), codeLens);
                        return callSiteOptimizationInfo.fixupAfterParametersChanged(
                            prototypeChanges);
                      }
                    })
                .fixupClassTypeReferences(appView, graphLens)
                .fixupAbstractReturnValue(appView, graphLens, codeLens)
                .fixupInstanceInitializerInfo(
                    appView, graphLens, codeLens, treeFixerResult.getPrunedItems());

            // Clear the enum unboxer method classification for check-not-null methods (these
            // classifications are transferred to the synthesized check-not-zero methods by now).
            if (!treeFixerResult
                .getCheckNotNullToCheckNotZeroMapping()
                .containsValue(method.getReference())) {
              optimizationInfo.unsetEnumUnboxerMethodClassification();
            }
          }
        });
  }

  private void updateKeepInfo(Set<DexType> enumsToUnbox) {
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    keepInfo.mutate(
        mutator ->
            mutator.removeKeepInfoForPrunedItems(
                PrunedItems.builder().setRemovedClasses(enumsToUnbox).build()));
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
    assert enumDataMap.getUnboxedSuperEnums().size()
        == enumUnboxingCandidatesInfo.candidates().size();
    return enumDataMap;
  }

  @SuppressWarnings("ReferenceEquality")
  private EnumDataMap analyzeEnumInstances() {
    ImmutableMap.Builder<DexType, DexType> enumSubtypes = ImmutableMap.builder();
    ImmutableMap.Builder<DexType, EnumData> builder = ImmutableMap.builder();
    enumUnboxingCandidatesInfo.forEachCandidateInfo(
        info -> {
          DexProgramClass enumClass = info.getEnumClass();
          EnumData data = buildData(enumClass, info.getRequiredInstanceFieldData());
          if (data == null) {
            // Reason is already reported at this point.
            enumUnboxingCandidatesInfo.removeCandidate(enumClass);
            return;
          }
          if (!debugLogEnabled || !debugLogs.containsKey(enumClass.getType())) {
            builder.put(enumClass.type, data);
            if (data.valuesTypes != null) {
              for (DexType value : data.valuesTypes.values()) {
                if (value != enumClass.type) {
                  enumSubtypes.put(value, enumClass.type);
                }
              }
            }
          }
        });
    staticFieldValuesMap.clear();
    return new EnumDataMap(builder.build(), enumSubtypes.build());
  }

  private EnumData buildData(DexProgramClass enumClass, Set<DexField> instanceFields) {
    if (!enumClass.hasStaticFields()) {
      return new EnumData(ImmutableMap.of(), null, ImmutableMap.of(), ImmutableSet.of(), -1);
    }

    // This map holds all the accessible fields to their unboxed value, so we can remap the field
    // read to the unboxed value.
    ImmutableMap.Builder<DexField, Integer> unboxedValues = ImmutableMap.builder();
    // This maps the ordinal to their original type so that enum with subtypes can be correctly
    // handled.
    Int2ReferenceMap<DexType> valueTypes = new Int2ReferenceArrayMap<>();
    boolean isEnumWithSubtypes = enumUnboxingCandidatesInfo.hasSubtypes(enumClass.getType());
    // This maps the ordinal to the object state, note that some fields may have been removed,
    // hence the entry is in this map but not the enumToOrdinalMap.
    Int2ReferenceMap<ObjectState> ordinalToObjectState = new Int2ReferenceArrayMap<>();

    if (!staticFieldValuesMap.containsKey(enumClass.getType())) {
      reportFailure(enumClass, new MissingEnumStaticFieldValuesReason());
      return null;
    }

    EnumStaticFieldValues enumStaticFieldValues =
        staticFieldValuesMap
            .get(enumClass.getType())
            .rewrittenWithLens(appView, appView.graphLens(), appView.codeLens());
    Set<DexType> enumSubtypes = enumUnboxingCandidatesInfo.getSubtypes(enumClass.getType());

    // Step 1: We iterate over the field to find direct enum instance information and the values
    // fields.
    ImmutableSet.Builder<DexField> valuesField = ImmutableSet.builder();
    TraversalContinuation<?, EnumValuesObjectState> traversalContinuation =
        enumClass.traverseProgramFields(
            (field, valuesContents) -> {
              if (!field.getAccessFlags().isStatic()) {
                return TraversalContinuation.doContinue(valuesContents);
              }
              // The field might be specialized while the data was recorded without the
              // specialization.
              if (factory.enumMembers.isEnumField(field, enumClass.type, enumSubtypes)) {
                ObjectState enumState =
                    enumStaticFieldValues.getObjectStateForPossiblyPinnedField(
                        field.getReference());
                if (enumState == null) {
                  assert enumStaticFieldValues.getObjectStateForPossiblyPinnedField(
                          field.getReference().withType(enumClass.type, factory))
                      == null;
                  if (field.getOptimizationInfo().isDead()) {
                    // We don't care about unused field data.
                    return TraversalContinuation.doContinue(valuesContents);
                  }
                  // We could not track the content of that field. We bail out.
                  reportFailure(
                      enumClass, new MissingObjectStateForEnumInstanceReason(field.getReference()));
                  return TraversalContinuation.doBreak();
                }
                OptionalInt optionalOrdinal = getOrdinal(enumState);
                if (!optionalOrdinal.isPresent()) {
                  reportFailure(
                      enumClass,
                      new MissingInstanceFieldValueForEnumInstanceReason(
                          factory.enumMembers.ordinalField, field.getReference()));
                  return TraversalContinuation.doBreak();
                }
                int ordinal = optionalOrdinal.getAsInt();
                unboxedValues.put(field.getReference(), ordinalToUnboxedInt(ordinal));
                ordinalToObjectState.put(ordinal, enumState);
                if (isEnumWithSubtypes) {
                  DynamicType dynamicType = field.getOptimizationInfo().getDynamicType();
                  if (dynamicType.isExactClassType()) {
                    valueTypes.put(ordinal, dynamicType.getExactClassType().getClassType());
                  } else {
                    reportFailure(
                        enumClass,
                        new MissingExactDynamicEnumTypeForEnumWithSubtypesReason(
                            field.getReference()));
                    return TraversalContinuation.doBreak();
                  }
                }
              } else if (factory.enumMembers.isValuesFieldCandidate(field, enumClass.type)) {
                ObjectState valuesState =
                    enumStaticFieldValues.getObjectStateForPossiblyPinnedField(
                        field.getReference());
                if (valuesState == null) {
                  if (field.getOptimizationInfo().isDead()) {
                    // We don't care about unused field data.
                    return TraversalContinuation.doContinue(valuesContents);
                  }
                  // We could not track the content of that field. We bail out.
                  // We could not track the content of that field, and the field could be a values
                  // field.
                  // We conservatively bail out.
                  reportFailure(
                      enumClass, new MissingContentsForEnumValuesArrayReason(field.getReference()));
                  return TraversalContinuation.doBreak();
                }
                assert valuesState.isEnumValuesObjectState();
                assert valuesContents == null
                    || valuesContents.equals(valuesState.asEnumValuesObjectState());
                valuesContents = valuesState.asEnumValuesObjectState();
                valuesField.add(field.getReference());
              }
              return TraversalContinuation.doContinue(valuesContents);
            },
            null);
    if (traversalContinuation.shouldBreak()) {
      return null;
    }

    // Step 2: We complete the information based on the values content, since some enum instances
    // may be reachable only though the $VALUES field.
    EnumValuesObjectState valuesContents = traversalContinuation.asContinue().getValue();
    if (valuesContents != null) {
      for (int ordinal = 0; ordinal < valuesContents.getEnumValuesSize(); ordinal++) {
        if (!ordinalToObjectState.containsKey(ordinal)) {
          ObjectState enumState = valuesContents.getObjectStateForOrdinal(ordinal);
          if (enumState.isEmpty()) {
            // If $VALUES is used, we need data for all enums, at least the ordinal.
            reportFailure(
                enumClass,
                new MissingInstanceFieldValueForEnumInstanceReason(
                    factory.enumMembers.ordinalField, ordinal));
            return null;
          }
          assert getOrdinal(enumState).isPresent();
          assert getOrdinal(enumState).getAsInt() == ordinal;
          ordinalToObjectState.put(ordinal, enumState);
          if (isEnumWithSubtypes) {
            DexType type = valuesContents.getObjectClassForOrdinal(ordinal);
            if (type == null) {
              reportFailure(
                  enumClass, new MissingExactDynamicEnumTypeForEnumWithSubtypesReason(ordinal));
              return null;
            }
            valueTypes.put(ordinal, type);
          }
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
        isEnumWithSubtypes ? valueTypes : Int2ReferenceMaps.emptyMap(),
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
    if (encodedInstanceField == null) {
      // This seems to be happening in b/238911016 but we do not have a reproduction.
      // If this assert fails, it would be nice to understand what is going on and potentially
      // fix the code below to do something more appropriate than bailing out.
      assert false;
      reportFailure(enumClass, new MissingInstanceFieldValueForEnumInstanceReason(instanceField));
      return EnumInstanceFieldUnknownData.getInstance();
    }
    boolean canBeOrdinal = instanceField.type.isIntType();
    ImmutableInt2ReferenceSortedMap.Builder<AbstractValue> data =
        ImmutableInt2ReferenceSortedMap.builder();
    for (int ordinal : ordinalToObjectState.keySet()) {
      ObjectState state = ordinalToObjectState.get(ordinal);
      AbstractValue fieldValue = state.getAbstractFieldValue(encodedInstanceField);
      if (!fieldValue.isSingleValue()) {
        reportFailure(
            enumClass, new MissingInstanceFieldValueForEnumInstanceReason(instanceField, ordinal));
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
    AbstractValue field = state.getAbstractFieldValue(getOrdinalField().getDefinition());
    if (field.isSingleNumberValue()) {
      return OptionalInt.of(field.asSingleNumberValue().getIntValue());
    }
    return OptionalInt.empty();
  }

  @Override
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

  private void analyzeInitializers() {
    enumUnboxingCandidatesInfo.forEachCandidateInfo(
        (info) -> {
          DexProgramClass enumClass = info.getEnumClass();
          if (!instanceInitializersAllowUnboxing(enumClass)) {
            if (markEnumAsUnboxable(Reason.INVALID_INIT, enumClass)) {
              return;
            }
          }
          if (enumClass.classInitializationMayHaveSideEffects(appView)) {
            if (markEnumAsUnboxable(Reason.INVALID_CLINIT, enumClass)) {
              return;
            }
          }
          for (DexProgramClass subclass : info.getSubclasses()) {
            if (!instanceInitializersAllowUnboxing(subclass)) {
              if (markEnumAsUnboxable(Reason.INVALID_SUBTYPE_INIT, enumClass)) {
                return;
              }
            }
            if (subclass.hasClassInitializer()) {
              if (markEnumAsUnboxable(Reason.SUBTYPE_CLINIT, enumClass)) {
                return;
              }
            }
          }
        });
  }

  private boolean instanceInitializersAllowUnboxing(DexProgramClass clazz) {
    return !Iterables.any(
        clazz.programInstanceInitializers(),
        instanceInitializer ->
            instanceInitializer
                .getOptimizationInfo()
                .getContextInsensitiveInstanceInitializerInfo()
                .mayHaveOtherSideEffectsThanInstanceFieldAssignments());
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
        return analyzeInvokeUser(instruction.asInvokeMethod(), code, context, enumClass, enumValue);
      case INVOKE_STATIC:
      case INVOKE_SUPER:
      case INVOKE_VIRTUAL:
        return analyzeInvokeUser(instruction.asInvokeMethod(), code, context, enumClass, enumValue);
      case NEW_ARRAY_FILLED:
        return analyzeNewArrayFilledUser(
            instruction.asNewArrayFilled(), code, context, enumClass, enumValue);
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

  private boolean isAssignableToArray(Value value, ClassTypeElement arrayBaseType) {
    TypeElement valueType = value.getType();
    if (valueType.isNullType()) {
      return true;
    }
    TypeElement valueBaseType =
        valueType.isArrayType() ? valueType.asArrayType().getBaseType() : valueType;
    assert valueBaseType.isClassType();
    return enumUnboxingCandidatesInfo.isAssignableTo(
        valueBaseType.asClassType().getClassType(), arrayBaseType.getClassType());
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
    if (isAssignableToArray(arrayPut.value(), arrayBaseType)) {
      return Reason.ELIGIBLE;
    }
    return Reason.INVALID_ARRAY_PUT;
  }

  @SuppressWarnings("ReferenceEquality")
  private Reason analyzeNewArrayFilledUser(
      NewArrayFilled newArrayFilled,
      IRCode code,
      ProgramMethod context,
      DexProgramClass enumClass,
      Value enumValue) {
    // MyEnum[] array = new MyEnum[] { MyEnum.A }; is valid.
    // We need to prove that the value to put in and the array have correct types.
    TypeElement arrayType = newArrayFilled.getOutType();
    assert arrayType.isArrayType();

    ClassTypeElement arrayBaseType = arrayType.asArrayType().getBaseType().asClassType();
    if (arrayBaseType == null) {
      assert false;
      return Reason.INVALID_INVOKE_NEW_ARRAY;
    }
    if (arrayBaseType.getClassType() != enumClass.type) {
      return Reason.INVALID_INVOKE_NEW_ARRAY;
    }

    for (Value value : newArrayFilled.inValues()) {
      if (!isAssignableToArray(value, arrayBaseType)) {
        return Reason.INVALID_INVOKE_NEW_ARRAY;
      }
    }
    return Reason.ELIGIBLE;
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
    DexProgramClass holderClass =
        appView.programDefinitionFor(field.getHolderType(), code.context());
    if (holderClass == null) {
      return Reason.INVALID_FIELD_PUT;
    }
    if (fieldPut.isInstancePut() && fieldPut.asInstancePut().object() == enumValue) {
      // TODO(b/249752942): The requirement to be inside an initializer of the enum can be relaxed
      //  if we support puts.
      return context.getHolder() == enumClass && context.getDefinition().isInstanceInitializer()
          ? Reason.ELIGIBLE
          : Reason.ASSIGNMENT_OUTSIDE_INIT;
    }
    // The put value has to be of the field type.
    if (!enumUnboxingCandidatesInfo.isAssignableTo(
        field.getReference().type.toBaseType(factory), enumClass.type)) {
      return Reason.TYPE_MISMATCH_FIELD_PUT;
    }
    return Reason.ELIGIBLE;
  }

  @SuppressWarnings("ReferenceEquality")
  // An If using enum as inValue is valid if it matches e == null
  // or e == X with X of same enum type as e. Ex: if (e == MyEnum.A).
  private Reason analyzeIfUser(
      If theIf, IRCode code, ProgramMethod context, DexProgramClass enumClass, Value enumValue) {
    assert (theIf.getType() == IfType.EQ || theIf.getType() == IfType.NE)
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

  @SuppressWarnings("ReferenceEquality")
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

  @SuppressWarnings("ReferenceEquality")
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

    DexClassAndMethod resolvedMethod =
        appView
            .appInfo()
            .resolveMethod(invoke.getInvokedMethod(), invoke.getInterfaceBit())
            .getResolutionPair();
    if (resolvedMethod == null) {
      return Reason.INVALID_INVOKE;
    }
    // The single target may be null if for example this is a virtual invoke into an abstract
    // method.
    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, code.context());
    DexClassAndMethod mostAccurateTarget = singleTarget == null ? resolvedMethod : singleTarget;

    if (mostAccurateTarget.isProgramMethod()) {
      if (mostAccurateTarget.getHolder().isEnum()
          && resolvedMethod.getDefinition().isInstanceInitializer()) {
        // The enum instance initializer is only allowed to be called from an initializer of the
        // enum itself.
        if (getEnumUnboxingCandidateOrNull(code.context().getHolderType())
                != getEnumUnboxingCandidateOrNull(mostAccurateTarget.getHolderType())
            || !context.getDefinition().isInitializer()) {
          return Reason.INVALID_INIT;
        }
        if (context.getDefinition().isInstanceInitializer()
            && !invoke.getFirstArgument().isThis()) {
          return Reason.INVALID_INIT;
        }
      }

      // Check if this is a checkNotNull() user. In this case, we can create a copy of the method
      // that takes an int instead of java.lang.Object and call that method instead.
      if (singleTarget != null) {
        EnumUnboxerMethodClassification classification =
            singleTarget.getOptimizationInfo().getEnumUnboxerMethodClassification();
        if (classification.isCheckNotNullClassification()) {
          assert singleTarget.getDefinition().isStatic();
          CheckNotNullEnumUnboxerMethodClassification checkNotNullClassification =
              classification.asCheckNotNullClassification();
          if (checkNotNullClassification.isUseEligibleForUnboxing(
              invoke.asInvokeStatic(), enumValue)) {
            GraphLens graphLens = appView.graphLens();
            checkNotNullMethodsBuilder
                .computeIfAbsent(
                    singleTarget.asProgramMethod(),
                    ignoreKey(
                        () ->
                            LongLivedClassSetBuilder.createConcurrentBuilderForIdentitySet(
                                graphLens)),
                    graphLens)
                .add(enumClass, graphLens);
            return Reason.ELIGIBLE;
          }
        }
      }

      // Check that the enum-value only flows into parameters whose type exactly matches the
      // enum's type.
      for (int i = 0; i < mostAccurateTarget.getParameters().size(); i++) {
        if (invoke.getArgumentForParameter(i) == enumValue
            && !enumUnboxingCandidatesInfo.isAssignableTo(
                mostAccurateTarget.getParameter(i).toBaseType(factory), enumClass.getType())) {
          return new IllegalInvokeWithImpreciseParameterTypeReason(
              mostAccurateTarget.getReference());
        }
      }
      if (invoke.isInvokeMethodWithReceiver()) {
        Value receiver = invoke.asInvokeMethodWithReceiver().getReceiver();
        if (receiver == enumValue && mostAccurateTarget.getHolder().isInterface()) {
          return Reason.DEFAULT_METHOD_INVOKE;

        }
      }
      return Reason.ELIGIBLE;
    }

    if (mostAccurateTarget.getHolder().isClasspathClass()) {
      return Reason.INVALID_INVOKE_CLASSPATH;
    }

    assert mostAccurateTarget.getHolder().isLibraryClass();

    if (singleTarget == null) {
      // We don't attempt library modeling if we don't have a single target.
      return Reason.INVALID_INVOKE;
    }

    Reason reason =
        analyzeLibraryInvoke(
            invoke,
            code,
            context,
            enumClass,
            enumValue,
            singleTarget.getReference(),
            singleTarget.getHolder());

    if (reason == Reason.ELIGIBLE) {
      markMethodDependsOnLibraryModelisation(context);
    }

    return reason;
  }

  private Reason comparableAsUnboxedValues(InvokeMethod invoke) {
    assert invoke.inValues().size() == 2;
    TypeElement type1 = invoke.getFirstArgument().getType();
    TypeElement type2 = invoke.getLastArgument().getType();
    DexProgramClass candidate1 = getEnumUnboxingCandidateOrNull(type1);
    DexProgramClass candidate2 = getEnumUnboxingCandidateOrNull(type2);
    assert candidate1 != null || candidate2 != null;
    if (type1.isNullType() || type2.isNullType()) {
      // Comparing an unboxed enum to null is always allowed.
      return Reason.ELIGIBLE;
    }
    if (candidate1 == candidate2) {
      // Comparing two unboxed enum values is valid only if they come from the same enum.
      return Reason.ELIGIBLE;
    }
    return new UnboxedValueNonComparable(invoke.getInvokedMethod(), type1, type2);
  }

  @SuppressWarnings("ReferenceEquality")
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
        return comparableAsUnboxedValues(invoke);
      } else if (singleTargetReference == factory.enumMembers.equals) {
        return comparableAsUnboxedValues(invoke);
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
        assert invoke.getFirstArgument() == enumValue;
        if (appView.options().canInitNewInstanceUsingSuperclassConstructor()) {
          // Enum constructor call is allowed if called from any enum initializer.
          DexProgramClass representativeContext =
              enumUnboxingCandidatesInfo.getCandidateClassOrNull(context.getHolderType());
          if (context.getDefinition().isInstanceInitializer()
              && representativeContext == enumClass) {
            return Reason.ELIGIBLE;
          }
          // Otherwise must be called from the class initializer of a root enum initializer.
          if (context.isStructurallyEqualTo(enumClass.getProgramClassInitializer())) {
            assert enumUnboxingCandidatesInfo.verifyIsSuperEnumUnboxingCandidate(enumClass);
            assert context.getHolder() == representativeContext;
            return Reason.ELIGIBLE;
          }
        } else {
          // Enum constructor call is allowed only if called from a root enum initializer.
          if (context.getDefinition().isInstanceInitializer() && context.getHolder() == enumClass) {
            assert enumUnboxingCandidatesInfo.verifyIsSuperEnumUnboxingCandidate(enumClass);
            return Reason.ELIGIBLE;
          }
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
      if (singleTargetReference == factory.objectMembers.toString) {
        assert invoke.asInvokeMethodWithReceiver().getReceiver() == enumValue;
        addRequiredNameData(enumClass);
        return Reason.ELIGIBLE;
      }
      if (singleTargetReference == factory.objectMembers.hashCode) {
        return Reason.ELIGIBLE;
      }
      if (singleTargetReference == factory.objectMembers.equals) {
        return comparableAsUnboxedValues(invoke);
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
      if (singleTargetReference == factory.objectsMethods.toStringWithObject) {
        addRequiredNameData(enumClass);
        return Reason.ELIGIBLE;
      }
      if (singleTargetReference == factory.objectsMethods.equals) {
        return comparableAsUnboxedValues(invoke);
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
      if (singleTargetReference == factory.javaLangSystemMembers.arraycopy) {
        // Important for Kotlin 1.5 enums, which use arraycopy to create a copy of $VALUES instead
        // of int[].clone().
        return Reason.ELIGIBLE;
      }
      if (singleTargetReference == factory.javaLangSystemMembers.identityHashCode) {
        // Important for proto enum unboxing.
        return Reason.ELIGIBLE;
      }
      return new UnsupportedLibraryInvokeReason(singleTargetReference);
    }

    // Unsupported holder.
    return new UnsupportedLibraryInvokeReason(singleTargetReference);
  }

  @SuppressWarnings("ReferenceEquality")
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

  @Override
  public void onMethodPruned(ProgramMethod method) {
    onMethodCodePruned(method);
  }

  @Override
  public void onMethodCodePruned(ProgramMethod method) {
    enumUnboxingCandidatesInfo.addPrunedMethod(method);
    methodsDependingOnLibraryModelisation.remove(method.getReference(), appView.graphLens());
  }

  @Override
  public Set<Phi> rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      RewrittenPrototypeDescription prototypeChanges) {
    // This has no effect during primary processing since the enumUnboxerRewriter is set
    // in between primary and post processing.
    if (enumUnboxerRewriter != null) {
      return enumUnboxerRewriter.rewriteCode(code, methodProcessor, prototypeChanges);
    }
    return Sets.newIdentityHashSet();
  }

  @Override
  public void unsetRewriter() {
    enumUnboxerRewriter = null;
  }
}
