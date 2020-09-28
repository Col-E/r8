// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer.Reason;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;

class EnumUnboxingCandidateAnalysis {

  // Each time we unbox an enum with instance fields, we need to generate a method with a
  // switch case to dispatch from the enum to the instance field value. We introduce this heuristic
  // to avoid unboxing enums with too many instance fields.
  private static final int MAX_INSTANCE_FIELDS_FOR_UNBOXING = 7;

  private final AppView<AppInfoWithLiveness> appView;
  private final EnumUnboxer enumUnboxer;
  private final DexItemFactory factory;
  private EnumUnboxingCandidateInfoCollection enumToUnboxCandidates =
      new EnumUnboxingCandidateInfoCollection();

  EnumUnboxingCandidateAnalysis(AppView<AppInfoWithLiveness> appView, EnumUnboxer enumUnboxer) {
    this.appView = appView;
    this.enumUnboxer = enumUnboxer;
    factory = appView.dexItemFactory();
  }

  EnumUnboxingCandidateInfoCollection findCandidates() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (isEnumUnboxingCandidate(clazz)) {
        enumToUnboxCandidates.addCandidate(clazz);
      }
    }
    removeEnumsInAnnotations();
    removePinnedCandidates();
    if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
      enumToUnboxCandidates.removeCandidate(appView.protoShrinker().references.methodToInvokeType);
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
    if (clazz.instanceFields().size() > MAX_INSTANCE_FIELDS_FOR_UNBOXING) {
      enumUnboxer.reportFailure(clazz.type, Reason.MANY_INSTANCE_FIELDS);
      return false;
    }
    if (!enumHasBasicStaticFields(clazz)) {
      enumUnboxer.reportFailure(clazz.type, Reason.UNEXPECTED_STATIC_FIELD);
      return false;
    }
    EnumValueInfoMap enumValueInfoMap =
        appView.appInfo().withLiveness().getEnumValueInfoMap(clazz.type);
    if (enumValueInfoMap == null) {
      enumUnboxer.reportFailure(clazz.type, Reason.MISSING_INFO_MAP);
      return false;
    }
    return true;
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
      } else if (appView.appInfo().isFieldRead(staticField)) {
        // Only non read static fields are valid, and they are assumed unused.
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
      if (enumToUnboxCandidates.isCandidate(valueType)) {
        enumUnboxer.reportFailure(valueType, Reason.ANNOTATION);
        enumToUnboxCandidates.removeCandidate(valueType);
      }
    }
  }

  private void removePinnedCandidates() {
    // A holder type, for field or method, should block enum unboxing only if the enum type is
    // also kept. This is to allow the keep rule -keepclassmembers to be used on enums while
    // enum unboxing can still be performed.
    KeepInfoCollection keepInfo = appView.appInfo().getKeepInfo();
    keepInfo.forEachPinnedType(this::removePinnedCandidate);
    keepInfo.forEachPinnedField(field -> removePinnedIfNotHolder(field, field.type));
    keepInfo.forEachPinnedMethod(
        method -> {
          DexProto proto = method.proto;
          removePinnedIfNotHolder(method, proto.returnType);
          for (DexType parameterType : proto.parameters.values) {
            removePinnedIfNotHolder(method, parameterType);
          }
        });
  }

  private void removePinnedIfNotHolder(DexMember<?, ?> member, DexType type) {
    DexType baseType = type.toBaseType(factory);
    if (baseType != member.holder) {
      removePinnedCandidate(baseType);
    }
  }

  private void removePinnedCandidate(DexType type) {
    if (enumToUnboxCandidates.isCandidate(type)) {
      enumUnboxer.reportFailure(type, Reason.PINNED);
      enumToUnboxCandidates.removeCandidate(type);
    }
  }
}
