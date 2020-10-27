// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class EnumUnboxingTreeFixer {

  private final Map<DexType, List<DexEncodedMethod>> unboxedEnumsMethods = new IdentityHashMap<>();
  private final EnumUnboxingLens.Builder lensBuilder = EnumUnboxingLens.enumUnboxingLensBuilder();
  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final Set<DexType> enumsToUnbox;
  private final UnboxedEnumMemberRelocator relocator;
  private final EnumUnboxingRewriter enumUnboxerRewriter;

  EnumUnboxingTreeFixer(
      AppView<?> appView,
      Set<DexType> enumsToUnbox,
      UnboxedEnumMemberRelocator relocator,
      EnumUnboxingRewriter enumUnboxerRewriter) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enumsToUnbox = enumsToUnbox;
    this.relocator = relocator;
    this.enumUnboxerRewriter = enumUnboxerRewriter;
  }

  GraphLens.NestedGraphLens fixupTypeReferences() {
    assert enumUnboxerRewriter != null;
    // Fix all methods and fields using enums to unbox.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (enumsToUnbox.contains(clazz.type)) {
        // Clear the initializers and move the static methods to the new location.
        Set<DexEncodedMethod> methodsToRemove = Sets.newIdentityHashSet();
        clazz
            .methods()
            .forEach(
                m -> {
                  if (m.isInitializer()) {
                    clearEnumToUnboxMethod(m);
                  } else {
                    DexType newHolder = relocator.getNewMemberLocationFor(clazz.type);
                    List<DexEncodedMethod> movedMethods =
                        unboxedEnumsMethods.computeIfAbsent(newHolder, k -> new ArrayList<>());
                    movedMethods.add(fixupEncodedMethodToUtility(m, newHolder));
                    methodsToRemove.add(m);
                  }
                });
        clazz.getMethodCollection().removeMethods(methodsToRemove);
      } else {
        clazz.getMethodCollection().replaceMethods(this::fixupEncodedMethod);
        fixupFields(clazz.staticFields(), clazz::setStaticField);
        fixupFields(clazz.instanceFields(), clazz::setInstanceField);
      }
    }
    for (DexType toUnbox : enumsToUnbox) {
      lensBuilder.map(toUnbox, factory.intType);
    }
    unboxedEnumsMethods.forEach(
        (newHolderType, movedMethods) -> {
          DexProgramClass newHolderClass = appView.definitionFor(newHolderType).asProgramClass();
          newHolderClass.addDirectMethods(movedMethods);
        });
    return lensBuilder.build(factory, appView.graphLens(), enumsToUnbox);
  }

  private void clearEnumToUnboxMethod(DexEncodedMethod enumMethod) {
    // The compiler may have references to the enum methods, but such methods will be removed
    // and they cannot be reprocessed since their rewriting through the lensCodeRewriter/
    // enumUnboxerRewriter will generate invalid code.
    // To work around this problem we clear such methods, i.e., we replace the code object by
    // an empty throwing code object, so reprocessing won't take time and will be valid.
    enumMethod.setCode(enumMethod.buildEmptyThrowingCode(appView.options()), appView);
  }

  private DexEncodedMethod fixupEncodedMethodToUtility(
      DexEncodedMethod encodedMethod, DexType newHolder) {
    DexMethod method = encodedMethod.method;
    DexString newMethodName =
        factory.createString(
            enumUnboxerRewriter.compatibleName(method.holder)
                + "$"
                + (encodedMethod.isStatic() ? "s" : "v")
                + "$"
                + method.name.toString());
    DexProto proto = encodedMethod.isStatic() ? method.proto : factory.prependHolderToProto(method);
    DexMethod newMethod = factory.createMethod(newHolder, fixupProto(proto), newMethodName);
    assert appView.definitionFor(encodedMethod.holder()).lookupMethod(newMethod) == null;
    lensBuilder.move(method, newMethod, encodedMethod.isStatic(), true);
    encodedMethod.accessFlags.promoteToPublic();
    encodedMethod.accessFlags.promoteToStatic();
    encodedMethod.clearAnnotations();
    encodedMethod.clearParameterAnnotations();
    return encodedMethod.toTypeSubstitutedMethod(newMethod);
  }

  private DexEncodedMethod fixupEncodedMethod(DexEncodedMethod method) {
    DexProto newProto = fixupProto(method.proto());
    if (newProto == method.proto()) {
      return method;
    }
    assert !method.isClassInitializer();
    // We add the $enumunboxing$ suffix to make sure we do not create a library override.
    String newMethodName =
        method.getName().toString() + (method.isNonPrivateVirtualMethod() ? "$enumunboxing$" : "");
    DexMethod newMethod = factory.createMethod(method.getHolderType(), newProto, newMethodName);
    newMethod = ensureUniqueMethod(method, newMethod);
    int numberOfExtraNullParameters = newMethod.getArity() - method.method.getArity();
    boolean isStatic = method.isStatic();
    lensBuilder.move(method.method, newMethod, isStatic, isStatic, numberOfExtraNullParameters);
    DexEncodedMethod newEncodedMethod =
        method.toTypeSubstitutedMethod(
            newMethod,
            builder ->
                builder
                    .setCompilationState(method.getCompilationState())
                    .setIsLibraryMethodOverrideIf(
                        method.isNonPrivateVirtualMethod(), OptionalBool.FALSE));
    assert !method.isLibraryMethodOverride().isTrue()
        : "Enum unboxing is changing the signature of a library override in a non unboxed class.";
    return newEncodedMethod;
  }

  private DexMethod ensureUniqueMethod(DexEncodedMethod encodedMethod, DexMethod newMethod) {
    DexClass holder = appView.definitionFor(encodedMethod.holder());
    assert holder != null;
    if (newMethod.isInstanceInitializer(appView.dexItemFactory())) {
      newMethod =
          factory.createInstanceInitializerWithFreshProto(
              newMethod,
              relocator.getDefaultEnumUnboxingUtility(),
              tryMethod -> holder.lookupMethod(tryMethod) == null);
    } else {
      int index = 0;
      while (holder.lookupMethod(newMethod) != null) {
        newMethod =
            factory.createMethod(
                newMethod.holder,
                newMethod.proto,
                encodedMethod.getName().toString() + "$enumunboxing$" + index++);
      }
    }
    return newMethod;
  }

  private void fixupFields(List<DexEncodedField> fields, DexClass.FieldSetter setter) {
    if (fields == null) {
      return;
    }
    for (int i = 0; i < fields.size(); i++) {
      DexEncodedField encodedField = fields.get(i);
      DexField field = encodedField.field;
      DexType newType = fixupType(field.type);
      if (newType != field.type) {
        DexField newField = factory.createField(field.holder, newType, field.name);
        lensBuilder.move(field, newField);
        DexEncodedField newEncodedField = encodedField.toTypeSubstitutedField(newField);
        setter.setField(i, newEncodedField);
        if (encodedField.isStatic() && encodedField.hasExplicitStaticValue()) {
          assert encodedField.getStaticValue() == DexValue.DexValueNull.NULL;
          newEncodedField.setStaticValue(DexValue.DexValueInt.DEFAULT);
          // TODO(b/150593449): Support conversion from DexValueEnum to DexValueInt.
        }
      }
    }
  }

  private DexProto fixupProto(DexProto proto) {
    DexType returnType = fixupType(proto.returnType);
    DexType[] arguments = fixupTypes(proto.parameters.values);
    return factory.createProto(returnType, arguments);
  }

  private DexType fixupType(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(factory);
      DexType fixed = fixupType(base);
      if (base == fixed) {
        return type;
      }
      return type.replaceBaseType(fixed, factory);
    }
    if (type.isClassType() && enumsToUnbox.contains(type)) {
      DexType intType = factory.intType;
      lensBuilder.map(type, intType);
      return intType;
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
