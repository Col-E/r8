// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer.Reason;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class EnumUnboxingCandidateAnalysis {

  private final AppView<AppInfoWithLiveness> appView;
  private final EnumUnboxer enumUnboxer;
  private final DexItemFactory factory;
  private Map<DexType, Map<DexEncodedMethod, ProgramMethod>> enumToUnboxCandidates =
      new ConcurrentHashMap<>();

  EnumUnboxingCandidateAnalysis(AppView<AppInfoWithLiveness> appView, EnumUnboxer enumUnboxer) {
    this.appView = appView;
    this.enumUnboxer = enumUnboxer;
    factory = appView.dexItemFactory();
  }

  Map<DexType, Map<DexEncodedMethod, ProgramMethod>> findCandidates() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isEnumUnboxingCandidate(clazz)) {
        enumToUnboxCandidates.put(clazz.type, new ConcurrentHashMap<>());
      }
    }
    removeEnumsInAnnotations();
    removePinnedCandidates();
    if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
      enumToUnboxCandidates.remove(appView.protoShrinker().references.methodToInvokeType);
    }
    return enumToUnboxCandidates;
  }

  private boolean isEnumUnboxingCandidate(DexProgramClass clazz) {
    if (!clazz.isEnum()) {
      return false;
    }
    if (!clazz.isEffectivelyFinal(appView)) {
      enumUnboxer.reportFailure(clazz.type, Reason.SUBTYPES);
      return false;
    }
    // TODO(b/147860220): interfaces without default methods should be acceptable if the build setup
    // is correct (all abstract methods are implemented).
    if (!clazz.interfaces.isEmpty()) {
      enumUnboxer.reportFailure(clazz.type, Reason.INTERFACE);
      return false;
    }
    if (!clazz.instanceFields().isEmpty()) {
      enumUnboxer.reportFailure(clazz.type, Reason.INSTANCE_FIELD);
      return false;
    }
    if (!enumHasBasicStaticFields(clazz)) {
      enumUnboxer.reportFailure(clazz.type, Reason.UNEXPECTED_STATIC_FIELD);
      return false;
    }
    if (!clazz.virtualMethods().isEmpty()) {
      enumUnboxer.reportFailure(clazz.type, Reason.VIRTUAL_METHOD);
      return false;
    }
    EnumValueInfoMap enumValueInfoMap =
        appView.appInfo().withLiveness().getEnumValueInfoMap(clazz.type);
    if (enumValueInfoMap == null) {
      enumUnboxer.reportFailure(clazz.type, Reason.MISSING_INFO_MAP);
      return false;
    }
    // Methods values, valueOf, init, clinit are present on each enum.
    // Methods init and clinit are required if the enum is used.
    // Methods valueOf and values are normally kept by the commonly used/recommended enum keep rule
    // -keepclassmembers,allowoptimization enum * {
    //     public static **[] values();
    //     public static ** valueOf(java.lang.String);
    // }
    // In general there will be 4 methods, unless the enum keep rule is not present.
    if (clazz.directMethods().size() > 4) {
      enumUnboxer.reportFailure(clazz.type, Reason.UNEXPECTED_DIRECT_METHOD);
      return false;
    }
    for (DexEncodedMethod directMethod : clazz.directMethods()) {
      if (!(factory.enumMethods.isValuesMethod(directMethod.method, clazz)
          || factory.enumMethods.isValueOfMethod(directMethod.method, clazz)
          || isStandardEnumInitializer(directMethod)
          || directMethod.isClassInitializer())) {
        enumUnboxer.reportFailure(clazz.type, Reason.UNEXPECTED_DIRECT_METHOD);
        return false;
      }
    }
    return true;
  }

  private boolean isStandardEnumInitializer(DexEncodedMethod method) {
    return method.isInstanceInitializer()
        && method.method.proto == factory.enumMethods.constructor.proto;
  }

  // The enum should have the $VALUES static field and only fields directly referencing the enum
  // instances.
  private boolean enumHasBasicStaticFields(DexProgramClass clazz) {
    for (DexEncodedField staticField : clazz.staticFields()) {
      if (staticField.field.type == clazz.type
          && staticField.accessFlags.isEnum()
          && staticField.accessFlags.isFinal()) {
        // Enum field, valid, do nothing.
      } else if (staticField.field.type.isArrayType()
          && staticField.field.type.toArrayElementType(factory) == clazz.type
          && staticField.accessFlags.isSynthetic()
          && staticField.accessFlags.isFinal()
          && staticField.field.name == factory.enumValuesFieldName) {
        // Field $VALUES, valid, do nothing.
      } else {
        return false;
      }
    }
    return true;
  }

  private void removeEnumsInAnnotations() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isAnnotation()) {
        assert clazz.interfaces.contains(appView.dexItemFactory().annotationType);
        removeEnumsInAnnotation(clazz);
      }
    }
  }

  private void removeEnumsInAnnotation(DexProgramClass clazz) {
    // Browse annotation values types in search for enum.
    // Each annotation value is represented by a virtual method.
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      assert method.parameters().isEmpty()
          || appView.options().testing.allowInjectedAnnotationMethods;
      DexType valueType = method.returnType().toBaseType(appView.dexItemFactory());
      if (enumToUnboxCandidates.containsKey(valueType)) {
        enumUnboxer.reportFailure(valueType, Reason.ANNOTATION);
        enumToUnboxCandidates.remove(valueType);
      }
    }
  }

  private void removePinnedCandidates() {
    // A holder type, for field or method, should block enum unboxing only if the enum type is
    // also kept. This is to allow the keep rule -keepclassmembers to be used on enums while
    // enum unboxing can still be performed.
    for (DexReference item : appView.appInfo().getPinnedItems()) {
      if (item.isDexType()) {
        removePinnedCandidate(item.asDexType());
      } else if (item.isDexField()) {
        DexField field = item.asDexField();
        removePinnedIfNotHolder(field, field.type);
      } else {
        assert item.isDexMethod();
        DexMethod method = item.asDexMethod();
        DexProto proto = method.proto;
        removePinnedIfNotHolder(method, proto.returnType);
        for (DexType parameterType : proto.parameters.values) {
          removePinnedIfNotHolder(method, parameterType);
        }
      }
    }
  }

  private void removePinnedIfNotHolder(DexMember<?, ?> member, DexType type) {
    DexType baseType = type.toBaseType(factory);
    if (baseType != member.holder) {
      removePinnedCandidate(baseType);
    }
  }

  private void removePinnedCandidate(DexType type) {
    if (enumToUnboxCandidates.containsKey(type)) {
      enumUnboxer.reportFailure(type, Reason.PINNED);
      enumToUnboxCandidates.remove(type);
    }
  }
}
