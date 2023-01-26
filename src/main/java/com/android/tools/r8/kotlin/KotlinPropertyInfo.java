// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.consume;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteIfNotNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.rewriteList;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import java.util.List;
import java.util.function.Consumer;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.jvm.JvmExtensionsKt;

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

  private final KotlinVersionRequirementInfo versionRequirements;

  private final int jvmFlags;

  private final KotlinJvmFieldSignatureInfo fieldSignature;

  private final KotlinJvmMethodSignatureInfo getterSignature;

  private final KotlinJvmMethodSignatureInfo setterSignature;

  private final KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations;

  private final KotlinJvmMethodSignatureInfo syntheticMethodForDelegate;
  // Collection of context receiver types
  private final List<KotlinTypeInfo> contextReceiverTypes;

  private KotlinPropertyInfo(
      int flags,
      int getterFlags,
      int setterFlags,
      String name,
      KotlinTypeInfo returnType,
      KotlinTypeInfo receiverParameterType,
      KotlinValueParameterInfo setterParameter,
      List<KotlinTypeParameterInfo> typeParameters,
      KotlinVersionRequirementInfo versionRequirements,
      int jvmFlags,
      KotlinJvmFieldSignatureInfo fieldSignature,
      KotlinJvmMethodSignatureInfo getterSignature,
      KotlinJvmMethodSignatureInfo setterSignature,
      KotlinJvmMethodSignatureInfo syntheticMethodForAnnotations,
      KotlinJvmMethodSignatureInfo syntheticMethodForDelegate,
      List<KotlinTypeInfo> contextReceiverTypes) {
    this.flags = flags;
    this.getterFlags = getterFlags;
    this.setterFlags = setterFlags;
    this.name = name;
    this.returnType = returnType;
    this.receiverParameterType = receiverParameterType;
    this.setterParameter = setterParameter;
    this.typeParameters = typeParameters;
    this.versionRequirements = versionRequirements;
    this.jvmFlags = jvmFlags;
    this.fieldSignature = fieldSignature;
    this.getterSignature = getterSignature;
    this.setterSignature = setterSignature;
    this.syntheticMethodForAnnotations = syntheticMethodForAnnotations;
    this.syntheticMethodForDelegate = syntheticMethodForDelegate;
    this.contextReceiverTypes = contextReceiverTypes;
  }

  public static KotlinPropertyInfo create(
      KmProperty kmProperty, DexItemFactory factory, Reporter reporter) {
    return new KotlinPropertyInfo(
        kmProperty.getFlags(),
        kmProperty.getGetterFlags(),
        kmProperty.getSetterFlags(),
        kmProperty.getName(),
        KotlinTypeInfo.create(kmProperty.getReturnType(), factory, reporter),
        KotlinTypeInfo.create(kmProperty.getReceiverParameterType(), factory, reporter),
        KotlinValueParameterInfo.create(kmProperty.getSetterParameter(), factory, reporter),
        KotlinTypeParameterInfo.create(kmProperty.getTypeParameters(), factory, reporter),
        KotlinVersionRequirementInfo.create(kmProperty.getVersionRequirements()),
        JvmExtensionsKt.getJvmFlags(kmProperty),
        KotlinJvmFieldSignatureInfo.create(JvmExtensionsKt.getFieldSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getGetterSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSetterSignature(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSyntheticMethodForAnnotations(kmProperty), factory),
        KotlinJvmMethodSignatureInfo.create(
            JvmExtensionsKt.getSyntheticMethodForDelegate(kmProperty), factory),
        ListUtils.map(
            kmProperty.getContextReceiverTypes(),
            contextRecieverType -> KotlinTypeInfo.create(contextRecieverType, factory, reporter)));
  }

  @Override
  public boolean isProperty() {
    return true;
  }

  @Override
  public KotlinPropertyInfo asProperty() {
    return this;
  }

  public KotlinJvmFieldSignatureInfo getFieldSignature() {
    return fieldSignature;
  }

  public KotlinJvmMethodSignatureInfo getGetterSignature() {
    return getterSignature;
  }

  public KotlinJvmMethodSignatureInfo getSetterSignature() {
    return setterSignature;
  }

  boolean rewriteNoBacking(Consumer<KmProperty> consumer, AppView<?> appView) {
    return rewrite(consumer, null, null, null, appView);
  }

  boolean rewrite(
      Consumer<KmProperty> consumer,
      DexEncodedField field,
      DexEncodedMethod getter,
      DexEncodedMethod setter,
      AppView<?> appView) {
    // TODO(b/154348683): Flags again.
    KmProperty kmProperty =
        consume(new KmProperty(flags, name, getterFlags, setterFlags), consumer);
    // TODO(b/154348149): ReturnType could have been merged to a subtype.
    boolean rewritten =
        rewriteIfNotNull(appView, returnType, kmProperty::setReturnType, KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView,
            receiverParameterType,
            kmProperty::setReceiverParameterType,
            KotlinTypeInfo::rewrite);
    rewritten |=
        rewriteIfNotNull(
            appView,
            setterParameter,
            kmProperty::setSetterParameter,
            KotlinValueParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            typeParameters,
            kmProperty.getTypeParameters(),
            KotlinTypeParameterInfo::rewrite);
    rewritten |=
        rewriteList(
            appView,
            contextReceiverTypes,
            kmProperty.getContextReceiverTypes(),
            KotlinTypeInfo::rewrite);
    rewritten |= versionRequirements.rewrite(kmProperty.getVersionRequirements()::addAll);
    if (fieldSignature != null) {
      rewritten |=
          fieldSignature.rewrite(
              newSignature -> JvmExtensionsKt.setFieldSignature(kmProperty, newSignature),
              field,
              appView);
    }
    if (getterSignature != null) {
      rewritten |=
          getterSignature.rewrite(
              newSignature -> JvmExtensionsKt.setGetterSignature(kmProperty, newSignature),
              getter,
              appView);
    }
    if (setterSignature != null) {
      rewritten |=
          setterSignature.rewrite(
              newSignature -> JvmExtensionsKt.setSetterSignature(kmProperty, newSignature),
              setter,
              appView);
    }
    JvmExtensionsKt.setJvmFlags(kmProperty, jvmFlags);
    rewritten |=
        rewriteIfNotNull(
            appView,
            syntheticMethodForAnnotations,
            newMethod -> JvmExtensionsKt.setSyntheticMethodForAnnotations(kmProperty, newMethod),
            KotlinJvmMethodSignatureInfo::rewriteNoBacking);
    rewritten |=
        rewriteIfNotNull(
            appView,
            syntheticMethodForDelegate,
            newMethod -> JvmExtensionsKt.setSyntheticMethodForDelegate(kmProperty, newMethod),
            KotlinJvmMethodSignatureInfo::rewriteNoBacking);
    return rewritten;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    if (returnType != null) {
      returnType.trace(definitionSupplier);
    }
    if (receiverParameterType != null) {
      receiverParameterType.trace(definitionSupplier);
    }
    if (setterParameter != null) {
      setterParameter.trace(definitionSupplier);
    }
    forEachApply(typeParameters, param -> param::trace, definitionSupplier);
    forEachApply(contextReceiverTypes, type -> type::trace, definitionSupplier);
    if (fieldSignature != null) {
      fieldSignature.trace(definitionSupplier);
    }
    if (getterSignature != null) {
      getterSignature.trace(definitionSupplier);
    }
    if (setterSignature != null) {
      setterSignature.trace(definitionSupplier);
    }
    if (syntheticMethodForAnnotations != null) {
      syntheticMethodForAnnotations.trace(definitionSupplier);
    }
    if (syntheticMethodForDelegate != null) {
      syntheticMethodForDelegate.trace(definitionSupplier);
    }
  }
}
