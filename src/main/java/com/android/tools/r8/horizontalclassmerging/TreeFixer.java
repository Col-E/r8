// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass.FieldSetter;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tree fixer traverses all program classes and finds and fixes references to old classes which
 * have been remapped to new classes by the class merger. While doing so, all updated changes are
 * tracked in {@link TreeFixer#lensBuilder}.
 */
class TreeFixer {
  private final Map<DexProto, DexProto> protoFixupCache = new IdentityHashMap<>();
  private final HorizontallyMergedClasses mergedClasses;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder;
  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final BiMap<DexMethod, DexMethod> movedMethods = HashBiMap.create();
  private final SyntheticArgumentClass syntheticArgumentClass;
  private final BiMap<DexMethodSignature, DexMethodSignature> reservedInterfaceSignatures =
      HashBiMap.create();

  public TreeFixer(
      AppView<AppInfoWithLiveness> appView,
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      SyntheticArgumentClass syntheticArgumentClass) {
    this.appView = appView;
    this.mergedClasses = mergedClasses;
    this.lensBuilder = lensBuilder;
    this.fieldAccessChangesBuilder = fieldAccessChangesBuilder;
    this.syntheticArgumentClass = syntheticArgumentClass;
    this.dexItemFactory = appView.dexItemFactory();
  }

  /**
   * Lets assume the following initial classes, where the class B should be merged into A: <code>
   *   class A {
   *     public A(A a) { ... }
   *     public A(A a, int v) { ... }
   *     public A(B b) { ... }
   *     public A(B b, int v) { ... }
   *   }
   *
   *   class B {
   *     public B(A a) { ... }
   *     public B(B b) { ... }
   *   }
   * </code>
   *
   * <p>The {@link ClassMerger} merges the constructors {@code A.<init>(B)} and {@code B.<init>(B)}
   * into the constructor {@code A.<init>(B, int)} to prevent any collisions when merging the
   * constructor into A. The extra integer argument determines which class' constructor is called.
   * The SynthArg is used to prevent a collision with the existing {@code A.<init>(B, int)}
   * constructor. All constructors {@code A.<init>(A, ...)} generate a constructor {@code
   * A.<init>(A, int, SynthClass)} but are otherwise ignored. During ClassMerging the constructor
   * produces the following mappings in the graph lens builder:
   *
   * <ul>
   *   <li>{@code B.<init>(B) <--> A.<init>(B, int, SynthArg)}
   *   <li>{@code A.<init>(B) <--> A.<init>(B, int, SynthArg)} (This mapping is representative)
   *   <li>{@code A.constructor$B(B) ---> A.constructor$B(B)}
   *   <li>{@code B.<init>(B) <--- A.constructor$B(B)}
   * </ul>
   *
   * <p>Note: The identity mapping is needed so that the method is remapped in the forward direction
   * if there are changes in the tree fixer. Otherwise, methods are only remapped in directions they
   * are already mapped in.
   *
   * <p>During the fixup, all type references to B are changed into A. This causes a collision
   * between {@code A.<init>(A, int, SynthClass)} and {@code A.<init>(B, int, SynthClass)}. This
   * collision should be fixed by adding an extra argument to {@code A.<init>(B, int, SynthClass)}.
   * The TreeFixer generates the following mapping of renamed methods:
   *
   * <ul>
   *   <li>{@code A.<init>(B, int, SynthArg) <--> A.<init>(A, int, SynthArg, ExtraArg)}
   *   <li>{@code A.constructor$B(B) <--> A.constructor$B(A)}
   * </ul>
   *
   * <p>This rewrites the previous method mappings to:
   *
   * <ul>
   *   <li>{@code B.<init>(B) <--- A.constructor$B(A)}
   *   <li>{@code A.constructor$B(B) ---> A.constructor$B(A)}
   *   <li>{@code B.<init>(B) <--> A.<init>(A, int, SynthArg, ExtraArg)}
   *   <li>{@code A.<init>(B) <--> A.<init>(A, int, SynthArg, ExtraArg)} (including represents)
   * </ul>
   */
  public HorizontalClassMergerGraphLens fixupTypeReferences() {
    List<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
    Iterables.filter(classes, DexProgramClass::isInterface).forEach(this::fixupInterfaceClass);

    classes.forEach(this::fixupProgramClassSuperType);
    SubtypingForrestForClasses subtypingForrest = new SubtypingForrestForClasses(appView);
    // TODO(b/170078037): parallelize this code segment.
    for (DexProgramClass root : subtypingForrest.getProgramRoots()) {
      subtypingForrest.traverseNodeDepthFirst(root, HashBiMap.create(), this::fixupProgramClass);
    }

    lensBuilder.remapMethods(movedMethods);

    HorizontalClassMergerGraphLens lens = lensBuilder.build(appView, mergedClasses);
    fieldAccessChangesBuilder.build(this::fixupMethodReference).modify(appView);
    new AnnotationFixer(lens).run(appView.appInfo().classes());
    return lens;
  }

  private void fixupProgramClassSuperType(DexProgramClass clazz) {
    clazz.superType = fixupType(clazz.superType);
  }

  private BiMap<DexMethodSignature, DexMethodSignature> fixupProgramClass(
      DexProgramClass clazz, BiMap<DexMethodSignature, DexMethodSignature> remappedVirtualMethods) {
    assert !clazz.isInterface();

    // TODO(b/169395592): ensure merged classes have been removed using:
    //   assert !mergedClasses.hasBeenMergedIntoDifferentType(clazz.type);

    BiMap<DexMethodSignature, DexMethodSignature> remappedClassVirtualMethods =
        HashBiMap.create(remappedVirtualMethods);

    Set<DexMethodSignature> newMethodReferences = Sets.newHashSet();
    clazz
        .getMethodCollection()
        .replaceAllVirtualMethods(
            method -> fixupVirtualMethod(remappedClassVirtualMethods, newMethodReferences, method));
    clazz
        .getMethodCollection()
        .replaceAllDirectMethods(method -> fixupDirectMethod(newMethodReferences, method));

    fixupFields(clazz.staticFields(), clazz::setStaticField);
    fixupFields(clazz.instanceFields(), clazz::setInstanceField);

    return remappedClassVirtualMethods;
  }

  private DexEncodedMethod fixupVirtualInterfaceMethod(DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();

    // Don't process this method if it does not refer to a merge class type.
    boolean referencesMergeClass =
        Iterables.any(
            originalMethodReference.proto.getBaseTypes(dexItemFactory),
            mergedClasses::hasBeenMergedOrIsMergeTarget);
    if (!referencesMergeClass) {
      return method;
    }

    DexMethodSignature originalMethodSignature = originalMethodReference.getSignature();
    DexMethodSignature newMethodSignature =
        reservedInterfaceSignatures.get(originalMethodSignature);

    if (newMethodSignature == null) {
      newMethodSignature = fixupMethodReference(originalMethodReference).getSignature();

      // If the signature is already reserved by another interface, find a fresh one.
      if (reservedInterfaceSignatures.containsValue(newMethodSignature)) {
        DexString name =
            dexItemFactory.createGloballyFreshMemberString(
                originalMethodReference.getName().toSourceString());
        newMethodSignature = newMethodSignature.withName(name);
      }

      assert !reservedInterfaceSignatures.containsValue(newMethodSignature);
      reservedInterfaceSignatures.put(originalMethodSignature, newMethodSignature);
    }

    DexMethod newMethodReference =
        newMethodSignature.withHolder(originalMethodReference, dexItemFactory);
    movedMethods.put(originalMethodReference, newMethodReference);

    return method.toTypeSubstitutedMethod(newMethodReference);
  }

  private void fixupInterfaceClass(DexProgramClass iface) {
    Set<DexMethodSignature> newDirectMethods = new LinkedHashSet<>();

    assert iface.superType == dexItemFactory.objectType;
    iface.superType = mergedClasses.getMergeTargetOrDefault(iface.superType);

    iface
        .getMethodCollection()
        .replaceDirectMethods(method -> fixupDirectMethod(newDirectMethods, method));
    iface.getMethodCollection().replaceVirtualMethods(this::fixupVirtualInterfaceMethod);
    fixupFields(iface.staticFields(), iface::setStaticField);
    fixupFields(iface.instanceFields(), iface::setInstanceField);
  }

  private DexEncodedMethod fixupProgramMethod(
      DexMethod newMethodReference, DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();

    if (newMethodReference == originalMethodReference) {
      return method;
    }

    movedMethods.put(originalMethodReference, newMethodReference);

    DexEncodedMethod newMethod = method.toTypeSubstitutedMethod(newMethodReference);
    if (newMethod.isNonPrivateVirtualMethod()) {
      // Since we changed the return type or one of the parameters, this method cannot be a
      // classpath or library method override, since we only class merge program classes.
      assert !method.isLibraryMethodOverride().isTrue();
      newMethod.setLibraryMethodOverride(OptionalBool.FALSE);
    }

    return newMethod;
  }

  private DexEncodedMethod fixupDirectMethod(
      Set<DexMethodSignature> newMethods, DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();

    // Fix all type references in the method prototype.
    DexMethod newMethodReference = fixupMethodReference(originalMethodReference);

    if (newMethods.contains(newMethodReference.getSignature())) {
      // If the method collides with a direct method on the same class then rename it to a globally
      // fresh name and record the signature.

      if (method.isInstanceInitializer()) {
        // If the method is an instance initializer, then add extra nulls.
        newMethodReference =
            dexItemFactory.createInstanceInitializerWithFreshProto(
                newMethodReference,
                syntheticArgumentClass.getArgumentClass(),
                tryMethod -> !newMethods.contains(tryMethod.getSignature()));
        int extraNulls = newMethodReference.getArity() - originalMethodReference.getArity();
        lensBuilder.addExtraParameters(
            originalMethodReference,
            Collections.nCopies(extraNulls, new ExtraUnusedNullParameter()));
      } else {
        newMethodReference =
            dexItemFactory.createFreshMethodName(
                newMethodReference.getName().toSourceString(),
                null,
                newMethodReference.proto,
                newMethodReference.holder,
                tryMethod ->
                    !reservedInterfaceSignatures.containsValue(tryMethod.getSignature())
                        && !newMethods.contains(tryMethod.getSignature()));
      }
    }

    boolean changed = newMethods.add(newMethodReference.getSignature());
    assert changed;

    return fixupProgramMethod(newMethodReference, method);
  }

  private DexMethodSignature lookupReservedVirtualName(
      DexMethod originalMethodReference,
      BiMap<DexMethodSignature, DexMethodSignature> renamedClassVirtualMethods) {
    DexMethodSignature originalSignature = originalMethodReference.getSignature();

    // Determine if the original method has been rewritten by a parent class
    DexMethodSignature renamedVirtualName = renamedClassVirtualMethods.get(originalSignature);

    if (renamedVirtualName == null) {
      // Determine if there is a signature mapping.
      DexMethodSignature mappedInterfaceSignature =
          reservedInterfaceSignatures.get(originalSignature);
      if (mappedInterfaceSignature != null) {
        renamedVirtualName = mappedInterfaceSignature;
      }
    } else {
      assert !reservedInterfaceSignatures.containsKey(originalSignature);
    }

    return renamedVirtualName;
  }

  private DexEncodedMethod fixupVirtualMethod(
      BiMap<DexMethodSignature, DexMethodSignature> renamedClassVirtualMethods,
      Set<DexMethodSignature> newMethods,
      DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();
    DexMethodSignature originalSignature = originalMethodReference.getSignature();

    DexMethodSignature renamedVirtualName =
        lookupReservedVirtualName(originalMethodReference, renamedClassVirtualMethods);

    // Fix all type references in the method prototype.
    DexMethodSignature newSignature = fixupMethodSignature(originalSignature);

    if (renamedVirtualName != null) {
      // If the method was renamed in a parent, rename it in the child.
      newSignature = renamedVirtualName;

      assert !newMethods.contains(newSignature);
    } else if (reservedInterfaceSignatures.containsValue(newSignature)
        || newMethods.contains(newSignature)
        || renamedClassVirtualMethods.containsValue(newSignature)) {
      // If the method potentially collides with an interface method or with another virtual method
      // rename it to a globally fresh name and record the name.

      newSignature =
          dexItemFactory.createFreshMethodSignatureName(
              originalMethodReference.getName().toSourceString(),
              null,
              newSignature.getProto(),
              trySignature ->
                  !reservedInterfaceSignatures.containsValue(trySignature)
                      && !newMethods.contains(trySignature)
                      && !renamedClassVirtualMethods.containsValue(trySignature));

      // Record signature renaming so that subclasses perform the identical rename.
      renamedClassVirtualMethods.put(originalSignature, newSignature);
    } else {
      // There was no reserved name and the new signature is available.

      if (Iterables.any(
          newSignature.getProto().getParameterBaseTypes(dexItemFactory),
          mergedClasses::isMergeTarget)) {
        // If any of the parameter types have been merged, record the signature mapping.
        renamedClassVirtualMethods.put(originalSignature, newSignature);
      }
    }

    boolean changed = newMethods.add(newSignature);
    assert changed;

    DexMethod newMethodReference =
        newSignature.withHolder(fixupType(originalMethodReference.holder), dexItemFactory);
    return fixupProgramMethod(newMethodReference, method);
  }

  private void fixupFields(List<DexEncodedField> fields, FieldSetter setter) {
    if (fields == null) {
      return;
    }
    Set<DexField> existingFields = Sets.newIdentityHashSet();

    for (int i = 0; i < fields.size(); i++) {
      DexEncodedField encodedField = fields.get(i);
      DexField field = encodedField.field;
      DexField newField = fixupFieldReference(field);

      // Rename the field if it already exists.
      if (!existingFields.add(newField)) {
        DexField template = newField;
        newField =
            dexItemFactory.createFreshMember(
                tryName ->
                    Optional.of(template.withName(tryName, dexItemFactory))
                        .filter(tryMethod -> !existingFields.contains(tryMethod)),
                newField.name.toSourceString());
        boolean added = existingFields.add(newField);
        assert added;
      }

      if (newField != encodedField.field) {
        lensBuilder.mapField(field, newField);
        setter.setField(i, encodedField.toTypeSubstitutedField(newField));
      }
    }
  }

  public DexField fixupFieldReference(DexField field) {
    DexType newType = fixupType(field.type);
    DexType newHolder = fixupType(field.holder);
    return appView.dexItemFactory().createField(newHolder, newType, field.name);
  }

  private DexMethod fixupMethodReference(DexMethod method) {
    return appView
        .dexItemFactory()
        .createMethod(fixupType(method.holder), fixupProto(method.proto), method.name);
  }

  private DexMethodSignature fixupMethodSignature(DexMethodSignature signature) {
    return signature.withProto(fixupProto(signature.getProto()));
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
      type = mergedClasses.getMergeTargetOrDefault(type);
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
