// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.CONSTRUCTOR;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.EXTENSION_FUNCTION;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.EXTENSION_PROPERTY_GETTER;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.EXTENSION_PROPERTY_SETTER;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.FUNCTION;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.PROPERTY_GETTER;
import static com.android.tools.r8.kotlin.KotlinMemberInfo.MemberKind.PROPERTY_SETTER;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.getJvmMethodSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.isExtension;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmPropertyProcessor;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmType;
import kotlinx.metadata.KmValueParameter;
import kotlinx.metadata.jvm.JvmMethodSignature;

// Provides access to field/method-level Kotlin information.
public abstract class KotlinMemberInfo {

  private static final List<KotlinValueParameterInfo> EMPTY_PARAM_INFO = ImmutableList.of();

  private static final KotlinMemberInfo NO_KOTLIN_MEMBER_INFO = new NoKotlinMemberInfo();

  public static KotlinMemberInfo getNoKotlinMemberInfo() {
    return NO_KOTLIN_MEMBER_INFO;
  }

  public final MemberKind memberKind;
  // Original member flags. May be necessary to keep Kotlin-specific flag, e.g., suspend function.
  final int flags;

  private KotlinMemberInfo(MemberKind memberKind, int flags) {
    this.memberKind = memberKind;
    this.flags = flags;
  }

  public boolean isFunctionInfo() {
    return false;
  }

  public KotlinFunctionInfo asFunctionInfo() {
    return null;
  }

  public boolean isFieldInfo() {
    return false;
  }

  public KotlinFieldInfo asFieldInfo() {
    return null;
  }

  public boolean isPropertyInfo() {
    return false;
  }

  public KotlinPropertyInfo asPropertyInfo() {
    return null;
  }

  private static class NoKotlinMemberInfo extends KotlinMemberInfo {

    private NoKotlinMemberInfo() {
      super(MemberKind.NONE, 0);
    }
  }

  public static class KotlinFunctionInfo extends KotlinMemberInfo {

    // Information from original KmValueParameter(s) if available.
    final List<KotlinValueParameterInfo> valueParameterInfos;
    // Information from original KmFunction.returnType. Null if this is from a KmConstructor.
    public final KotlinTypeInfo returnType;

    private KotlinFunctionInfo(
        MemberKind memberKind,
        int flags,
        KotlinTypeInfo returnType,
        List<KotlinValueParameterInfo> valueParameterInfos) {
      super(memberKind, flags);
      assert memberKind.isFunction() || memberKind.isConstructor();
      this.returnType = returnType;
      this.valueParameterInfos = valueParameterInfos;
    }

    KotlinValueParameterInfo getValueParameterInfo(int i) {
      if (valueParameterInfos.isEmpty()) {
        return null;
      }
      if (i < 0 || i >= valueParameterInfos.size()) {
        return null;
      }
      return valueParameterInfos.get(i);
    }

    @Override
    public boolean isFunctionInfo() {
      return true;
    }

    @Override
    public KotlinFunctionInfo asFunctionInfo() {
      return this;
    }
  }

  public static class KotlinFieldInfo extends KotlinMemberInfo {

    // Original property name for (extension) property. Otherwise, null.
    final String propertyName;

    private KotlinFieldInfo(MemberKind memberKind, int flags, String propertyName) {
      super(memberKind, flags);
      this.propertyName = propertyName;
    }

    @Override
    public boolean isFieldInfo() {
      return true;
    }

    @Override
    public KotlinFieldInfo asFieldInfo() {
      return this;
    }
  }

  public static class KotlinPropertyInfo extends KotlinMemberInfo {

    // Original getter flags. E.g., for property getter.
    final int getterFlags;

    // Original setter flags. E.g., for property setter.
    final int setterFlags;

    // Original property name for (extension) property. Otherwise, null.
    final String propertyName;

    // Original return type information. This should never be NULL (even for setters without field).
    final KotlinTypeInfo returnType;

    // Information from original KmValueParameter if available.
    final KotlinValueParameterInfo valueParameterInfo;

    private KotlinPropertyInfo(
        MemberKind memberKind,
        int flags,
        int getterFlags,
        int setterFlags,
        String propertyName,
        KotlinTypeInfo returnType,
        KotlinValueParameterInfo valueParameterInfo) {
      super(memberKind, flags);
      this.getterFlags = getterFlags;
      this.setterFlags = setterFlags;
      this.propertyName = propertyName;
      this.returnType = returnType;
      this.valueParameterInfo = valueParameterInfo;
    }

