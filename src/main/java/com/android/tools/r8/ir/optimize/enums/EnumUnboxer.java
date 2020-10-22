// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

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
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ProgramPackageCollection;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.code.ArrayPut;
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
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Opcodes;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldMappingData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldOrdinalData;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldUnknownData;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

public class EnumUnboxer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  // Map the enum candidates with their dependencies, i.e., the methods to reprocess for the given
  // enum if the optimization eventually decides to unbox it.
  private final EnumUnboxingCandidateInfoCollection enumUnboxingCandidatesInfo;
  private final ProgramPackageCollection enumsToUnboxWithPackageRequirement =
      ProgramPackageCollection.createEmpty();

  private EnumUnboxingRewriter enumUnboxerRewriter;

  private final boolean debugLogEnabled;
  private final Map<DexType, Reason> debugLogs;

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
  }

  private void markEnumAsUnboxable(Reason reason, DexProgramClass enumClass) {
    assert enumClass.isEnum();
    reportFailure(enumClass.type, reason);
    enumUnboxingCandidatesInfo.removeCandidate(enumClass.type);
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

  public void analyzeEnums(IRCode code) {
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
            analyzeConstClass(instruction.asConstClass(), eligibleEnums);
            break;
          case Opcodes.CHECK_CAST:
            analyzeCheckCast(instruction.asCheckCast(), eligibleEnums);
            break;
          case Opcodes.INVOKE_STATIC:
            analyzeInvokeStatic(instruction.asInvokeStatic(), eligibleEnums, code.context());
            break;
          case Opcodes.STATIC_GET:
          case Opcodes.INSTANCE_GET:
          case Opcodes.STATIC_PUT:
          case Opcodes.INSTANCE_PUT:
            analyzeFieldInstruction(instruction.asFieldInstruction(), code);
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
  }

  private void analyzeFieldInstruction(FieldInstruction fieldInstruction, IRCode code) {
    DexField field = fieldInstruction.getField();
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(field.holder);
    if (enumClass != null) {
      FieldResolutionResult resolutionResult =
          appView.appInfo().resolveField(field, code.context());
      if (resolutionResult.isFailedOrUnknownResolution()) {
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

  private void analyzeConstClass(ConstClass constClass, Set<DexType> eligibleEnums) {
    // We are using the ConstClass of an enum, which typically means the enum cannot be unboxed.
    // We however allow unboxing if the ConstClass is used only:
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
    for (Instruction user : constClass.outValue().uniqueUsers()) {
      if (user.isInvokeVirtual()
          && isUnboxableNameMethod(user.asInvokeVirtual().getInvokedMethod())) {
        continue;
      }
      if (!(user.isInvokeStatic()
          && user.asInvokeStatic().getInvokedMethod() == factory.enumMembers.valueOf)) {
        markEnumAsUnboxable(Reason.CONST_CLASS, enumClass);
        return;
      }
    }
    // The name data is required for the correct mapping from the enum name to the ordinal in the
    // valueOf utility method.
    addRequiredNameData(enumType);
    eligibleEnums.add(enumType);
  }

  private void addRequiredNameData(DexType enumType) {
    enumUnboxingCandidatesInfo.addRequiredEnumInstanceFieldData(
        enumType, factory.enumMembers.nameField);
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
        DexType returnType = code.method().method.proto.returnType;
        if (enumUnboxingCandidatesInfo.isCandidate(returnType)) {
          eligibleEnums.add(returnType);
        }
      }
    }
  }

  private Reason validateEnumUsages(IRCode code, Value value, DexProgramClass enumClass) {
    for (Instruction user : value.uniqueUsers()) {
      Reason reason = instructionAllowEnumUnboxing(user, code, enumClass, value);
      if (reason != Reason.ELIGIBLE) {
        markEnumAsUnboxable(reason, enumClass);
        return reason;
      }
    }
    for (Phi phi : value.uniquePhiUsers()) {
      for (Value operand : phi.getOperands()) {
        if (!operand.getType().isNullType()
            && getEnumUnboxingCandidateOrNull(operand.getType()) != enumClass) {
          markEnumAsUnboxable(Reason.INVALID_PHI, enumClass);
          return Reason.INVALID_PHI;
        }
      }
    }
    return Reason.ELIGIBLE;
  }

  public void unboxEnums(
      PostMethodProcessor.Builder postBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback)
      throws ExecutionException {
    EnumInstanceFieldDataMap enumInstanceFieldDataMap = finishAnalysis();
    // At this point the enum unboxing candidates are no longer candidates, they will all be
    // unboxed. We extract the now immutable enums to unbox information and clear the candidate
    // info.
    if (enumUnboxingCandidatesInfo.isEmpty()) {
      return;
    }
    ImmutableSet<DexType> enumsToUnbox = enumUnboxingCandidatesInfo.candidates();
    ImmutableSet<DexProgramClass> enumClassesToUnbox =
        enumUnboxingCandidatesInfo.candidateClasses();
    ProgramMethodSet dependencies = enumUnboxingCandidatesInfo.allMethodDependencies();
    enumUnboxingCandidatesInfo.clear();
    // Update keep info on any of the enum methods of the removed classes.
    updateKeepInfo(enumsToUnbox);
    DirectMappedDexApplication.Builder appBuilder = appView.appInfo().app().asDirect().builder();
    UnboxedEnumMemberRelocator relocator =
        UnboxedEnumMemberRelocator.builder(appView)
            .synthesizeEnumUnboxingUtilityClasses(
                enumClassesToUnbox, enumsToUnboxWithPackageRequirement, appBuilder)
            .build();
    enumUnboxerRewriter =
        new EnumUnboxingRewriter(appView, enumsToUnbox, enumInstanceFieldDataMap, relocator);
    NestedGraphLens enumUnboxingLens =
        new EnumUnboxingTreeFixer(appView, enumsToUnbox, relocator, enumUnboxerRewriter)
            .fixupTypeReferences();
    appView.setUnboxedEnums(enumUnboxerRewriter.getEnumsToUnbox());
    GraphLens previousLens = appView.graphLens();
    appView.rewriteWithLensAndApplication(enumUnboxingLens, appBuilder.build());
    updateOptimizationInfos(executorService, feedback);
    postBuilder.put(dependencies);
    postBuilder.rewrittenWithLens(appView, previousLens);
  }

  private void updateOptimizationInfos(
      ExecutorService executorService, OptimizationFeedbackDelayed feedback)
      throws ExecutionException {
    feedback.fixupOptimizationInfos(
        appView,
        executorService,
        new OptimizationInfoFixer() {
          @Override
          public void fixup(DexEncodedField field) {
            FieldOptimizationInfo optimizationInfo = field.getOptimizationInfo();
            if (optimizationInfo.isMutableFieldOptimizationInfo()) {
              optimizationInfo
                  .asMutableFieldOptimizationInfo()
                  .fixupClassTypeReferences(appView.graphLens()::lookupType, appView)
                  .fixupAbstractValue(appView, appView.graphLens());
            } else {
              assert optimizationInfo.isDefaultFieldOptimizationInfo();
            }
          }

          @Override
          public void fixup(DexEncodedMethod method) {
            MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
            if (optimizationInfo.isUpdatableMethodOptimizationInfo()) {
              optimizationInfo
                  .asUpdatableMethodOptimizationInfo()
                  .fixupClassTypeReferences(appView.graphLens()::lookupType, appView)
                  .fixupAbstractReturnValue(appView, appView.graphLens())
                  .fixupInstanceInitializerInfo(appView, appView.graphLens());
            } else {
              assert optimizationInfo.isDefaultMethodOptimizationInfo();
            }
          }
        });
  }

  private void updateKeepInfo(Set<DexType> enumsToUnbox) {
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    keepInfo.mutate(mutator -> mutator.removeKeepInfoForPrunedItems(enumsToUnbox));
  }

  public EnumInstanceFieldDataMap finishAnalysis() {
    analyzeInitializers();
    analyzeAccessibility();
    EnumInstanceFieldDataMap enumInstanceFieldDataMap = analyzeFields();
    if (debugLogEnabled) {
      reportEnumsAnalysis();
    }
    return enumInstanceFieldDataMap;
  }

  private EnumInstanceFieldDataMap analyzeFields() {
    ImmutableMap.Builder<DexType, ImmutableMap<DexField, EnumInstanceFieldKnownData>> builder =
        ImmutableMap.builder();
    enumUnboxingCandidatesInfo.forEachCandidateAndRequiredInstanceFieldData(
        (enumClass, fields) -> {
          ImmutableMap.Builder<DexField, EnumInstanceFieldKnownData> typeBuilder =
              ImmutableMap.builder();
          for (DexField field : fields) {
            EnumInstanceFieldData enumInstanceFieldData = computeEnumFieldData(field, enumClass);
            if (enumInstanceFieldData.isUnknown()) {
              markEnumAsUnboxable(Reason.MISSING_INSTANCE_FIELD_DATA, enumClass);
              return;
            }
            typeBuilder.put(field, enumInstanceFieldData.asEnumFieldKnownData());
          }
          builder.put(enumClass.type, typeBuilder.build());
        });
    return new EnumInstanceFieldDataMap(builder.build());
  }

  private void analyzeAccessibility() {
    // Unboxing an enum will require to move its methods to a different class, which may impact
    // accessibility. For a quick analysis we simply reuse the inliner analysis.
    enumUnboxingCandidatesInfo.forEachCandidate(
        enumClass -> {
          Constraint classConstraint = analyzeAccessibilityInClass(enumClass);
          if (classConstraint == Constraint.NEVER) {
            markEnumAsUnboxable(Reason.ACCESSIBILITY, enumClass);
          } else if (classConstraint == Constraint.PACKAGE) {
            enumsToUnboxWithPackageRequirement.addProgramClass(enumClass);
          }
        });
  }

  private Constraint analyzeAccessibilityInClass(DexProgramClass enumClass) {
    Constraint classConstraint = Constraint.ALWAYS;
    EnumAccessibilityUseRegistry useRegistry = null;
    for (DexEncodedMethod method : enumClass.methods()) {
      // Enum initializer are analyzed in analyzeInitializers instead.
      if (!method.isInitializer()) {
        if (useRegistry == null) {
          useRegistry = new EnumAccessibilityUseRegistry(factory);
        }
        Constraint methodConstraint = constraintForEnumUnboxing(method, useRegistry);
        classConstraint = classConstraint.meet(methodConstraint);
        if (classConstraint == Constraint.NEVER) {
          return classConstraint;
        }
      }
    }
    return classConstraint;
  }

  public Constraint constraintForEnumUnboxing(
      DexEncodedMethod method, EnumAccessibilityUseRegistry useRegistry) {
    return useRegistry.computeConstraint(method.asProgramMethod(appView));
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
      ResolutionResult resolutionResult = appView.appInfo().resolveMethod(method, isInterface);
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
      DexType resolvedHolder = target.holder();
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
      ResolutionResult resolutionResult =
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
      assert !factory.isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());
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
          boolean hasInstanceInitializer = false;
          for (DexEncodedMethod directMethod : enumClass.directMethods()) {
            if (directMethod.isInstanceInitializer()) {
              hasInstanceInitializer = true;
              if (directMethod
                  .getOptimizationInfo()
                  .getInstanceInitializerInfo()
                  .mayHaveOtherSideEffectsThanInstanceFieldAssignments()) {
                markEnumAsUnboxable(Reason.INVALID_INIT, enumClass);
                break;
              }
            }
          }
          if (!hasInstanceInitializer) {
            // This case typically happens when a programmer uses EnumSet/EnumMap without using the
            // enum keep rules. The code is incorrect in this case (EnumSet/EnumMap won't work).
            // We bail out.
            markEnumAsUnboxable(Reason.NO_INIT, enumClass);
            return;
          }

          if (enumClass.classInitializationMayHaveSideEffects(appView)) {
            markEnumAsUnboxable(Reason.INVALID_CLINIT, enumClass);
          }
        });
  }

  private Reason instructionAllowEnumUnboxing(
      Instruction instruction, IRCode code, DexProgramClass enumClass, Value enumValue) {

    // All invokes in the library are invalid, besides a few cherry picked cases such as ordinal().
    if (instruction.isInvokeMethod()) {
      InvokeMethod invokeMethod = instruction.asInvokeMethod();
      if (invokeMethod.getInvokedMethod().holder.isArrayType()) {
        // The only valid methods is clone for values() to be correct.
        if (invokeMethod.getInvokedMethod().name == factory.cloneMethodName) {
          return Reason.ELIGIBLE;
        }
        return Reason.INVALID_INVOKE_ON_ARRAY;
      }
      DexClassAndMethod singleTarget = invokeMethod.lookupSingleTarget(appView, code.context());
      if (singleTarget == null) {
        return Reason.INVALID_INVOKE;
      }
      DexClass dexClass = singleTarget.getHolder();
      if (dexClass.isProgramClass()) {
        if (dexClass.isEnum() && singleTarget.getDefinition().isInstanceInitializer()) {
          if (code.method().holder() == dexClass.type && code.method().isClassInitializer()) {
            // The enum instance initializer is allowed to be called only from the enum clinit.
            return Reason.ELIGIBLE;
          } else {
            return Reason.INVALID_INIT;
          }
        }
        // Check that the enum-value only flows into parameters whose type exactly matches the
        // enum's type.
        int offset = BooleanUtils.intValue(!singleTarget.getDefinition().isStatic());
        for (int i = 0; i < singleTarget.getReference().getParameters().size(); i++) {
          if (invokeMethod.getArgument(offset + i) == enumValue) {
            if (singleTarget.getReference().getParameter(i).toBaseType(factory) != enumClass.type) {
              return Reason.GENERIC_INVOKE;
            }
          }
        }
        if (invokeMethod.isInvokeMethodWithReceiver()) {
          Value receiver = invokeMethod.asInvokeMethodWithReceiver().getReceiver();
          if (receiver == enumValue && dexClass.isInterface()) {
            return Reason.DEFAULT_METHOD_INVOKE;
          }
        }
        return Reason.ELIGIBLE;
      }
      if (dexClass.isClasspathClass()) {
        return Reason.INVALID_INVOKE;
      }
      assert dexClass.isLibraryClass();
      DexMethod singleTargetReference = singleTarget.getReference();
      if (dexClass.type != factory.enumType) {
        // System.identityHashCode(Object) is supported for proto enums.
        // Object#getClass without outValue and Objects.requireNonNull are supported since R8
        // rewrites explicit null checks to such instructions.
        if (singleTargetReference == factory.javaLangSystemMethods.identityHashCode) {
          return Reason.ELIGIBLE;
        }
        if (singleTargetReference == factory.stringMembers.valueOf) {
          addRequiredNameData(enumClass.type);
          return Reason.ELIGIBLE;
        }
        if (singleTargetReference == factory.objectMembers.getClass
            && (!invokeMethod.hasOutValue() || !invokeMethod.outValue().hasAnyUsers())) {
          // This is a hidden null check.
          return Reason.ELIGIBLE;
        }
        if (singleTargetReference == factory.objectsMethods.requireNonNull
            || singleTargetReference == factory.objectsMethods.requireNonNullWithMessage) {
          return Reason.ELIGIBLE;
        }
        return Reason.UNSUPPORTED_LIBRARY_CALL;
      }
      // TODO(b/147860220): EnumSet and EnumMap may be interesting to model.
      if (singleTargetReference == factory.enumMembers.compareTo) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.equals) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.nameMethod
          || singleTargetReference == factory.enumMembers.toString) {
        assert invokeMethod.asInvokeMethodWithReceiver().getReceiver() == enumValue;
        addRequiredNameData(enumClass.type);
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.ordinalMethod) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.hashCode) {
        return Reason.ELIGIBLE;
      } else if (singleTargetReference == factory.enumMembers.constructor) {
        // Enum constructor call is allowed only if called from an enum initializer.
        if (code.method().isInstanceInitializer() && code.method().holder() == enumClass.type) {
          return Reason.ELIGIBLE;
        }
      }
      return Reason.UNSUPPORTED_LIBRARY_CALL;
    }

    // A field put is valid only if the field is not on an enum, and the field type and the valuePut
    // have identical enum type.
    if (instruction.isFieldPut()) {
      FieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexEncodedField field =
          appView.appInfo().resolveField(fieldInstruction.getField()).getResolvedField();
      if (field == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      DexProgramClass dexClass = appView.programDefinitionFor(field.holder(), code.context());
      if (dexClass == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      if (fieldInstruction.isInstancePut()
          && fieldInstruction.asInstancePut().object() == enumValue) {
        return Reason.ELIGIBLE;
      }
      // The put value has to be of the field type.
      if (field.field.type.toBaseType(factory) != enumClass.type) {
        return Reason.TYPE_MISMATCH_FIELD_PUT;
      }
      return Reason.ELIGIBLE;
    }

    if (instruction.isInstanceGet()) {
      InstanceGet instanceGet = instruction.asInstanceGet();
      assert instanceGet.getField().holder == enumClass.type;
      DexField field = instanceGet.getField();
      enumUnboxingCandidatesInfo.addRequiredEnumInstanceFieldData(enumClass.type, field);
      return Reason.ELIGIBLE;
    }

    // An If using enum as inValue is valid if it matches e == null
    // or e == X with X of same enum type as e. Ex: if (e == MyEnum.A).
    if (instruction.isIf()) {
      If anIf = instruction.asIf();
      assert (anIf.getType() == If.Type.EQ || anIf.getType() == If.Type.NE)
          : "Comparing a reference with " + anIf.getType().toString();
      // e == null.
      if (anIf.isZeroTest()) {
        return Reason.ELIGIBLE;
      }
      // e == MyEnum.X
      TypeElement leftType = anIf.lhs().getType();
      TypeElement rightType = anIf.rhs().getType();
      if (leftType.equalUpToNullability(rightType)) {
        assert leftType.isClassType();
        assert leftType.asClassType().getClassType() == enumClass.type;
        return Reason.ELIGIBLE;
      }
      return Reason.INVALID_IF_TYPES;
    }

    if (instruction.isCheckCast()) {
      if (allowCheckCast(instruction.asCheckCast())) {
        return Reason.ELIGIBLE;
      }
      return Reason.DOWN_CAST;
    }

    if (instruction.isArrayLength()) {
      // MyEnum[] array = ...; array.length; is valid.
      return Reason.ELIGIBLE;
    }

    if (instruction.isArrayGet()) {
      // MyEnum[] array = ...; array[0]; is valid.
      return Reason.ELIGIBLE;
    }

    if (instruction.isArrayPut()) {
      // MyEnum[] array; array[0] = MyEnum.A; is valid.
      // MyEnum[][] array2d; MyEnum[] array; array2d[0] = array; is valid.
      // MyEnum[]^N array; MyEnum[]^(N-1) element; array[0] = element; is valid.
      // We need to prove that the value to put in and the array have correct types.
      ArrayPut arrayPut = instruction.asArrayPut();
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

    if (instruction.isAssume()) {
      Value outValue = instruction.outValue();
      return validateEnumUsages(code, outValue, enumClass);
    }

    // Return is used for valueOf methods.
    if (instruction.isReturn()) {
      DexType returnType = code.method().method.proto.returnType;
      if (returnType != enumClass.type && returnType.toBaseType(factory) != enumClass.type) {
        return Reason.IMPLICIT_UP_CAST_IN_RETURN;
      }
      return Reason.ELIGIBLE;
    }

    return Reason.OTHER_UNSUPPORTED_INSTRUCTION;
  }

  private EnumInstanceFieldData computeEnumFieldData(
      DexField instanceField, DexProgramClass enumClass) {
    DexEncodedField encodedInstanceField =
        appView.appInfo().resolveFieldOn(enumClass, instanceField).getResolvedField();
    assert encodedInstanceField != null;
    boolean canBeOrdinal = instanceField.type.isIntType();
    Map<DexField, AbstractValue> data = new IdentityHashMap<>();
    EnumValueInfoMapCollection.EnumValueInfoMap enumValueInfoMap =
        appView.appInfo().getEnumValueInfoMap(enumClass.type);
    for (DexField staticField : enumValueInfoMap.enumValues()) {
      ObjectState enumInstanceState =
          computeEnumInstanceObjectState(enumClass, staticField, enumValueInfoMap);
      if (enumInstanceState == null) {
        // The enum instance is effectively unused. No need to generate anything for it, the path
        // will never be taken.
      } else {
        AbstractValue fieldValue = enumInstanceState.getAbstractFieldValue(encodedInstanceField);
        if (!(fieldValue.isSingleNumberValue() || fieldValue.isSingleStringValue())) {
          return EnumInstanceFieldUnknownData.getInstance();
        }
        data.put(staticField, fieldValue);
        if (canBeOrdinal) {
          int ordinalValue = enumValueInfoMap.getEnumValueInfo(staticField).ordinal;
          assert fieldValue.isSingleNumberValue();
          int computedValue = fieldValue.asSingleNumberValue().getIntValue();
          if (computedValue != ordinalValue) {
            canBeOrdinal = false;
          }
        }
      }
    }
    if (canBeOrdinal) {
      return new EnumInstanceFieldOrdinalData();
    }
    return new EnumInstanceFieldMappingData(data);
  }

  // We need to access the enum instance object state to figure out if it contains known constant
  // field values. The enum instance may be accessed in two ways, directly through the enum
  // static field, or through the enum $VALUES field. If none of them are kept, the instance is
  // effectively unused. The object state may be stored in the enum static field optimization
  // info, if kept, or in the $VALUES optimization info, if kept.
  // If the enum instance is unused, this method answers null.
  private ObjectState computeEnumInstanceObjectState(
      DexProgramClass enumClass,
      DexField staticField,
      EnumValueInfoMapCollection.EnumValueInfoMap enumValueInfoMap) {
    // Attempt 1: Get object state from the instance field's optimization info.
    DexEncodedField encodedStaticField = enumClass.lookupStaticField(staticField);
    AbstractValue enumInstanceValue = encodedStaticField.getOptimizationInfo().getAbstractValue();
    if (enumInstanceValue.isSingleFieldValue()) {
      return enumInstanceValue.asSingleFieldValue().getState();
    }
    if (enumInstanceValue.isUnknown()) {
      return ObjectState.empty();
    }
    assert enumInstanceValue.isZero();

    // Attempt 2: Get object state from the values field's optimization info.
    DexEncodedField valuesField =
        enumClass.lookupStaticField(
            factory.createField(
                enumClass.type,
                factory.createArrayType(1, enumClass.type),
                factory.enumValuesFieldName));
    AbstractValue valuesValue = valuesField.getOptimizationInfo().getAbstractValue();
    if (valuesValue.isZero()) {
      // Unused enum instance.
      return null;
    }
    if (valuesValue.isUnknown()) {
      return ObjectState.empty();
    }
    assert valuesValue.isSingleFieldValue();
    ObjectState valuesState = valuesValue.asSingleFieldValue().getState();
    if (valuesState.isEnumValuesObjectState()) {
      return valuesState
          .asEnumValuesObjectState()
          .getObjectStateForOrdinal(enumValueInfoMap.getEnumValueInfo(staticField).ordinal);
    }
    return ObjectState.empty();
  }

  private void reportEnumsAnalysis() {
    assert debugLogEnabled;
    Reporter reporter = appView.options().reporter;
    Set<DexType> candidates = enumUnboxingCandidatesInfo.candidates();
    reporter.info(
        new StringDiagnostic(
            "Unboxed enums (Unboxing succeeded "
                + candidates.size()
                + "): "
                + Arrays.toString(candidates.toArray())));
    StringBuilder sb = new StringBuilder();
    sb.append("Boxed enums (Unboxing failed ").append(debugLogs.size()).append("):\n");
    for (DexType enumType : debugLogs.keySet()) {
      sb.append("- ")
          .append(enumType)
          .append(": ")
          .append(debugLogs.get(enumType).toString())
          .append('\n');
    }
    reporter.info(new StringDiagnostic(sb.toString()));
  }

  void reportFailure(DexType enumType, Reason reason) {
    if (debugLogEnabled) {
      debugLogs.put(enumType, reason);
    }
  }

  public Set<Phi> rewriteCode(IRCode code) {
    // This has no effect during primary processing since the enumUnboxerRewriter is set
    // in between primary and post processing.
    if (enumUnboxerRewriter != null) {
      return enumUnboxerRewriter.rewriteCode(code);
    }
    return Sets.newIdentityHashSet();
  }

  public void synthesizeUtilityMethods(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    if (enumUnboxerRewriter != null) {
      enumUnboxerRewriter.synthesizeEnumUnboxingUtilityMethods(converter, executorService);
    }
  }

  public enum Reason {
    ELIGIBLE,
    ACCESSIBILITY,
    ANNOTATION,
    PINNED,
    DOWN_CAST,
    SUBTYPES,
    INTERFACE,
    MANY_INSTANCE_FIELDS,
    GENERIC_INVOKE,
    DEFAULT_METHOD_INVOKE,
    UNEXPECTED_STATIC_FIELD,
    UNRESOLVABLE_FIELD,
    CONST_CLASS,
    INVALID_PHI,
    NO_INIT,
    INVALID_INIT,
    INVALID_CLINIT,
    INVALID_INVOKE,
    INVALID_INVOKE_ON_ARRAY,
    IMPLICIT_UP_CAST_IN_RETURN,
    VALUE_OF_INVOKE,
    VALUES_INVOKE,
    COMPARE_TO_INVOKE,
    UNSUPPORTED_LIBRARY_CALL,
    MISSING_INFO_MAP,
    MISSING_INSTANCE_FIELD_DATA,
    INVALID_FIELD_READ,
    INVALID_FIELD_PUT,
    INVALID_ARRAY_PUT,
    FIELD_PUT_ON_ENUM,
    TYPE_MISMATCH_FIELD_PUT,
    INVALID_IF_TYPES,
    DYNAMIC_TYPE,
    ENUM_METHOD_CALLED_WITH_NULL_RECEIVER,
    OTHER_UNSUPPORTED_INSTRUCTION;
  }
}
