// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.NO_KOTLIN_INFO;

import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.optimize.info.DefaultFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.kotlin.KotlinFieldLevelInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;

public class DexEncodedField extends DexEncodedMember<DexEncodedField, DexField> {
  public static final DexEncodedField[] EMPTY_ARRAY = {};

  public final DexField field;
  public final FieldAccessFlags accessFlags;
  private DexValue staticValue;
  private final boolean deprecated;
  /** Generic signature information if the attribute is present in the input */
  private FieldTypeSignature genericSignature;

  private FieldOptimizationInfo optimizationInfo = DefaultFieldOptimizationInfo.getInstance();
  private KotlinFieldLevelInfo kotlinMemberInfo = NO_KOTLIN_INFO;

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue,
      boolean deprecated) {
    super(annotations);
    this.field = field;
    this.accessFlags = accessFlags;
    this.staticValue = staticValue;
    this.deprecated = deprecated;
    this.genericSignature = genericSignature;
    assert genericSignature != null;
    assert GenericSignatureUtils.verifyNoDuplicateGenericDefinitions(genericSignature, annotations);
  }

  public DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue) {
    this(field, accessFlags, genericSignature, annotations, staticValue, false);
  }

  public DexType type() {
    return field.type;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  public boolean isProgramField(DexDefinitionSupplier definitions) {
    if (field.holder.isClassType()) {
      DexClass clazz = definitions.definitionFor(field.holder);
      return clazz != null && clazz.isProgramClass();
    }
    return false;
  }

  public FieldOptimizationInfo getOptimizationInfo() {
    return optimizationInfo;
  }

  public synchronized MutableFieldOptimizationInfo getMutableOptimizationInfo() {
    if (optimizationInfo.isDefaultFieldOptimizationInfo()) {
      MutableFieldOptimizationInfo mutableOptimizationInfo = new MutableFieldOptimizationInfo();
      optimizationInfo = mutableOptimizationInfo;
      return mutableOptimizationInfo;
    }
    assert optimizationInfo.isMutableFieldOptimizationInfo();
    return optimizationInfo.asMutableFieldOptimizationInfo();
  }

  public void setOptimizationInfo(MutableFieldOptimizationInfo info) {
    optimizationInfo = info;
  }

  @Override
  public KotlinFieldLevelInfo getKotlinMemberInfo() {
    return kotlinMemberInfo;
  }

  @Override
  public FieldAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public void setKotlinMemberInfo(KotlinFieldLevelInfo kotlinMemberInfo) {
    assert this.kotlinMemberInfo == NO_KOTLIN_INFO;
    this.kotlinMemberInfo = kotlinMemberInfo;
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    annotations().collectMixedSectionItems(mixedItems);
  }

  @Override
  public String toString() {
    return "Encoded field " + field;
  }

  @Override
  public String toSmaliString() {
    return field.toSmaliString();
  }

  @Override
  public String toSourceString() {
    return field.toSourceString();
  }

  @Override
  public DexField getReference() {
    return field;
  }

  @Override
  public boolean isDexEncodedField() {
    return true;
  }

  @Override
  public DexEncodedField asDexEncodedField() {
    return this;
  }

  @Override
  public ProgramField asProgramMember(DexDefinitionSupplier definitions) {
    return asProgramField(definitions);
  }

  public ProgramField asProgramField(DexDefinitionSupplier definitions) {
    assert holder().isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(field));
    if (clazz != null) {
      return new ProgramField(clazz, this);
    }
    return null;
  }

  public boolean isEnum() {
    return accessFlags.isEnum();
  }

  public boolean isFinal() {
    return accessFlags.isFinal();
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  public boolean isPackagePrivate() {
    return accessFlags.isPackagePrivate();
  }

  public boolean isPrivate() {
    return accessFlags.isPrivate();
  }

  public boolean isProtected() {
    return accessFlags.isProtected();
  }

  public boolean isPublic() {
    return accessFlags.isPublic();
  }

  @Override
  public boolean isStaticMember() {
    return isStatic();
  }

  public boolean isVolatile() {
    return accessFlags.isVolatile();
  }

  public boolean hasAnnotation() {
    return !annotations().isEmpty();
  }

  public boolean hasExplicitStaticValue() {
    assert accessFlags.isStatic();
    return staticValue != null;
  }

  public void setStaticValue(DexValue staticValue) {
    assert accessFlags.isStatic();
    assert staticValue != null;
    this.staticValue = staticValue;
  }

  public void clearStaticValue() {
    assert accessFlags.isStatic();
    this.staticValue = null;
  }

  public DexValue getStaticValue() {
    assert accessFlags.isStatic();
    return staticValue == null ? DexValue.defaultForType(field.type) : staticValue;
  }

  /**
   * Returns a const instructions if this field is a compile time final const.
   *
   * <p>NOTE: It is the responsibility of the caller to check if this field is pinned or not.
   */
  public Instruction valueAsConstInstruction(
      IRCode code, DebugLocalInfo local, AppView<AppInfoWithLiveness> appView) {
    boolean isWritten = appView.appInfo().isFieldWrittenByFieldPutInstruction(this);
    if (!isWritten) {
      // Since the field is not written, we can simply return the default value for the type.
      DexValue value = isStatic() ? getStaticValue() : DexValue.defaultForType(field.type);
      return value.asConstInstruction(appView, code, local);
    }

    // Check if we have a single value for the field according to the field optimization info.
    AbstractValue abstractValue = getOptimizationInfo().getAbstractValue();
    if (abstractValue.isSingleValue()) {
      SingleValue singleValue = abstractValue.asSingleValue();
      if (singleValue.isSingleFieldValue()
          && singleValue.asSingleFieldValue().getField() == field) {
        return null;
      }
      if (singleValue.isMaterializableInContext(appView, code.context())) {
        TypeElement type = TypeElement.fromDexType(field.type, maybeNull(), appView);
        return singleValue.createMaterializingInstruction(
            appView, code, TypeAndLocalInfoSupplier.create(type, local));
      }
    }

    // The only way to figure out whether the static value contains the final value is ensure the
    // value is not the default or check that <clinit> is not present.
    if (accessFlags.isFinal() && isStatic()) {
      DexClass clazz = appView.definitionFor(field.holder);
      if (clazz == null || clazz.hasClassInitializer()) {
        return null;
      }
      DexValue staticValue = getStaticValue();
      if (!staticValue.isDefault(field.type)) {
        return staticValue.asConstInstruction(appView, code, local);
      }
    }

    return null;
  }

  public boolean mayTriggerClassInitializationSideEffects(
      AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    // Only static field matters when it comes to class initialization side effects.
    if (!isStatic()) {
      return false;
    }
    DexClass clazz = appView.definitionFor(field.holder);
    if (clazz == null) {
      return true;
    }
    if (clazz.classInitializationMayHaveSideEffects(
        appView,
        // Types that are a super type of the current context are guaranteed to be initialized
        // already.
        type -> appView.appInfo().isSubtype(context.getHolderType(), type),
        Sets.newIdentityHashSet())) {
      // Ignore class initialization side-effects for dead proto extension fields to ensure that
      // we force replace these field reads by null.
      boolean ignore =
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.isDeadProtoExtensionField(field), false);
      return !ignore;
    }
    return false;
  }

  public DexEncodedField toTypeSubstitutedField(DexField field) {
    if (this.field == field) {
      return this;
    }
    // TODO(b/169923358): Consider removing the fieldSignature here.
    DexEncodedField result =
        new DexEncodedField(field, accessFlags, genericSignature, annotations(), staticValue);
    result.optimizationInfo =
        optimizationInfo.isMutableFieldOptimizationInfo()
            ? optimizationInfo.asMutableFieldOptimizationInfo().mutableCopy()
            : DefaultFieldOptimizationInfo.getInstance();
    return result;
  }

  public boolean validateDexValue(DexItemFactory factory) {
    if (!accessFlags.isStatic() || staticValue == null) {
      return true;
    }
    if (field.type.isPrimitiveType()) {
      assert staticValue.getType(factory) == field.type
          : "Static " + field + " has invalid static value " + staticValue + ".";
    }
    if (staticValue.isDexValueNull()) {
      assert field.type.isReferenceType() : "Static " + field + " has invalid null static value.";
    }
    // TODO(b/150593449): Support non primitive DexValue (String, enum) and add assertions.
    return true;
  }

  public FieldTypeSignature getGenericSignature() {
    return genericSignature;
  }

  public void setGenericSignature(FieldTypeSignature genericSignature) {
    assert genericSignature != null;
    this.genericSignature = genericSignature;
  }

  public void clearGenericSignature() {
    this.genericSignature = FieldTypeSignature.noSignature();
  }
}