    @Override
    public KotlinPropertyInfo asPropertyInfo() {
      return this;
    }

    @Override
    public boolean isPropertyInfo() {
      return true;
    }
  }

  private static KotlinFunctionInfo createFunctionInfoFromConstructor(KmConstructor kmConstructor) {
    return createFunctionInfo(
        CONSTRUCTOR, kmConstructor.getFlags(), null, kmConstructor.getValueParameters());
  }

  private static KotlinFunctionInfo createFunctionInfo(
      MemberKind memberKind, KmFunction kmFunction) {
    return createFunctionInfo(
        memberKind,
        kmFunction.getFlags(),
        kmFunction.getReturnType(),
        kmFunction.getValueParameters());
  }

  private static KotlinFunctionInfo createFunctionInfo(
      MemberKind memberKind, int flags, KmType returnType, List<KmValueParameter> valueParameters) {
    assert memberKind.isFunction() || memberKind.isConstructor();
    KotlinTypeInfo returnTypeInfo = KotlinTypeInfo.create(returnType);
    assert memberKind.isFunction() || memberKind.isConstructor();
    if (valueParameters.isEmpty()) {
      return new KotlinFunctionInfo(memberKind, flags, returnTypeInfo, EMPTY_PARAM_INFO);
    }
    List<KotlinValueParameterInfo> valueParameterInfos = new ArrayList<>(valueParameters.size());
    for (KmValueParameter kmValueParameter : valueParameters) {
      valueParameterInfos.add(KotlinValueParameterInfo.fromKmValueParameter(kmValueParameter));
    }
    return new KotlinFunctionInfo(memberKind, flags, returnTypeInfo, valueParameterInfos);
  }

  private static KotlinFieldInfo createFieldInfo(MemberKind memberKind, KmProperty kmProperty) {
    assert memberKind.isBackingField() || memberKind.isBackingFieldForCompanionObject();
    return new KotlinFieldInfo(memberKind, kmProperty.getFlags(), kmProperty.getName());
  }

  private static KotlinPropertyInfo createPropertyInfo(
      MemberKind memberKind, KmProperty kmProperty) {
    assert memberKind.isProperty();
    return new KotlinPropertyInfo(
        memberKind,
        kmProperty.getFlags(),
        kmProperty.getGetterFlags(),
        kmProperty.getSetterFlags(),
        kmProperty.getName(),
        KotlinTypeInfo.create(kmProperty.getReturnType()),
        KotlinValueParameterInfo.fromKmValueParameter(kmProperty.getSetterParameter()));
  }

  public enum MemberKind {
    NONE,

    CONSTRUCTOR,
    FUNCTION,
    EXTENSION_FUNCTION,

    COMPANION_OBJECT_BACKING_FIELD,
    PROPERTY_BACKING_FIELD,
    PROPERTY_GETTER,
    PROPERTY_SETTER,
    PROPERTY_ANNOTATIONS,

    // No backing field for extension property.
    EXTENSION_PROPERTY_GETTER,
    EXTENSION_PROPERTY_SETTER,
    EXTENSION_PROPERTY_ANNOTATIONS;

    public boolean isConstructor() {
      return this == CONSTRUCTOR;
    }

    public boolean isFunction() {
      return this == FUNCTION || isExtensionFunction();
    }

    public boolean isExtensionFunction() {
      return this == EXTENSION_FUNCTION;
    }

    public boolean isBackingField() {
      return this == PROPERTY_BACKING_FIELD;
    }

    public boolean isBackingFieldForCompanionObject() {
      return this == COMPANION_OBJECT_BACKING_FIELD;
    }

    public boolean isProperty() {
      return isBackingField()
          || isBackingFieldForCompanionObject()
          || this == PROPERTY_GETTER
          || this == PROPERTY_SETTER
          || this == PROPERTY_ANNOTATIONS
          || isExtensionProperty();
    }

    public boolean isExtensionProperty() {
      return this == EXTENSION_PROPERTY_GETTER
          || this == EXTENSION_PROPERTY_SETTER
          || this == EXTENSION_PROPERTY_ANNOTATIONS;
    }
  }

