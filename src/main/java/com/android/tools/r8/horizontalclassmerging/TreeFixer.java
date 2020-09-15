// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass.FieldSetter;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.OptionalBool;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tree fixer traverses all program classes and finds and fixes references to old classes which
 * have been remapped to new classes by the class merger. While doing so, all updated changes are
 * tracked in {@link TreeFixer#lensBuilder}.
 */
class TreeFixer {
  private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder;
  private final AppView<AppInfoWithLiveness> appView;

  public TreeFixer(
      AppView<AppInfoWithLiveness> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder) {
    this.lensBuilder = lensBuilder;
    this.fieldAccessChangesBuilder = fieldAccessChangesBuilder;
    this.appView = appView;
  }

  HorizontalClassMergerGraphLens fixupTypeReferences() {
    // Globally substitute merged class types in protos and holders.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.superType = lensBuilder.lookupType(clazz.superType);
      clazz.getMethodCollection().replaceMethods(this::fixupMethod);
      fixupFields(clazz.staticFields(), clazz::setStaticField);
      fixupFields(clazz.instanceFields(), clazz::setInstanceField);
    }
    HorizontalClassMergerGraphLens lens = lensBuilder.build(appView);
    fieldAccessChangesBuilder.build(this::fixupMethod).modify(appView);
    new AnnotationFixer(lens).run(appView.appInfo().classes());
    return lens;
  }

  private DexEncodedMethod fixupMethod(DexEncodedMethod method) {
    DexMethod methodReference = method.method;
    DexMethod newMethodReference = fixupMethod(methodReference);
    if (newMethodReference == methodReference) {
      return method;
    }

    // If the method is a synthesized method, then don't record the original signature.
    if ((method.getCode() instanceof ConstructorEntryPointSynthesizedCode)) {
      assert lensBuilder.hasExtraSignatureMappingFor(methodReference);
      lensBuilder.recordExtraOriginalSignature(methodReference, newMethodReference);
      lensBuilder.mapMethod(methodReference, newMethodReference);
    } else {
      lensBuilder.moveMethod(methodReference, newMethodReference);
    }

    DexEncodedMethod newMethod = method.toTypeSubstitutedMethod(newMethodReference);
    if (newMethod.isNonPrivateVirtualMethod()) {
      // Since we changed the return type or one of the parameters, this method cannot be a
      // classpath or library method override, since we only class merge program classes.
      assert !method.isLibraryMethodOverride().isTrue();
      newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
    }
    return newMethod;
  }

  private void fixupFields(List<DexEncodedField> fields, FieldSetter setter) {
    if (fields == null) {
      return;
    }
    for (int i = 0; i < fields.size(); i++) {
      DexEncodedField encodedField = fields.get(i);
      DexField field = encodedField.field;
      DexType newType = fixupType(field.type);
      DexType newHolder = fixupType(field.holder);
      DexField newField = appView.dexItemFactory().createField(newHolder, newType, field.name);
      if (newField != encodedField.field) {
        // TODO(b/165498187): track mapped fields
        /* lensBuilder.map(field, newField); */
        setter.setField(i, encodedField.toTypeSubstitutedField(newField));
      }
    }
  }

  private DexMethod fixupMethod(DexMethod method) {
    return appView
        .dexItemFactory()
        .createMethod(fixupType(method.holder), fixupProto(method.proto), method.name);
  }

  private DexProto fixupProto(DexProto proto) {
    DexProto result = protoFixupCache.get(proto);
    if (result == null) {
      DexType returnType = fixupType(proto.returnType);
      DexType[] arguments = fixupTypes(proto.parameters.values);
      result = appView.dexItemFactory().createProto(returnType, arguments);
      protoFixupCache.put(proto, result);
    }
    return result;
  }

  private DexType fixupType(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(appView.dexItemFactory());
      DexType fixed = fixupType(base);
      if (base == fixed) {
        return type;
      }
      return type.replaceBaseType(fixed, appView.dexItemFactory());
    }
    if (type.isClassType()) {
      while (true) {
        DexType mapped = lensBuilder.lookupType(type);
        if (mapped == type) break;
        type = mapped;
      }
    }
    return type;
  }

  private DexType[] fixupTypes(DexType[] types) {
    DexType[] result = new DexType[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = fixupType(types[i]);
    }
    return result;
  }
}
