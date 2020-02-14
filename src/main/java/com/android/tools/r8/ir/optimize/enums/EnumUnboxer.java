// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ArrayTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AppInfoWithLiveness.EnumValueInfo;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EnumUnboxer {

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexType> enumsToUnbox;

  private final boolean debugLogEnabled;
  private final Map<DexType, Reason> debugLogs;
  private final DexItemFactory factory;

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
    enumsToUnbox = new EnumUnboxingCandidateAnalysis(appView, this).findCandidates();
  }

  public void unboxEnums(IRCode code) {
    // TODO(b/147860220): To implement.
    // Do not forget static get, which is implicitly valid (no inValue).
  }

  public void analyzeEnums(IRCode code) {
    // Enum <clinit> and <init> are analyzed in between the two processing phases using optimization
    // feedback.
    DexClass dexClass = appView.definitionFor(code.method.method.holder);
    if (dexClass.isEnum() && code.method.isInitializer()) {
      return;
    }
    analyzeEnumsInMethod(code);
  }

  private void markEnumAsUnboxable(Reason reason, DexProgramClass enumClass) {
    assert enumClass.isEnum();
    reportFailure(enumClass.type, reason);
    enumsToUnbox.remove(enumClass.type);
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(TypeLatticeElement lattice) {
    if (lattice.isClassType()) {
      DexType classType = lattice.asClassTypeLatticeElement().getClassType();
      return getEnumUnboxingCandidateOrNull(classType);
    }
    if (lattice.isArrayType()) {
      ArrayTypeLatticeElement arrayLattice = lattice.asArrayTypeLatticeElement();
      if (arrayLattice.getArrayBaseTypeLattice().isClassType()) {
        DexType classType =
            arrayLattice.getArrayBaseTypeLattice().asClassTypeLatticeElement().getClassType();
        return getEnumUnboxingCandidateOrNull(classType);
      }
    }
    return null;
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(DexType anyType) {
    if (!enumsToUnbox.contains(anyType)) {
      return null;
    }
    return appView.definitionForProgramType(anyType);
  }

  private void analyzeEnumsInMethod(IRCode code) {
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        Value outValue = instruction.outValue();
        DexProgramClass enumClass =
            outValue == null ? null : getEnumUnboxingCandidateOrNull(outValue.getTypeLattice());
        if (enumClass != null) {
          validateEnumUsages(code, outValue.uniqueUsers(), outValue.uniquePhiUsers(), enumClass);
        }
      }
      for (Phi phi : block.getPhis()) {
        DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(phi.getTypeLattice());
        if (enumClass != null) {
          validateEnumUsages(code, phi.uniqueUsers(), phi.uniquePhiUsers(), enumClass);
        }
      }
    }
  }

  private Reason validateEnumUsages(
      IRCode code, Set<Instruction> uses, Set<Phi> phiUses, DexProgramClass enumClass) {
    for (Instruction user : uses) {
      Reason reason = instructionAllowEnumUnboxing(user, code, enumClass);
      if (reason != Reason.ELIGIBLE) {
        markEnumAsUnboxable(reason, enumClass);
        return reason;
      }
    }
    for (Phi phi : phiUses) {
      for (Value operand : phi.getOperands()) {
        if (getEnumUnboxingCandidateOrNull(operand.getTypeLattice()) != enumClass) {
          markEnumAsUnboxable(Reason.INVALID_PHI, enumClass);
          return Reason.INVALID_PHI;
        }
      }
    }
    return Reason.ELIGIBLE;
  }

  public void finishEnumAnalysis() {
    for (DexType toUnbox : enumsToUnbox) {
      DexProgramClass enumClass = appView.definitionForProgramType(toUnbox);
      assert enumClass != null;

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
        continue;
      }

      Map<DexField, EnumValueInfo> enumValueInfoMapFor =
          appView.appInfo().withLiveness().getEnumValueInfoMapFor(enumClass.type);
      if (enumValueInfoMapFor == null) {
        markEnumAsUnboxable(Reason.MISSING_INFO_MAP, enumClass);
        continue;
      }
      if (enumValueInfoMapFor.size() != enumClass.staticFields().size() - 1) {
        markEnumAsUnboxable(Reason.UNEXPECTED_STATIC_FIELD, enumClass);
      }
    }
    if (debugLogEnabled) {
      reportEnumsAnalysis();
    }
  }

  private Reason instructionAllowEnumUnboxing(
      Instruction instruction, IRCode code, DexProgramClass enumClass) {

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
      DexEncodedMethod invokedEncodedMethod =
          invokeMethod.lookupSingleTarget(appView, code.method.method.holder);
      if (invokedEncodedMethod == null) {
        return Reason.INVALID_INVOKE;
      }
      DexMethod invokedMethod = invokedEncodedMethod.method;
      DexClass dexClass = appView.definitionFor(invokedMethod.holder);
      if (dexClass == null) {
        return Reason.INVALID_INVOKE;
      }
      if (dexClass.isProgramClass()) {
        // All invokes in the program are generally valid, but specific care is required
        // for values() and valueOf().
        if (dexClass.isEnum() && factory.enumMethods.isValuesMethod(invokedMethod, dexClass)) {
          return Reason.VALUES_INVOKE;
        }
        if (dexClass.isEnum() && factory.enumMethods.isValueOfMethod(invokedMethod, dexClass)) {
          return Reason.VALUE_OF_INVOKE;
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
      // TODO(b/147860220): Methods toString(), name(), compareTo(), EnumSet and EnumMap may be
      // interesting to model. A the moment rewrite only Enum#ordinal().
      if (debugLogEnabled) {
        if (invokedMethod == factory.enumMethods.compareTo) {
          return Reason.COMPARE_TO_INVOKE;
        }
        if (invokedMethod == factory.enumMethods.name) {
          return Reason.NAME_INVOKE;
        }
        if (invokedMethod == factory.enumMethods.toString) {
          return Reason.TO_STRING_INVOKE;
        }
      }
      if (invokedMethod != factory.enumMethods.ordinal) {
        return Reason.UNSUPPORTED_LIBRARY_CALL;
      }
      return Reason.ELIGIBLE;
    }

    // A field put is valid only if the field is not on an enum, and the field type and the valuePut
    // have identical enum type.
    if (instruction.isFieldPut()) {
      FieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexEncodedField field = appView.appInfo().resolveField(fieldInstruction.getField());
      if (field == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      DexProgramClass dexClass = appView.definitionForProgramType(field.field.holder);
      if (dexClass == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      if (dexClass.isEnum()) {
        return Reason.FIELD_PUT_ON_ENUM;
      }
      // The put value has to be of the field type.
      if (field.field.type != enumClass.type) {
        return Reason.TYPE_MISSMATCH_FIELD_PUT;
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
      TypeLatticeElement leftType = anIf.lhs().getTypeLattice();
      TypeLatticeElement rightType = anIf.rhs().getTypeLattice();
      if (leftType.equalUpToNullability(rightType)) {
        assert leftType.isClassType();
        assert leftType.asClassTypeLatticeElement().getClassType() == enumClass.type;
        return Reason.ELIGIBLE;
      }
      return Reason.INVALID_IF_TYPES;
    }

    if (instruction.isAssume()) {
      Value outValue = instruction.outValue();
      return validateEnumUsages(code, outValue.uniqueUsers(), outValue.uniquePhiUsers(), enumClass);
    }

    // Return is used for valueOf methods.
    if (instruction.isReturn()) {
      DexType returnType = code.method.method.proto.returnType;
      if (returnType != enumClass.type && returnType.toBaseType(factory) != enumClass.type) {
        return Reason.IMPLICIT_UP_CAST_IN_RETURN;
      }
      return Reason.ELIGIBLE;
    }

    return Reason.OTHER_UNSUPPORTED_INSTRUCTION;
  }

  private void reportEnumsAnalysis() {
    assert debugLogEnabled;
    Reporter reporter = appView.options().reporter;
    reporter.info(
        new StringDiagnostic(
            "Unboxed enums (Unboxing succeeded "
                + enumsToUnbox.size()
                + "): "
                + Arrays.toString(enumsToUnbox.toArray())));
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

  public enum Reason {
    ELIGIBLE,
    SUBTYPES,
    INTERFACE,
    INSTANCE_FIELD,
    UNEXPECTED_STATIC_FIELD,
    VIRTUAL_METHOD,
    UNEXPECTED_DIRECT_METHOD,
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
    TO_STRING_INVOKE,
    NAME_INVOKE,
    UNSUPPORTED_LIBRARY_CALL,
    MISSING_INFO_MAP,
    INVALID_FIELD_PUT,
    FIELD_PUT_ON_ENUM,
    TYPE_MISSMATCH_FIELD_PUT,
    INVALID_IF_TYPES,
    OTHER_UNSUPPORTED_INSTRUCTION;
  }
}
