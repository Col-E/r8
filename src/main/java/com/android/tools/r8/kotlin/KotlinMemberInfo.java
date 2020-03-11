// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.toJvmMethodSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataSynthesizer.isExtension;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmFunctionProcessor;
import com.android.tools.r8.kotlin.KotlinMetadataJvmExtensionUtils.KmPropertyProcessor;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kotlinx.metadata.KmDeclarationContainer;
import kotlinx.metadata.KmFunction;
import kotlinx.metadata.KmProperty;
import kotlinx.metadata.KmValueParameter;

// Provides access to field/method-level Kotlin information.
public class KotlinMemberInfo {
  private static final List<KmValueParameter> EMPTY_PARAM = ImmutableList.of();
  private static final List<KotlinValueParameterInfo> EMPTY_PARAM_INFO = ImmutableList.of();

  private static final KotlinMemberInfo NO_KOTLIN_MEMBER_INFO =
      new KotlinMemberInfo(MemberKind.NONE, 0, EMPTY_PARAM);

  public static KotlinMemberInfo getNoKotlinMemberInfo() {
    return NO_KOTLIN_MEMBER_INFO;
  }

  public final MemberKind memberKind;
  // Original member flags. May be necessary to keep Kotlin-specific flag, e.g., suspend function.
  final int flags;
  // TODO(b/151194869): better to split into FunctionInfo v.s. PropertyInfo ?
  // Original property flags. E.g., for property getter, getter flags are stored to `flags`, while
  // the property's flags are stored here, in case of properties without a backing field.
  final int propertyFlags;
  // Original property name for (extension) property. Otherwise, null.
  final String propertyName;
  // Information from original KmValueParameter(s) if available. Otherwise, null.
  private final List<KotlinValueParameterInfo> valueParameterInfos;

  // Constructor for KmFunction
  private KotlinMemberInfo(
      MemberKind memberKind, int flags, List<KmValueParameter> kmValueParameters) {
    this(memberKind, flags, 0, null, kmValueParameters);
  }

  // Constructor for a backing field and a getter in KmProperty
  private KotlinMemberInfo(
      MemberKind memberKind, int flags, int propertyFlags, String propertyName) {
    this(memberKind, flags, propertyFlags, propertyName, EMPTY_PARAM);
  }

  // Constructor for a setter in KmProperty
  private KotlinMemberInfo(
      MemberKind memberKind,
      int flags,
      int propertyFlags,
      String propertyName,
      KmValueParameter kmValueParameter) {
    this(
        memberKind,
        flags,
        propertyFlags,
        propertyName,
        kmValueParameter != null ? ImmutableList.of(kmValueParameter) : EMPTY_PARAM);
  }

  private KotlinMemberInfo(
      MemberKind memberKind,
      int flags,
      int propertyFlags,
      String propertyName,
      List<KmValueParameter> kmValueParameters) {
    this.memberKind = memberKind;
    this.flags = flags;
    this.propertyFlags = propertyFlags;
    this.propertyName = propertyName;
    assert kmValueParameters != null;
    if (kmValueParameters.isEmpty()) {
      this.valueParameterInfos = EMPTY_PARAM_INFO;
    } else {
      this.valueParameterInfos = new ArrayList<>(kmValueParameters.size());
      for (KmValueParameter kmValueParameter : kmValueParameters) {
        valueParameterInfos.add(KotlinValueParameterInfo.fromKmValueParameter(kmValueParameter));
      }
    }
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

  public enum MemberKind {
    NONE,

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

    KmDeclarationContainer kmDeclarationContainer = kotlinInfo.getDeclarations();
    String companionObject = null;
    if (kotlinInfo.isClass()) {
      companionObject = kotlinInfo.asClass().kmClass.getCompanionObject();
    }

    Map<String, KmFunction> kmFunctionMap = new HashMap<>();
    Map<String, KmProperty> kmPropertyFieldMap = new HashMap<>();
    Map<String, KmProperty> kmPropertyGetterMap = new HashMap<>();
    Map<String, KmProperty> kmPropertySetterMap = new HashMap<>();

    kmDeclarationContainer.getFunctions().forEach(kmFunction -> {
      KmFunctionProcessor functionProcessor = new KmFunctionProcessor(kmFunction, reporter);
      if (functionProcessor.signature() != null) {
        kmFunctionMap.put(functionProcessor.signature().asString(), kmFunction);
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
            new KotlinMemberInfo(
                clazz == kotlinInfo.clazz
                    ? MemberKind.PROPERTY_BACKING_FIELD
                    : MemberKind.COMPANION_OBJECT_BACKING_FIELD,
                kmProperty.getFlags(),
                kmProperty.getFlags(),
                kmProperty.getName()));
      }
    }

    for (DexEncodedMethod method : clazz.methods()) {
      if (method.isInitializer()) {
        continue;
      }
      String key = toJvmMethodSignature(method.method).asString();
      if (kmFunctionMap.containsKey(key)) {
        KmFunction kmFunction = kmFunctionMap.get(key);
        if (isExtension(kmFunction)) {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.EXTENSION_FUNCTION,
                  kmFunction.getFlags(),
                  kmFunction.getValueParameters()));
        } else {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.FUNCTION,
                  kmFunction.getFlags(),
                  kmFunction.getValueParameters()));
        }
      } else if (kmPropertyGetterMap.containsKey(key)) {
        KmProperty kmProperty = kmPropertyGetterMap.get(key);
        if (isExtension(kmProperty)) {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.EXTENSION_PROPERTY_GETTER,
                  kmProperty.getGetterFlags(),
                  kmProperty.getFlags(),
                  kmProperty.getName()));
        } else {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.PROPERTY_GETTER,
                  kmProperty.getGetterFlags(),
                  kmProperty.getFlags(),
                  kmProperty.getName()));
        }
      } else if (kmPropertySetterMap.containsKey(key)) {
        KmProperty kmProperty = kmPropertySetterMap.get(key);
        if (isExtension(kmProperty)) {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.EXTENSION_PROPERTY_SETTER,
                  kmProperty.getSetterFlags(),
                  kmProperty.getFlags(),
                  kmProperty.getName(),
                  kmProperty.getSetterParameter()));
        } else {
          method.setKotlinMemberInfo(
              new KotlinMemberInfo(
                  MemberKind.PROPERTY_SETTER,
                  kmProperty.getSetterFlags(),
                  kmProperty.getFlags(),
                  kmProperty.getName(),
                  kmProperty.getSetterParameter()));
        }
      }
    }
  }
}
