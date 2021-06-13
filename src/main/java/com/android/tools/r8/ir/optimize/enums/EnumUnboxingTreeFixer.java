// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

class EnumUnboxingTreeFixer {

  private final Map<DexType, List<DexEncodedMethod>> unboxedEnumsMethods = new IdentityHashMap<>();
  private final EnumUnboxingLens.Builder lensBuilder = EnumUnboxingLens.enumUnboxingLensBuilder();
  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final Set<DexType> enumsToUnbox;
  private final EnumUnboxingUtilityClasses utilityClasses;
  private final EnumUnboxingRewriter enumUnboxerRewriter;

  EnumUnboxingTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      Set<DexType> enumsToUnbox,
      EnumUnboxingUtilityClasses utilityClasses,
      EnumUnboxingRewriter enumUnboxerRewriter) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enumsToUnbox = enumsToUnbox;
    this.utilityClasses = utilityClasses;
    this.enumUnboxerRewriter = enumUnboxerRewriter;
  }

  EnumUnboxingLens fixupTypeReferences() {
    assert enumUnboxerRewriter != null;
    // Fix all methods and fields using enums to unbox.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (enumsToUnbox.contains(clazz.getType())) {
        // Clear the initializers and move the static methods to the new location.
        Set<DexEncodedMethod> methodsToRemove = Sets.newIdentityHashSet();
        clazz
            .methods()
            .forEach(
                m -> {
                  if (m.isInitializer()) {
                    clearEnumToUnboxMethod(m);
                  } else {
                    DexType newHolder = utilityClasses.getLocalUtilityClass(clazz).getType();
                    List<DexEncodedMethod> movedMethods =
                        unboxedEnumsMethods.computeIfAbsent(newHolder, k -> new ArrayList<>());
                    movedMethods.add(fixupEncodedMethodToUtility(m, newHolder));
                    methodsToRemove.add(m);
                  }
                });
        clazz.getMethodCollection().removeMethods(methodsToRemove);
      } else {
        clazz
            .getMethodCollection()
            .replaceMethods(method -> this.fixupEncodedMethod(clazz, method));
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
          movedMethods.sort(Comparator.comparing(DexEncodedMember::getReference));
          newHolderClass.addDirectMethods(movedMethods);
        });
    return lensBuilder.build(appView);
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
    DexMethod method = encodedMethod.getReference();
    DexString newMethodName =
        factory.createString(
            enumUnboxerRewriter.compatibleName(method.holder)
                + "$"
                + (encodedMethod.isStatic() ? "s" : "v")
                + "$"
                + method.name.toString());
    DexProto proto = encodedMethod.isStatic() ? method.proto : factory.prependHolderToProto(method);
    DexMethod newMethod = factory.createMethod(newHolder, fixupProto(proto), newMethodName);
    assert appView.definitionFor(encodedMethod.getHolderType()).lookupMethod(newMethod) == null;
    lensBuilder.move(method, newMethod, encodedMethod.isStatic(), true);
    encodedMethod.accessFlags.promoteToPublic();
    encodedMethod.accessFlags.promoteToStatic();
    encodedMethod.clearAnnotations();
    encodedMethod.clearParameterAnnotations();
    return encodedMethod.toTypeSubstitutedMethod(
        newMethod, builder -> builder.setCompilationState(encodedMethod.getCompilationState()));
  }

  private DexEncodedMethod fixupEncodedMethod(DexProgramClass holder, DexEncodedMethod method) {
    DexProto oldProto = method.getProto();
    DexProto newProto = fixupProto(oldProto);
    if (newProto == method.getProto()) {
      return method;
    }
    assert !method.isClassInitializer();
    assert !method.isLibraryMethodOverride().isTrue()
        : "Enum unboxing is changing the signature of a library override in a non unboxed class.";
    // We add the $enumunboxing$ suffix to make sure we do not create a library override.
    String newMethodName =
        method.getName().toString() + (method.isNonPrivateVirtualMethod() ? "$enumunboxing$" : "");
    DexMethod newMethod = factory.createMethod(method.getHolderType(), newProto, newMethodName);
    newMethod = ensureUniqueMethod(method, newMethod);
    int numberOfExtraNullParameters = newMethod.getArity() - method.getReference().getArity();
    boolean isStatic = method.isStatic();
    lensBuilder.move(
        method.getReference(), newMethod, isStatic, isStatic, numberOfExtraNullParameters);
    return method.toTypeSubstitutedMethod(
        newMethod,
        builder ->
            builder
                .setCompilationState(method.getCompilationState())
                .setIsLibraryMethodOverrideIf(
                    method.isNonPrivateVirtualMethod(), OptionalBool.FALSE)
                .setSimpleInliningConstraint(
                    holder, getRewrittenSimpleInliningConstraint(method, oldProto, newProto)));
  }

  private SimpleInliningConstraint getRewrittenSimpleInliningConstraint(
      DexEncodedMethod method, DexProto oldProto, DexProto newProto) {
    IntList unboxedArgumentIndices = new IntArrayList();
    int offset = BooleanUtils.intValue(method.isInstance());
    for (int i = 0; i < method.getReference().getArity(); i++) {
      if (oldProto.getParameter(i).isReferenceType()
          && newProto.getParameter(i).isPrimitiveType()) {
        unboxedArgumentIndices.add(i + offset);
      }
    }
    return method
        .getOptimizationInfo()
        .getSimpleInliningConstraint()
        .rewrittenWithUnboxedArguments(unboxedArgumentIndices);
  }

  private DexMethod ensureUniqueMethod(DexEncodedMethod encodedMethod, DexMethod newMethod) {
    DexClass holder = appView.definitionFor(encodedMethod.getHolderType());
    assert holder != null;
    if (newMethod.isInstanceInitializer(appView.dexItemFactory())) {
      newMethod =
          factory.createInstanceInitializerWithFreshProto(
              newMethod,
              utilityClasses.getSharedUtilityClass().getType(),
              tryMethod -> holder.lookupMethod(tryMethod) == null);
    } else {
      int index = 0;
      while (holder.lookupMethod(newMethod) != null) {
        newMethod =
            newMethod.withName(
                encodedMethod.getName().toString() + "$enumunboxing$" + index++,
                appView.dexItemFactory());
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
      DexField field = encodedField.getReference();
      DexType newType = fixupType(field.type);
      if (newType != field.type) {
        DexField newField = field.withType(newType, factory);
        lensBuilder.move(field, newField);
        DexEncodedField newEncodedField =
            encodedField.toTypeSubstitutedField(
                newField,
                builder ->
                    builder.setAbstractValue(
                        encodedField.getOptimizationInfo().getAbstractValue(), appView));
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
