// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.shaking.ObjectAllocationInfoCollectionUtils.mayHaveFinalizeMethodDirectlyOrIndirectly;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.ConcreteMutableFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.EmptyFieldSet;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.UnknownFieldSet;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleFieldValue;
import com.android.tools.r8.ir.analysis.value.UnknownValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.List;

public abstract class FieldInstruction extends Instruction {

  private final DexField field;

  protected FieldInstruction(DexField field, Value dest, Value value) {
    this(field, dest, Collections.singletonList(value));
  }

  protected FieldInstruction(DexField field, Value dest, List<Value> inValues) {
    super(dest, inValues);
    assert field != null;
    this.field = field;
  }

  public abstract Value value();

  public FieldMemberType getType() {
    return FieldMemberType.fromDexType(field.type);
  }

  public DexField getField() {
    return field;
  }

  @Override
  public boolean isFieldInstruction() {
    return true;
  }

  @Override
  public FieldInstruction asFieldInstruction() {
    return this;
  }

  @Override
  public boolean instructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      AbstractValueSupplier abstractValueSupplier,
      SideEffectAssumption assumption) {
    return internalInstructionInstanceCanThrow(
        appView, context, assumption, appView.appInfo().resolveField(field, context));
  }

  boolean internalInstructionInstanceCanThrow(
      AppView<?> appView,
      ProgramMethod context,
      SideEffectAssumption assumption,
      FieldResolutionResult resolutionResult) {
    if (!resolutionResult.isSingleFieldResolutionResult()) {
      // Conservatively treat instruction as being throwing.
      return true;
    }
    SingleFieldResolutionResult<?> singleFieldResolutionResult =
        resolutionResult.asSingleFieldResolutionResult();
    DexClassAndField resolvedField = singleFieldResolutionResult.getResolutionPair();
    // Check if the instruction may fail with an IncompatibleClassChangeError.
    if (resolvedField.getAccessFlags().isStatic() != isStaticFieldInstruction()) {
      return true;
    }
    // Check if the resolution target is accessible.
    if (singleFieldResolutionResult.getResolvedHolder() != context.getHolder()) {
      if (singleFieldResolutionResult
          .isAccessibleFrom(context, appView.withClassHierarchy())
          .isPossiblyFalse()) {
        return true;
      }
    }
    // TODO(b/137168535): Without non-null tracking, only locally created receiver is allowed in D8.
    // Check if the instruction may fail with a NullPointerException (null receiver).
    if (isInstanceGet() || isInstancePut()) {
      if (!assumption.canAssumeReceiverIsNotNull()) {
        Value receiver = inValues.get(0);
        if (receiver.isAlwaysNull(appView) || receiver.type.isNullable()) {
          return true;
        }
      }
    }
    // For D8, reaching here means the field is in the same context, hence the class is guaranteed
    // to be initialized already.
    if (!appView.enableWholeProgramOptimizations()) {
      return false;
    }
    boolean mayTriggerClassInitialization =
        isStaticFieldInstruction() && !assumption.canAssumeClassIsAlreadyInitialized();
    if (mayTriggerClassInitialization) {
      // Only check for <clinit> side effects if there is no -assumenosideeffects rule.
      if (appView.getAssumeInfoCollection().isSideEffectFree(resolvedField)) {
        return false;
      }
      // May trigger <clinit> that may have side effects.
      if (field.holder.classInitializationMayHaveSideEffectsInContext(appView, context)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean hasInvariantOutType() {
    // TODO(jsjeon): what if the target field is known to be non-null?
    return true;
  }

  @Override
  public AbstractFieldSet readSet(AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    if (instructionMayTriggerMethodInvocation(appView, context)) {
      // This may trigger class initialization, which could potentially read any field.
      return UnknownFieldSet.getInstance();
    }

    if (isFieldGet()) {
      DexField field = getField();
      DexEncodedField encodedField = null;
      if (appView.enableWholeProgramOptimizations()) {
        encodedField = appView.appInfo().resolveField(field).getResolvedField();
      } else {
        DexClass clazz = appView.definitionFor(field.holder);
        if (clazz != null) {
          encodedField = clazz.lookupField(field);
        }
      }
      if (encodedField != null) {
        return new ConcreteMutableFieldSet(encodedField);
      }
      return UnknownFieldSet.getInstance();
    }

    assert isFieldPut();
    return EmptyFieldSet.getInstance();
  }

  /**
   * Returns {@code true} if this instruction may store an instance of a class that has a non-
   * default finalize() method in a field. In that case, it is not safe to remove this instruction,
   * since that could change the lifetime of the value.
   */
  boolean isStoringObjectWithFinalizer(
      AppView<AppInfoWithLiveness> appView, DexClassAndField field) {
    assert isFieldPut();

    TypeElement type = value().getType();
    TypeElement baseType = type.isArrayType() ? type.asArrayType().getBaseType() : type;
    if (!baseType.isClassType()) {
      return false;
    }

    AbstractValue abstractValue = field.getOptimizationInfo().getAbstractValue();
    if (abstractValue.isSingleValue()) {
      if (abstractValue.isSingleConstValue()) {
        return false;
      }
      if (abstractValue.isSingleFieldValue()) {
        SingleFieldValue singleFieldValue = abstractValue.asSingleFieldValue();
        return singleFieldValue.mayHaveFinalizeMethodDirectlyOrIndirectly(appView);
      }
    }

    AppInfoWithLiveness appInfo = appView.appInfo();
    Value root = value().getAliasedValue();
    if (!root.isPhi() && root.definition.isNewInstance()) {
      DexClass clazz = appView.definitionFor(root.definition.asNewInstance().clazz);
      if (clazz == null) {
        return true;
      }
      if (clazz.superType == null) {
        return false;
      }
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexEncodedMethod resolutionResult =
          appInfo
              .resolveMethodOnClassLegacy(clazz, dexItemFactory.objectMembers.finalize)
              .getSingleTarget();
      return resolutionResult != null && resolutionResult.isProgramMethod(appView);
    }

    return mayHaveFinalizeMethodDirectlyOrIndirectly(appView, baseType.asClassType());
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    assert isFieldGet();
    if (outValue.hasLocalInfo() || !appView.hasClassHierarchy()) {
      return AbstractValue.unknown();
    }
    AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy =
        appView.withClassHierarchy();
    DexEncodedField field =
        appViewWithClassHierarchy.appInfo().resolveField(getField()).getResolvedField();
    if (field != null) {
      return field.getOptimizationInfo().getAbstractValue();
    }
    return UnknownValue.getInstance();
  }
}
