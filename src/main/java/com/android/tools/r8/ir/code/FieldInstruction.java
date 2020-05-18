// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
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
import com.google.common.collect.Sets;
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
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    return instructionInstanceCanThrow(appView, context, SideEffectAssumption.NONE);
  }

  public boolean instructionInstanceCanThrow(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption) {
    SuccessfulFieldResolutionResult resolutionResult =
        appView.appInfo().resolveField(field, context).asSuccessfulResolution();
    if (resolutionResult == null) {
      return true;
    }
    DexEncodedField resolvedField = resolutionResult.getResolvedField();
    // Check if the instruction may fail with an IncompatibleClassChangeError.
    if (resolvedField.isStatic() != isStaticFieldInstruction()) {
      return true;
    }
    // Check if the resolution target is accessible.
    if (resolutionResult.getResolvedHolder() != context.getHolder()) {
      if (resolutionResult
          .isAccessibleFrom(context, appView.appInfo().withClassHierarchy())
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
      if (appView.appInfo().hasLiveness()) {
        AppInfoWithLiveness appInfoWithLiveness = appView.appInfo().withLiveness();
        if (appInfoWithLiveness.noSideEffects.containsKey(resolvedField.field)) {
          return false;
        }
      }
      // May trigger <clinit> that may have side effects.
      if (field.holder.classInitializationMayHaveSideEffects(
          appView,
          // Types that are a super type of `context` are guaranteed to be initialized already.
          type -> appView.isSubtype(context.getHolderType(), type).isTrue(),
          Sets.newIdentityHashSet())) {
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
      AppView<AppInfoWithLiveness> appView, DexEncodedField field) {
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
              .resolveMethodOnClass(dexItemFactory.objectMembers.finalize, clazz)
              .getSingleTarget();
      return resolutionResult != null && resolutionResult.isProgramMethod(appView);
    }

    return appInfo.mayHaveFinalizeMethodDirectlyOrIndirectly(baseType.asClassType());
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    assert isFieldGet();
    DexEncodedField field = appView.appInfo().resolveField(getField()).getResolvedField();
    if (field != null) {
      return field.getOptimizationInfo().getAbstractValue();
    }
    return UnknownValue.getInstance();
  }
}
