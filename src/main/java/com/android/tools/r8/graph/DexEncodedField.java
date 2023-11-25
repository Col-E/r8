// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.optimize.info.DefaultFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.kotlin.KotlinFieldLevelInfo;
import com.android.tools.r8.kotlin.KotlinMetadataUtils;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

public class DexEncodedField extends DexEncodedMember<DexEncodedField, DexField>
    implements StructuralItem<DexEncodedField> {

  public static final DexEncodedField[] EMPTY_ARRAY = {};

  public final FieldAccessFlags accessFlags;
  private DexValue staticValue;
  private final boolean deprecated;
  /** Generic signature information if the attribute is present in the input */
  private FieldTypeSignature genericSignature;

  private FieldOptimizationInfo optimizationInfo = DefaultFieldOptimizationInfo.getInstance();
  private KotlinFieldLevelInfo kotlinMemberInfo = getNoKotlinInfo();

  // Mark indicating if this field has been identified as potentially inlined by javac.
  // This is to ensure consistent tracing in the second round of tree shaking. Remove this field
  // once conditional rules are represented by rule-instances rather than reevaluating rule-schemas.
  private boolean isInlinableByJavaC = false;

  private static void specify(StructuralSpecification<DexEncodedField, ?> spec) {
    spec.withItem(DexEncodedField::getReference)
        .withItem(DexEncodedField::getAccessFlags)
        .withNullableItem(f -> f.staticValue)
        .withBool(DexEncodedField::isDeprecated)
        // TODO(b/171867022): The generic signature should be part of the definition.
        .withAssert(f -> f.genericSignature.hasNoSignature());
    // TODO(b/171867022): Should the optimization info and member info be part of the definition?
  }

  private DexEncodedField(
      DexField field,
      FieldAccessFlags accessFlags,
      FieldTypeSignature genericSignature,
      DexAnnotationSet annotations,
      DexValue staticValue,
      ComputedApiLevel apiLevel,
      boolean deprecated,
      boolean d8R8Synthesized) {
    super(field, annotations, d8R8Synthesized, apiLevel);
    this.accessFlags = accessFlags;
    this.staticValue = staticValue;
    this.deprecated = deprecated;
    this.genericSignature = genericSignature;
    assert genericSignature != null;
    assert GenericSignatureUtils.verifyNoDuplicateGenericDefinitions(genericSignature, annotations);
  }

  @Override
  public StructuralMapping<DexEncodedField> getStructuralMapping() {
    return DexEncodedField::specify;
  }

  @Override
  public DexEncodedField self() {
    return this;
  }

  public DexType type() {
    return getReference().type;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  public boolean isProgramField(DexDefinitionSupplier definitions) {
    if (getReference().holder.isClassType()) {
      DexClass clazz = definitions.definitionFor(getReference().holder);
      return clazz != null && clazz.isProgramClass();
    }
    return false;
  }

  @Override
  public FieldOptimizationInfo getOptimizationInfo() {
    return optimizationInfo;
  }

  @Override
  public ComputedApiLevel getApiLevel() {
    return getApiLevelForDefinition();
  }

  public synchronized MutableFieldOptimizationInfo getMutableOptimizationInfo() {
    MutableFieldOptimizationInfo mutableInfo = optimizationInfo.toMutableOptimizationInfo();
    optimizationInfo = mutableInfo;
    return mutableInfo;
  }

  public void setOptimizationInfo(MutableFieldOptimizationInfo info) {
    optimizationInfo = info;
  }

  @Override
  public KotlinFieldLevelInfo getKotlinInfo() {
    return kotlinMemberInfo;
  }

  @Override
  public void clearKotlinInfo() {
    kotlinMemberInfo = getNoKotlinInfo();
  }

  @Override
  public FieldAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public void setKotlinMemberInfo(KotlinFieldLevelInfo kotlinMemberInfo) {
    assert this.kotlinMemberInfo == getNoKotlinInfo();
    this.kotlinMemberInfo = kotlinMemberInfo;
  }

  @Override
  protected void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    annotations().collectMixedSectionItems(mixedItems);
  }

  @Override
  public String toString() {
    return "Encoded field " + getReference();
  }

  @Override
  public String toSmaliString() {
    return getReference().toSmaliString();
  }

  @Override
  public String toSourceString() {
    return getReference().toSourceString();
  }

  public DexType getType() {
    return getReference().getType();
  }

  public TypeElement getTypeElement(AppView<?> appView) {
    return getReference().getTypeElement(appView);
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

  @Override
  public <T> T apply(
      Function<DexEncodedField, T> fieldConsumer, Function<DexEncodedMethod, T> methodConsumer) {
    return fieldConsumer.apply(this);
  }

  public DexClassAndField asClassField(DexDefinitionSupplier definitions) {
    assert getHolderType().isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(getReference()));
    if (clazz != null) {
      return DexClassAndField.create(clazz, this);
    }
    return null;
  }

  public ProgramField asProgramField(DexDefinitionSupplier definitions) {
    assert getHolderType().isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(getReference()));
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

  public boolean isInstance() {
    return !isStatic();
  }

  @Override
  public boolean isStatic() {
    return accessFlags.isStatic();
  }

  public boolean isPackagePrivate() {
    return accessFlags.isPackagePrivate();
  }

  public boolean isProtected() {
    return accessFlags.isProtected();
  }

  @Override
  public boolean isStaticMember() {
    return isStatic();
  }

  public boolean isSynthetic() {
    return accessFlags.isSynthetic();
  }

  public boolean isVolatile() {
    return accessFlags.isVolatile();
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

  @SuppressWarnings("ReferenceEquality")
  public DexValue getStaticValue() {
    assert accessFlags.isStatic();
    return staticValue == null ? DexValue.defaultForType(getReference().type) : staticValue;
  }

  public DexEncodedField toTypeSubstitutedField(AppView<?> appView, DexField field) {
    return toTypeSubstitutedField(appView, field, ConsumerUtils.emptyConsumer());
  }

  @SuppressWarnings("ReferenceEquality")
  public DexEncodedField toTypeSubstitutedField(
      AppView<?> appView, DexField field, Consumer<Builder> consumer) {
    if (this.getReference() == field) {
      return this;
    }
    return builder(this)
        .setField(field)
        .disableAndroidApiLevelCheckIf(
            !appView.options().apiModelingOptions().enableApiCallerIdentification
                || !appView.enableWholeProgramOptimizations())
        .apply(consumer)
        .build();
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean validateDexValue(DexItemFactory factory) {
    if (!accessFlags.isStatic() || staticValue == null) {
      return true;
    }
    if (getReference().type.isPrimitiveType()) {
      assert staticValue.getType(factory) == getReference().type
          : "Static " + getReference() + " has invalid static value " + staticValue + ".";
    }
    if (staticValue.isDexValueNull()) {
      assert getReference().type.isReferenceType()
          : "Static " + getReference() + " has invalid null static value.";
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

  @Override
  public void clearGenericSignature() {
    this.genericSignature = FieldTypeSignature.noSignature();
  }

  public static Builder builder() {
    return new Builder(false);
  }

  public static Builder builder(DexEncodedField from) {
    return new Builder(from.isD8R8Synthesized(), from);
  }

  public static Builder syntheticBuilder() {
    return new Builder(true);
  }

  public void markAsInlinableByJavaC() {
    isInlinableByJavaC = true;
  }

  public boolean getIsInlinableByJavaC() {
    return isInlinableByJavaC;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean getOrComputeIsInlinableByJavaC(DexItemFactory dexItemFactory) {
    if (getIsInlinableByJavaC()) {
      return true;
    }
    if (!isStatic() || !isFinal()) {
      return false;
    }
    if (!hasExplicitStaticValue()) {
      return false;
    }
    if (getType().isPrimitiveType()) {
      return true;
    }
    if (getType() != dexItemFactory.stringType) {
      return false;
    }
    if (!getStaticValue().isDexValueString()) {
      return false;
    }
    markAsInlinableByJavaC();
    return true;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DexEncodedField that = (DexEncodedField) o;

    if (deprecated != that.deprecated) return false;
    if (isInlinableByJavaC != that.isInlinableByJavaC) return false;
    if (!Objects.equals(accessFlags, that.accessFlags)) return false;
    if (!Objects.equals(staticValue, that.staticValue)) return false;
    if (!Objects.equals(genericSignature, that.genericSignature))
      return false;
    if (!Objects.equals(optimizationInfo, that.optimizationInfo))
      return false;
    return Objects.equals(kotlinMemberInfo, that.kotlinMemberInfo);
  }

  @Override
  public int hashCode() {
    int result = accessFlags != null ? accessFlags.hashCode() : 0;
    result = 31 * result + (staticValue != null ? staticValue.hashCode() : 0);
    result = 31 * result + (deprecated ? 1 : 0);
    result = 31 * result + (genericSignature != null ? genericSignature.hashCode() : 0);
    result = 31 * result + (optimizationInfo != null ? optimizationInfo.hashCode() : 0);
    result = 31 * result + (kotlinMemberInfo != null ? kotlinMemberInfo.hashCode() : 0);
    result = 31 * result + (isInlinableByJavaC ? 1 : 0);
    return result;
  }

  public static class Builder {

    private DexField field;
    private DexAnnotationSet annotations = DexAnnotationSet.empty();
    private FieldAccessFlags accessFlags;
    private FieldTypeSignature genericSignature = FieldTypeSignature.noSignature();
    private KotlinFieldLevelInfo kotlinInfo = KotlinMetadataUtils.getNoKotlinInfo();
    private DexValue staticValue = null;
    private ComputedApiLevel apiLevel = ComputedApiLevel.notSet();
    private FieldOptimizationInfo optimizationInfo = DefaultFieldOptimizationInfo.getInstance();
    private boolean deprecated;
    private final boolean d8R8Synthesized;
    private Consumer<DexEncodedField> buildConsumer = ConsumerUtils.emptyConsumer();

    // Checks to impose on the built method. They should always be active to start with and be
    // lowered on the use site.
    private boolean checkAndroidApiLevel = true;

    private Builder(boolean d8R8Synthesized) {
      this.d8R8Synthesized = d8R8Synthesized;
    }

    private Builder(boolean d8R8Synthesized, DexEncodedField from) {
      // Copy all the mutable state of a DexEncodedField here.
      field = from.getReference();
      accessFlags = from.accessFlags.copy();
      genericSignature = from.getGenericSignature();
      kotlinInfo = from.getKotlinInfo();
      annotations = from.annotations();
      staticValue = from.staticValue;
      apiLevel = from.getApiLevel();
      optimizationInfo =
          from.optimizationInfo.isMutableOptimizationInfo()
              ? from.optimizationInfo.asMutableFieldOptimizationInfo().mutableCopy()
              : from.optimizationInfo;
      deprecated = from.isDeprecated();
      this.d8R8Synthesized = d8R8Synthesized;
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public Builder modifyAccessFlags(Consumer<FieldAccessFlags> consumer) {
      consumer.accept(accessFlags);
      return this;
    }

    public Builder clearDynamicType() {
      return addBuildConsumer(
          fixedUpField ->
              OptimizationFeedbackSimple.getInstance()
                  .markFieldHasDynamicType(fixedUpField, DynamicType.unknown()));
    }

    public Builder clearAnnotations() {
      return setAnnotations(DexAnnotationSet.empty());
    }

    public Builder setAnnotations(DexAnnotationSet annotations) {
      this.annotations = annotations;
      return this;
    }

    private Builder addBuildConsumer(Consumer<DexEncodedField> consumer) {
      this.buildConsumer = this.buildConsumer.andThen(consumer);
      return this;
    }

    public Builder setField(DexField field) {
      this.field = field;
      return this;
    }

    public Builder setAccessFlags(FieldAccessFlags accessFlags) {
      this.accessFlags = accessFlags;
      return this;
    }

    public Builder setApiLevel(ComputedApiLevel apiLevel) {
      this.apiLevel = apiLevel;
      return this;
    }

    public Builder setGenericSignature(FieldTypeSignature genericSignature) {
      this.genericSignature = genericSignature;
      return this;
    }

    public Builder setStaticValue(DexValue staticValue) {
      this.staticValue = staticValue;
      return this;
    }

    public Builder setDeprecated(boolean deprecated) {
      this.deprecated = deprecated;
      return this;
    }

    public Builder disableAndroidApiLevelCheck() {
      return disableAndroidApiLevelCheckIf(true);
    }

    public Builder disableAndroidApiLevelCheckIf(boolean shouldDisable) {
      if (shouldDisable) {
        checkAndroidApiLevel = false;
      }
      return this;
    }

    public DexEncodedField build() {
      assert field != null;
      assert accessFlags != null;
      assert genericSignature != null;
      assert annotations != null;
      assert !checkAndroidApiLevel || !apiLevel.isNotSetApiLevel();
      DexEncodedField dexEncodedField =
          new DexEncodedField(
              field,
              accessFlags,
              genericSignature,
              annotations,
              staticValue,
              apiLevel,
              deprecated,
              d8R8Synthesized);
      dexEncodedField.setKotlinMemberInfo(kotlinInfo);
      dexEncodedField.optimizationInfo = optimizationInfo;
      buildConsumer.accept(dexEncodedField);
      return dexEncodedField;
    }
  }
}