  static void markKotlinMemberInfo(DexClass clazz, KotlinInfo kotlinInfo, Reporter reporter) {
    if (kotlinInfo == null || !kotlinInfo.hasDeclarations()) {
      return;
    }

    Map<String, KmConstructor> kmConstructorMap = new HashMap<>();
    Map<String, KmFunction> kmFunctionMap = new HashMap<>();
    Map<String, KmProperty> kmPropertyFieldMap = new HashMap<>();
    Map<String, KmProperty> kmPropertyGetterMap = new HashMap<>();
    Map<String, KmProperty> kmPropertySetterMap = new HashMap<>();

    KmDeclarationContainer kmDeclarationContainer = kotlinInfo.getDeclarations();
    String companionObject = null;
    if (kotlinInfo.isClass()) {
      companionObject = kotlinInfo.asClass().kmClass.getCompanionObject();
      kotlinInfo
          .asClass()
          .kmClass
          .getConstructors()
          .forEach(
              kmConstructor -> {
                JvmMethodSignature methodSignature = getJvmMethodSignature(kmConstructor, reporter);
                if (methodSignature != null) {
                  kmConstructorMap.put(methodSignature.asString(), kmConstructor);
                }
              });
    }
    kmDeclarationContainer
        .getFunctions()
        .forEach(
            kmFunction -> {
              JvmMethodSignature methodSignature = getJvmMethodSignature(kmFunction, reporter);
              if (methodSignature != null) {
                kmFunctionMap.put(methodSignature.asString(), kmFunction);
              }
            });
    kmDeclarationContainer.getProperties().forEach(kmProperty -> {
      KmPropertyProcessor propertyProcessor = new KmPropertyProcessor(kmProperty, reporter);
      if (propertyProcessor.fieldSignature() != null) {
        kmPropertyFieldMap.put(propertyProcessor.fieldSignature().asString(), kmProperty);
      }
      if (propertyProcessor.getterSignature() != null) {
        kmPropertyGetterMap.put(propertyProcessor.getterSignature().asString(), kmProperty);
      }
      if (propertyProcessor.setterSignature() != null) {
        kmPropertySetterMap.put(propertyProcessor.setterSignature().asString(), kmProperty);
      }
      // TODO(b/151194869): property annotations
    });

    for (DexEncodedField field : clazz.fields()) {
      if (companionObject != null && companionObject.equals(field.field.name.toString())) {
        assert kotlinInfo.isClass();
        kotlinInfo.asClass().foundCompanionObject(field);
        continue;
      }
      String key = toJvmFieldSignature(field.field).asString();
      if (kmPropertyFieldMap.containsKey(key)) {
        KmProperty kmProperty = kmPropertyFieldMap.get(key);
        field.setKotlinMemberInfo(
            createFieldInfo(
                clazz == kotlinInfo.clazz
                    ? MemberKind.PROPERTY_BACKING_FIELD
                    : MemberKind.COMPANION_OBJECT_BACKING_FIELD,
                kmProperty));
      }
    }

    for (DexEncodedMethod method : clazz.methods()) {
      String key = toJvmMethodSignature(method.method).asString();
      if (kmConstructorMap.containsKey(key)) {
        // Interestingly we cannot assert that the method is a jvm initializer, because the jvm
        // signature can be a different.
        method.setKotlinMemberInfo(createFunctionInfoFromConstructor(kmConstructorMap.get(key)));
      } else if (kmFunctionMap.containsKey(key)) {
        KmFunction kmFunction = kmFunctionMap.get(key);
        method.setKotlinMemberInfo(
            createFunctionInfo(
                isExtension(kmFunction) ? EXTENSION_FUNCTION : FUNCTION, kmFunction));
      } else if (kmPropertyGetterMap.containsKey(key)) {
        KmProperty kmProperty = kmPropertyGetterMap.get(key);
        method.setKotlinMemberInfo(
            createPropertyInfo(
                isExtension(kmProperty) ? EXTENSION_PROPERTY_GETTER : PROPERTY_GETTER, kmProperty));
      } else if (kmPropertySetterMap.containsKey(key)) {
        KmProperty kmProperty = kmPropertySetterMap.get(key);
        method.setKotlinMemberInfo(
            createPropertyInfo(
                isExtension(kmProperty) ? EXTENSION_PROPERTY_SETTER : PROPERTY_SETTER, kmProperty));
      }
    }
  }
}
