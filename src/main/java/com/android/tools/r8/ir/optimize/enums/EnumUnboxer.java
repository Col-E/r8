// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClass.FieldSetter;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Opcodes;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.conversion.PostOptimization;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnumUnboxer implements PostOptimization {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  // Map the enum candidates with their dependencies, i.e., the methods to reprocess for the given
  // enum if the optimization eventually decides to unbox it.
  private final Map<DexType, ProgramMethodSet> enumsUnboxingCandidates;

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
    enumsUnboxingCandidates = new EnumUnboxingCandidateAnalysis(appView, this).findCandidates();
  }

  private void markEnumAsUnboxable(Reason reason, DexProgramClass enumClass) {
    assert enumClass.isEnum();
    reportFailure(enumClass.type, reason);
    enumsUnboxingCandidates.remove(enumClass.type);
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
    if (!enumsUnboxingCandidates.containsKey(type)) {
      return null;
    }
    return appView.definitionForProgramType(type);
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
            analyzeInvokeStatic(instruction.asInvokeStatic(), eligibleEnums);
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
        ProgramMethodSet dependencies = enumsUnboxingCandidates.get(eligibleEnum);
        // If dependencies is null, it means the enum is not eligible (It has been marked as
        // unboxable by this thread or another one), so we do not need to record dependencies.
        if (dependencies != null) {
          dependencies.add(code.context());
        }
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

  private void analyzeInvokeStatic(InvokeStatic invokeStatic, Set<DexType> eligibleEnums) {
    DexMethod invokedMethod = invokeStatic.getInvokedMethod();
    DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
    if (enumClass != null) {
      eligibleEnums.add(enumClass.type);
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
    // We however allow unboxing if the ConstClass is only used as an argument to Enum#valueOf, to
    // allow unboxing of: MyEnum a = Enum.valueOf(MyEnum.class, "A");.
    if (!enumsUnboxingCandidates.containsKey(constClass.getValue())) {
      return;
    }
    if (constClass.outValue() == null) {
      eligibleEnums.add(constClass.getValue());
      return;
    }
    if (constClass.outValue().hasPhiUsers()) {
      markEnumAsUnboxable(
          Reason.CONST_CLASS, appView.definitionForProgramType(constClass.getValue()));
      return;
    }
    for (Instruction user : constClass.outValue().uniqueUsers()) {
      if (!(user.isInvokeStatic()
          && user.asInvokeStatic().getInvokedMethod() == factory.enumMethods.valueOf)) {
        markEnumAsUnboxable(
            Reason.CONST_CLASS, appView.definitionForProgramType(constClass.getValue()));
        return;
      }
    }
    eligibleEnums.add(constClass.getValue());
  }

  private void addNullDependencies(IRCode code, Set<Instruction> uses, Set<DexType> eligibleEnums) {
    for (Instruction use : uses) {
      if (use.isInvokeMethod()) {
        InvokeMethod invokeMethod = use.asInvokeMethod();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        for (DexType paramType : invokedMethod.proto.parameters.values) {
          if (enumsUnboxingCandidates.containsKey(paramType)) {
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
        if (enumsUnboxingCandidates.containsKey(type)) {
          eligibleEnums.add(type);
        }
      } else if (use.isReturn()) {
        DexType returnType = code.method().method.proto.returnType;
        if (enumsUnboxingCandidates.containsKey(returnType)) {
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
        if (getEnumUnboxingCandidateOrNull(operand.getType()) != enumClass) {
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
    // At this point the enumsToUnbox are no longer candidates, they will all be unboxed.
    if (enumsUnboxingCandidates.isEmpty()) {
      return;
    }
    ImmutableSet<DexType> enumsToUnbox = ImmutableSet.copyOf(this.enumsUnboxingCandidates.keySet());
    // Update keep info on any of the enum methods of the removed classes.
    updatePinnedItems(enumsToUnbox);
    enumUnboxerRewriter = new EnumUnboxingRewriter(appView, enumsToUnbox);
    NestedGraphLense enumUnboxingLens = new TreeFixer(enumsToUnbox).fixupTypeReferences();
    appView.setUnboxedEnums(enumUnboxerRewriter.getEnumsToUnbox());
    GraphLense previousLens = appView.graphLense();
    appView.setGraphLense(enumUnboxingLens);
    appView.setAppInfo(
        appView.appInfo().rewrittenWithLens(appView.appInfo().app().asDirect(), enumUnboxingLens));
    // Update optimization info.
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
                  .fixupClassTypeReferences(appView.graphLense()::lookupType, appView)
                  .fixupAbstractValue(appView, appView.graphLense());
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
                  .fixupClassTypeReferences(appView.graphLense()::lookupType, appView)
                  .fixupAbstractReturnValue(appView, appView.graphLense())
                  .fixupInstanceInitializerInfo(appView, appView.graphLense());
            } else {
              assert optimizationInfo.isDefaultMethodOptimizationInfo();
            }
          }
        });
    postBuilder.put(this);
    postBuilder.rewrittenWithLens(appView, previousLens);
  }

  private void updatePinnedItems(Set<DexType> enumsToUnbox) {
    appView
        .appInfo()
        .getKeepInfo()
        .mutate(
            keepInfo -> {
              for (DexType type : enumsToUnbox) {
                DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
                assert !keepInfo.getClassInfo(clazz).isPinned();
                clazz.forEachProgramMethod(keepInfo::unsafeUnpinMethod);
                clazz.forEachField(field -> keepInfo.unsafeUnpinField(clazz, field));
              }
            });
  }

  public void finishAnalysis() {
    for (DexType toUnbox : enumsUnboxingCandidates.keySet()) {
      DexProgramClass enumClass = appView.definitionForProgramType(toUnbox);
      assert enumClass != null;

      // Enum candidates have necessarily only one constructor matching enumMethods.constructor
      // signature.
      DexEncodedMethod initializer = enumClass.lookupDirectMethod(factory.enumMethods.constructor);
      if (initializer == null) {
        // This case typically happens when a programmer uses EnumSet/EnumMap without using the
        // enum keep rules. The code is incorrect in this case (EnumSet/EnumMap won't work).
        // We bail out.
        markEnumAsUnboxable(Reason.NO_INIT, enumClass);
        continue;
      }
      if (initializer.getOptimizationInfo().mayHaveSideEffects()) {
        markEnumAsUnboxable(Reason.INVALID_INIT, enumClass);
        continue;
      }

      if (enumClass.classInitializationMayHaveSideEffects(appView)) {
        markEnumAsUnboxable(Reason.INVALID_CLINIT, enumClass);
      }
    }
    if (debugLogEnabled) {
      reportEnumsAnalysis();
    }
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
      DexEncodedMethod encodedSingleTarget =
          invokeMethod.lookupSingleTarget(appView, code.context());
      if (encodedSingleTarget == null) {
        return Reason.INVALID_INVOKE;
      }
      DexMethod singleTarget = encodedSingleTarget.method;
      DexClass dexClass = appView.definitionFor(singleTarget.holder);
      if (dexClass == null) {
        assert false;
        return Reason.INVALID_INVOKE;
      }
      if (dexClass.isProgramClass()) {
        if (dexClass.isEnum() && encodedSingleTarget.isInstanceInitializer()) {
          if (code.method().holder() == dexClass.type && code.method().isClassInitializer()) {
            // The enum instance initializer is allowed to be called only from the enum clinit.
            return Reason.ELIGIBLE;
          } else {
            return Reason.INVALID_INIT;
          }
        }
        // Check that the enum-value only flows into parameters whose type exactly matches the
        // enum's type.
        int offset = BooleanUtils.intValue(!encodedSingleTarget.isStatic());
        for (int i = 0; i < singleTarget.proto.parameters.size(); i++) {
          if (invokeMethod.getArgument(offset + i) == enumValue) {
            if (singleTarget.proto.parameters.values[i].toBaseType(factory) != enumClass.type) {
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
      if (dexClass.type != factory.enumType) {
        return Reason.UNSUPPORTED_LIBRARY_CALL;
      }
      // TODO(b/147860220): EnumSet and EnumMap may be interesting to model.
      if (singleTarget == factory.enumMethods.compareTo) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.equals) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.name) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.toString) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.ordinal) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.hashCode) {
        return Reason.ELIGIBLE;
      } else if (singleTarget == factory.enumMethods.constructor) {
        // Enum constructor call is allowed only if first call of an enum initializer.
        if (code.method().isInstanceInitializer()
            && code.method().holder() == enumClass.type
            && isFirstInstructionAfterArguments(invokeMethod, code)) {
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
      DexProgramClass dexClass = appView.definitionForProgramType(field.holder());
      if (dexClass == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      // The put value has to be of the field type.
      if (field.field.type.toBaseType(factory) != enumClass.type) {
        return Reason.TYPE_MISMATCH_FIELD_PUT;
      }
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

  private boolean isFirstInstructionAfterArguments(InvokeMethod invokeMethod, IRCode code) {
    BasicBlock basicBlock = code.entryBlock();
    for (Instruction instruction : basicBlock.getInstructions()) {
      if (!instruction.isArgument()) {
        return instruction == invokeMethod;
      }
    }
    return false;
  }

  private void reportEnumsAnalysis() {
    assert debugLogEnabled;
    Reporter reporter = appView.options().reporter;
    reporter.info(
        new StringDiagnostic(
            "Unboxed enums (Unboxing succeeded "
                + enumsUnboxingCandidates.size()
                + "): "
                + Arrays.toString(enumsUnboxingCandidates.keySet().toArray())));
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

  public void synthesizeUtilityMethods(
      Builder<?> builder, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    if (enumUnboxerRewriter != null) {
      enumUnboxerRewriter.synthesizeEnumUnboxingUtilityMethods(builder, converter, executorService);
    }
  }

  @Override
  public ProgramMethodSet methodsToRevisit() {
    ProgramMethodSet toReprocess = ProgramMethodSet.create();
    for (ProgramMethodSet methods : enumsUnboxingCandidates.values()) {
      toReprocess.addAll(methods);
    }
    return toReprocess;
  }

  @Override
  public Collection<CodeOptimization> codeOptimizationsForPostProcessing() {
    // Answers null so default optimization setup is performed.
    return null;
  }

  public enum Reason {
    ELIGIBLE,
    ANNOTATION,
    PINNED,
    DOWN_CAST,
    SUBTYPES,
    INTERFACE,
    INSTANCE_FIELD,
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
    INVALID_FIELD_PUT,
    INVALID_ARRAY_PUT,
    FIELD_PUT_ON_ENUM,
    TYPE_MISMATCH_FIELD_PUT,
    INVALID_IF_TYPES,
    DYNAMIC_TYPE,
    ENUM_METHOD_CALLED_WITH_NULL_RECEIVER,
    OTHER_UNSUPPORTED_INSTRUCTION;
  }

  private class TreeFixer {

    private final List<DexEncodedMethod> unboxedEnumsMethods = new ArrayList<>();
    private final EnumUnboxingLens.Builder lensBuilder = EnumUnboxingLens.builder();
    private final Set<DexType> enumsToUnbox;

    private TreeFixer(Set<DexType> enumsToUnbox) {
      this.enumsToUnbox = enumsToUnbox;
    }

    private NestedGraphLense fixupTypeReferences() {
      assert enumUnboxerRewriter != null;
      // Fix all methods and fields using enums to unbox.
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (enumsToUnbox.contains(clazz.type)) {
          assert clazz.instanceFields().size() == 0;
          // Clear the initializers and move the static methods to the utility class.
          Set<DexEncodedMethod> methodsToRemove = Sets.newIdentityHashSet();
          clazz
              .methods()
              .forEach(
                  m -> {
                    if (m.isInitializer()) {
                      clearEnumToUnboxMethod(m);
                    } else {
                      unboxedEnumsMethods.add(
                          fixupEncodedMethodToUtility(m, factory.enumUnboxingUtilityType));
                      methodsToRemove.add(m);
                    }
                  });
          clazz.getMethodCollection().removeMethods(methodsToRemove);
        } else {
          clazz.getMethodCollection().replaceMethods(this::fixupEncodedMethod);
          fixupFields(clazz.staticFields(), clazz::setStaticField);
          fixupFields(clazz.instanceFields(), clazz::setInstanceField);
        }
      }
      for (DexType toUnbox : enumsToUnbox) {
        lensBuilder.map(toUnbox, factory.intType);
      }
      DexProgramClass utilityClass =
          appView.definitionForProgramType(factory.enumUnboxingUtilityType);
      assert utilityClass != null : "Should have been synthesized upfront";
      utilityClass.addDirectMethods(unboxedEnumsMethods);
      return lensBuilder.build(factory, appView.graphLense(), enumsToUnbox);
    }

    private void clearEnumToUnboxMethod(DexEncodedMethod enumMethod) {
      // The compiler may have references to the enum methods, but such methods will be removed
      // and they cannot be reprocessed since their rewriting through the lensCodeRewriter/
      // enumUnboxerRewriter will generate invalid code.
      // To work around this problem we clear such methods, i.e., we replace the code object by
      // an empty throwing code object, so reprocessing won't take time and will be valid.
      enumMethod.setCode(enumMethod.buildEmptyThrowingCode(appView.options()), appView);
    }

    private DexEncodedMethod fixupEncodedMethodToUtility(
        DexEncodedMethod encodedMethod, DexType newHolder) {
      DexMethod method = encodedMethod.method;
      DexString newMethodName =
          factory.createString(
              enumUnboxerRewriter.compatibleName(method.holder)
                  + "$"
                  + (encodedMethod.isDirectMethod() ? "d" : "v")
                  + "$"
                  + method.name.toString());
      DexProto proto =
          encodedMethod.isStatic() ? method.proto : factory.prependHolderToProto(method);
      DexMethod newMethod = factory.createMethod(newHolder, fixupProto(proto), newMethodName);
      lensBuilder.move(method, encodedMethod.isStatic(), newMethod, true);
      encodedMethod.accessFlags.promoteToPublic();
      encodedMethod.accessFlags.promoteToStatic();
      encodedMethod.clearAnnotations();
      encodedMethod.clearParameterAnnotations();
      return encodedMethod.toTypeSubstitutedMethod(newMethod);
    }

    private DexEncodedMethod fixupEncodedMethod(DexEncodedMethod encodedMethod) {
      DexMethod newMethod = fixupMethod(encodedMethod.method);
      if (newMethod != encodedMethod.method) {
        boolean isStatic = encodedMethod.isStatic();
        lensBuilder.move(encodedMethod.method, isStatic, newMethod, isStatic);
        return encodedMethod.toTypeSubstitutedMethod(newMethod);
      }
      return encodedMethod;
    }

    private void fixupFields(List<DexEncodedField> fields, FieldSetter setter) {
      if (fields == null) {
        return;
      }
      for (int i = 0; i < fields.size(); i++) {
        DexEncodedField encodedField = fields.get(i);
        DexField field = encodedField.field;
        DexType newType = fixupType(field.type);
        if (newType != field.type) {
          DexField newField = factory.createField(field.holder, newType, field.name);
          lensBuilder.move(field, newField);
          DexEncodedField newEncodedField = encodedField.toTypeSubstitutedField(newField);
          setter.setField(i, newEncodedField);
          if (encodedField.isStatic() && encodedField.hasExplicitStaticValue()) {
            assert encodedField.getStaticValue() == DexValueNull.NULL;
            newEncodedField.setStaticValue(DexValueInt.DEFAULT);
            // TODO(b/150593449): Support conversion from DexValueEnum to DexValueInt.
          }
        }
      }
    }

    private DexMethod fixupMethod(DexMethod method) {
      return fixupMethod(method, method.holder, method.name);
    }

    private DexMethod fixupMethod(DexMethod method, DexType newHolder, DexString newMethodName) {
      return factory.createMethod(newHolder, fixupProto(method.proto), newMethodName);
    }

    private DexProto fixupProto(DexProto proto) {
      DexType returnType = fixupType(proto.returnType);
      DexType[] arguments = fixupTypes(proto.parameters.values);
      return factory.createProto(returnType, arguments);
    }

    private DexType fixupType(DexType type) {
      if (type.isArrayType()) {
        DexType base = type.toBaseType(factory);
        DexType fixed = fixupType(base);
        if (base == fixed) {
          return type;
        }
        return type.replaceBaseType(fixed, factory);
      }
      if (type.isClassType() && enumsToUnbox.contains(type)) {
        DexType intType = factory.intType;
        lensBuilder.map(type, intType);
        return intType;
      }
      return type;
    }

    private DexType[] fixupTypes(DexType[] types) {
      DexType[] result = new DexType[types.length];
      for (int i = 0; i < result.length; i++) {
        result[i] = fixupType(types[i]);
      }
      return result;
    }
  }

  private static class EnumUnboxingLens extends NestedGraphLense {

    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;
    private final Set<DexType> unboxedEnums;

    EnumUnboxingLens(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLense previousLense,
        DexItemFactory dexItemFactory,
        Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges,
        Set<DexType> unboxedEnums) {
      super(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLense,
          dexItemFactory);
      this.prototypeChanges = prototypeChanges;
      this.unboxedEnums = unboxedEnums;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChanges(DexMethod method) {
      // During the second IR processing enum unboxing is the only optimization rewriting
      // prototype description, if this does not hold, remove the assertion and merge
      // the two prototype changes.
      assert previousLense.lookupPrototypeChanges(method).isEmpty();
      return prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none());
    }

    @Override
    protected Invoke.Type mapInvocationType(
        DexMethod newMethod, DexMethod originalMethod, Invoke.Type type) {
      if (unboxedEnums.contains(originalMethod.holder)) {
        // Methods moved from unboxed enums to the utility class are either static or statified.
        assert newMethod != originalMethod;
        return Invoke.Type.STATIC;
      }
      return type;
    }

    public static Builder builder() {
      return new Builder();
    }

    private static class Builder extends NestedGraphLense.Builder {

      private Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
          new IdentityHashMap<>();

      public void move(DexMethod from, boolean fromStatic, DexMethod to, boolean toStatic) {
        super.move(from, to);
        int offsetDiff = 0;
        int toOffset = BooleanUtils.intValue(!toStatic);
        ArgumentInfoCollection.Builder builder = ArgumentInfoCollection.builder();
        if (fromStatic != toStatic) {
          assert toStatic;
          offsetDiff = 1;
          builder.addArgumentInfo(
              0, new RewrittenTypeInfo(from.holder, to.proto.parameters.values[0]));
        }
        for (int i = 0; i < from.proto.parameters.size(); i++) {
          DexType fromType = from.proto.parameters.values[i];
          DexType toType = to.proto.parameters.values[i + offsetDiff];
          if (fromType != toType) {
            builder.addArgumentInfo(
                i + offsetDiff + toOffset, new RewrittenTypeInfo(fromType, toType));
          }
        }
        RewrittenTypeInfo returnInfo =
            from.proto.returnType == to.proto.returnType
                ? null
                : new RewrittenTypeInfo(from.proto.returnType, to.proto.returnType);
        prototypeChanges.put(
            to, RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build()));
      }

      public EnumUnboxingLens build(
          DexItemFactory dexItemFactory, GraphLense previousLense, Set<DexType> unboxedEnums) {
        if (typeMap.isEmpty() && methodMap.isEmpty() && fieldMap.isEmpty()) {
          return null;
        }
        return new EnumUnboxingLens(
            typeMap,
            methodMap,
            fieldMap,
            originalFieldSignatures,
            originalMethodSignatures,
            previousLense,
            dexItemFactory,
            ImmutableMap.copyOf(prototypeChanges),
            ImmutableSet.copyOf(unboxedEnums));
      }
    }
  }
}
