// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmPropertyVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmPropertyExtensionVisitor;

// Holds information about KmProperty
public class KotlinPropertyInfo implements KotlinFieldLevelInfo, KotlinMethodLevelInfo {

  // Original flags.
  private final int flags;

  // Original getter flags. E.g., for property getter.
  private final int getterFlags;

  // Original setter flags. E.g., for property setter.
  private final int setterFlags;

  // Original property name for (extension) property. Otherwise, null.
  private final String name;

  // Original return type information. This should never be NULL (even for setters without field).
  private final KotlinTypeInfo returnType;

  private final KotlinTypeInfo receiverParameterType;

  private final KotlinValueParameterInfo setterParameter;

  private final List<KotlinTypeParameterInfo> typeParameters;

  private final int jvmFlags;

  private final KotlinJvmFieldSignatureInfo fieldSignature;

  private final KotlinJvmMethodSignatureInfo getterSignature;

  private final KotlinJvmMethodSignatureInfo setterSignature;

  private final KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations;

  private KotlinPropertyInfo(
      int flags,
      int getterFlags,
      int setterFlags,
      String name,
      KotlinTypeInfo returnType,
      KotlinTypeInfo receiverParameterType,
      KotlinValueParameterInfo setterParameter,
      List<KotlinTypeParameterInfo> typeParameters,
      int jvmFlags,
      KotlinJvmFieldSignatureInfo fieldSignature,
      KotlinJvmMethodSignatureInfo getterSignature,
      KotlinJvmMethodSignatureInfo setterSignature,
      KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations) {
    this.flags = flags;
    this.getterFlags = getterFlags;
    this.setterFlags = setterFlags;
    this.name = name;
    this.returnType = returnType;
    this.receiverParameterType = receiverParameterType;
    this.setterParameter = setterParameter;
    this.typeParameters = typeParameters;
    this.jvmFlags = jvmFlags;
    this.fieldSignature = fieldSignature;
    this.getterSignature = getterSignature;
    this.setterSignature = setterSignature;
    this.syntheticMethodForAnnotations = syntheticMethodForAnnotations;
  }

  public static KotlinPropertyInfo create(
      KmProperty kmProperty, DexDefinitionSupplier definitionSupplier, Reporter reporter) {
    return new KotlinPropertyInfo(
        kmProperty.getFlags(),
        kmProperty.getGetterFlags(),
        kmProperty.getSetterFlags(),
        kmProperty.getName(),
        KotlinTypeInfo.create(kmProperty.getReturnType(), definitionSupplier, reporter),
        KotlinTypeInfo.create(kmProperty.getReceiverParameterType(), definitionSupplier, reporter),
        KotlinValueParameterInfo.create(
            kmProperty.getSetterParameter(), definitionSupplier, reporter),
        KotlinTypeParameterInfo.create(
            kmProperty.getTypeParameters(), definitionSupplier, reporter),
        JvmExtensionsKt.getJvmFlags(kmProperty),
        KotlinJvmFieldSignatureInfo.create(
            JvmExtensionsKt.getFieldSignature(kmProperty), definitionSupplier),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getGetterSignature(kmProperty), definitionSupplier),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSetterSignature(kmProperty), definitionSupplier),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSyntheticMethodForAnnotations(kmProperty), definitionSupplier));
  }

  @Override
  public boolean isFieldProperty() {
    return true;
  }

  @Override
  public KotlinPropertyInfo asFieldProperty() {
    return this;
  }

  @Override
  public boolean isProperty() {
    return true;
  }

  @Override
  public KotlinPropertyInfo asProperty() {
    return this;
  }

  void rewrite(
      KmVisitorProviders.KmPropertyVisitorProvider visitorProvider,
      DexEncodedField field,
      DexEncodedMethod getter,
      DexEncodedMethod setter,
      AppView<AppInfoWithLiveness> appView,
      NamingLens namingLens) {
    // TODO(b/154348683): Flags again.
    KmPropertyVisitor kmProperty = visitorProvider.get(flags, name, getterFlags, setterFlags);
    // TODO(b/154348149): ReturnType could have been merged to a subtype.
    if (returnType != null) {
      returnType.rewrite(kmProperty::visitReturnType, appView, namingLens);
    }
    if (receiverParameterType != null) {
      receiverParameterType.rewrite(kmProperty::visitReceiverParameterType, appView, namingLens);
    }
    if (setterParameter != null) {
      setterParameter.rewrite(kmProperty::visitSetterParameter, appView, namingLens);
    }
    for (KotlinTypeParameterInfo typeParameter : typeParameters) {
      typeParameter.rewrite(kmProperty::visitTypeParameter, appView, namingLens);
    }
    JvmPropertyExtensionVisitor extensionVisitor =
        (JvmPropertyExtensionVisitor) kmProperty.visitExtensions(JvmPropertyExtensionVisitor.TYPE);
    if (extensionVisitor != null) {
      extensionVisitor.visit(
          jvmFlags,
          fieldSignature == null ? null : fieldSignature.rewrite(field, appView, namingLens),
          getterSignature == null ? null : getterSignature.rewrite(getter, appView, namingLens),
          setterSignature == null ? null : setterSignature.rewrite(setter, appView, namingLens));
      if (syntheticMethodForAnnotations != null) {
        extensionVisitor.visitSyntheticMethodForAnnotations(
            syntheticMethodForAnnotations.rewrite(null, appView, namingLens));
      }
    }
  }
}
