// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.fixup.TreeFixerBase;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AnnotationFixer;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The tree fixer traverses all program classes and finds and fixes references to old classes which
 * have been remapped to new classes by the class merger. While doing so, all updated changes are
 * tracked in {@link TreeFixer#lensBuilder}.
 */
class TreeFixer extends TreeFixerBase {

  private final AppView<?> appView;
  private final HorizontallyMergedClasses mergedClasses;
  private final Mode mode;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final DexItemFactory dexItemFactory;
  private final ProfileCollectionAdditions profileCollectionAdditions;
  private final SyntheticArgumentClass syntheticArgumentClass;

  private final Map<DexProgramClass, DexType> originalSuperTypes = new IdentityHashMap<>();
  private final BiMap<DexMethodSignature, DexMethodSignature> reservedInterfaceSignatures =
      HashBiMap.create();

  public TreeFixer(
      AppView<?> appView,
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Mode mode,
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass) {
    super(appView);
    this.appView = appView;
    this.mergedClasses = mergedClasses;
    this.mode = mode;
    this.lensBuilder = lensBuilder;
    this.profileCollectionAdditions = profileCollectionAdditions;
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
    HorizontalClassMergerGraphLens lens = lensBuilder.build(appView, mergedClasses);
    if (appView.enableWholeProgramOptimizations()) {
      Collection<DexProgramClass> classes = appView.appInfo().classesWithDeterministicOrder();
      Iterables.filter(classes, DexProgramClass::isInterface).forEach(this::fixupInterfaceClass);
      classes.forEach(this::fixupAttributes);
      classes.forEach(this::fixupProgramClassSuperTypes);
      SubtypingForrestForClasses subtypingForrest =
          new SubtypingForrestForClasses(appView.withClassHierarchy());
      // TODO(b/170078037): parallelize this code segment.
      for (DexProgramClass root : subtypingForrest.getProgramRoots()) {
        subtypingForrest.traverseNodeDepthFirst(root, HashBiMap.create(), this::fixupProgramClass);
      }
      new AnnotationFixer(lens, appView.graphLens()).run(appView.appInfo().classes());
    }
    return lens;
  }

  private void fixupAttributes(DexProgramClass clazz) {
    if (clazz.hasEnclosingMethodAttribute()) {
      EnclosingMethodAttribute enclosingMethodAttribute = clazz.getEnclosingMethodAttribute();
      if (mergedClasses.hasBeenMergedIntoDifferentType(
          enclosingMethodAttribute.getEnclosingType())) {
        clazz.clearEnclosingMethodAttribute();
      } else {
        clazz.setEnclosingMethodAttribute(fixupEnclosingMethodAttribute(enclosingMethodAttribute));
      }
    }
    clazz.setInnerClasses(fixupInnerClassAttributes(clazz.getInnerClasses()));
    clazz.setNestHostAttribute(fixupNestHost(clazz.getNestHostClassAttribute()));
    clazz.setNestMemberAttributes(fixupNestMemberAttributes(clazz.getNestMembersClassAttributes()));
    clazz.setPermittedSubclassAttributes(
        fixupPermittedSubclassAttribute(clazz.getPermittedSubclassAttributes()));
  }

  @SuppressWarnings("ReferenceEquality")
  private void fixupProgramClassSuperTypes(DexProgramClass clazz) {
    DexType rewrittenSuperType = fixupType(clazz.getSuperType());
    if (rewrittenSuperType != clazz.getSuperType()) {
      originalSuperTypes.put(clazz, clazz.getSuperType());
      clazz.superType = rewrittenSuperType;
    }
    clazz.setInterfaces(fixupInterfaces(clazz, clazz.getInterfaces()));
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
        .replaceAllDirectMethods(method -> fixupDirectMethod(newMethodReferences, clazz, method));

    Set<DexField> newFieldReferences = Sets.newIdentityHashSet();
    DexEncodedField[] instanceFields = clazz.clearInstanceFields();
    DexEncodedField[] staticFields = clazz.clearStaticFields();
    clazz.setInstanceFields(fixupFields(instanceFields, newFieldReferences));
    clazz.setStaticFields(fixupFields(staticFields, newFieldReferences));

    lensBuilder.commitPendingUpdates();

    return remappedClassVirtualMethods;
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedMethod fixupVirtualInterfaceMethod(DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();

    // Don't process this method if it does not refer to a merge class type.
    boolean referencesMergeClass =
        Iterables.any(
            originalMethodReference.getProto().getBaseTypes(dexItemFactory),
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
    lensBuilder.fixupMethod(originalMethodReference, newMethodReference);
    return newMethodReference != originalMethodReference
        ? method.toTypeSubstitutedMethod(newMethodReference)
        : method;
  }

  private void fixupInterfaceClass(DexProgramClass iface) {
    Set<DexMethodSignature> newDirectMethods = new LinkedHashSet<>();
    iface
        .getMethodCollection()
        .replaceDirectMethods(method -> fixupDirectMethod(newDirectMethods, iface, method));
    iface.getMethodCollection().replaceVirtualMethods(this::fixupVirtualInterfaceMethod);

    assert !iface.hasInstanceFields();

    Set<DexField> newFieldReferences = Sets.newIdentityHashSet();
    DexEncodedField[] staticFields = iface.clearStaticFields();
    iface.setStaticFields(fixupFields(staticFields, newFieldReferences));

    lensBuilder.commitPendingUpdates();
  }

  @SuppressWarnings("ReferenceEquality")
  private DexTypeList fixupInterfaces(DexProgramClass clazz, DexTypeList interfaceTypes) {
    Set<DexType> seen = Sets.newIdentityHashSet();
    return interfaceTypes.map(
        interfaceType -> {
          DexType rewrittenInterfaceType = mapClassType(interfaceType);
          assert rewrittenInterfaceType != clazz.getType();
          return seen.add(rewrittenInterfaceType) ? rewrittenInterfaceType : null;
        });
  }

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedMethod fixupProgramMethod(
      DexMethod newMethodReference, DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();
    if (newMethodReference == originalMethodReference) {
      return method;
    }

    lensBuilder.fixupMethod(originalMethodReference, newMethodReference);

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
      Set<DexMethodSignature> newMethods, DexProgramClass clazz, DexEncodedMethod method) {
    DexMethod originalMethodReference = method.getReference();

    // Fix all type references in the method prototype.
    DexMethod newMethodReference = fixupMethodReference(originalMethodReference);

    if (newMethods.contains(newMethodReference.getSignature())) {
      // If the method collides with a direct method on the same class then rename it to a globally
      // fresh name and record the signature.

      if (method.isInstanceInitializer()) {
        // If the method is an instance initializer, then add extra nulls.
        Box<Set<DexType>> usedSyntheticArgumentClasses = new Box<>();
        newMethodReference =
            dexItemFactory.createInstanceInitializerWithFreshProto(
                newMethodReference,
                syntheticArgumentClass.getArgumentClasses(),
                tryMethod -> !newMethods.contains(tryMethod.getSignature()),
                usedSyntheticArgumentClasses::set);
        lensBuilder.addExtraParameters(
            originalMethodReference,
            ExtraUnusedNullParameter.computeExtraUnusedNullParameters(
                originalMethodReference, newMethodReference));

        // Amend the art profile collection.
        if (usedSyntheticArgumentClasses.isSet()) {
          Set<DexMethod> previousMethodReferences =
              lensBuilder.methodMap.getKeys(originalMethodReference);
          if (previousMethodReferences.isEmpty()) {
            profileCollectionAdditions.applyIfContextIsInProfile(
                originalMethodReference,
                additionsBuilder ->
                    usedSyntheticArgumentClasses.get().forEach(additionsBuilder::addRule));
          } else {
            for (DexMethod previousMethodReference : previousMethodReferences) {
              profileCollectionAdditions.applyIfContextIsInProfile(
                  previousMethodReference,
                  additionsBuilder ->
                      usedSyntheticArgumentClasses.get().forEach(additionsBuilder::addRule));
            }
          }
        }
      } else {
        newMethodReference =
            dexItemFactory.createFreshMethodNameWithoutHolder(
                newMethodReference.getName().toSourceString(),
                newMethodReference.proto,
                newMethodReference.holder,
                tryMethod ->
                    !reservedInterfaceSignatures.containsValue(tryMethod.getSignature())
                        && !newMethods.contains(tryMethod.getSignature()));
      }
    }

    boolean changed = newMethods.add(newMethodReference.getSignature());
    assert changed;

    // Convert out of DefaultInstanceInitializerCode, since this piece of code will require lens
    // code rewriting.
    if (mode.isInitial()
        && method.hasCode()
        && method.getCode().isDefaultInstanceInitializerCode()
        && mergedClasses.hasBeenMergedOrIsMergeTarget(clazz.getSuperType())) {
      DexType originalSuperType = originalSuperTypes.getOrDefault(clazz, clazz.getSuperType());
      DefaultInstanceInitializerCode.uncanonicalizeCode(
          appView, method.asProgramMethod(clazz), originalSuperType);
    }

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

  @SuppressWarnings("ReferenceEquality")
  private DexEncodedField[] fixupFields(
      DexEncodedField[] fields, Set<DexField> newFieldReferences) {
    if (fields == null || ArrayUtils.isEmpty(fields)) {
      return DexEncodedField.EMPTY_ARRAY;
    }

    DexEncodedField[] newFields = new DexEncodedField[fields.length];
    for (int i = 0; i < fields.length; i++) {
      DexEncodedField oldField = fields[i];
      DexField oldFieldReference = oldField.getReference();
      DexField newFieldReference = fixupFieldReference(oldFieldReference);

      // Rename the field if it already exists.
      if (!newFieldReferences.add(newFieldReference)) {
        DexField template = newFieldReference;
        newFieldReference =
            dexItemFactory.createFreshMember(
                tryName ->
                    Optional.of(template.withName(tryName, dexItemFactory))
                        .filter(tryMethod -> !newFieldReferences.contains(tryMethod)),
                newFieldReference.name.toSourceString());
        boolean added = newFieldReferences.add(newFieldReference);
        assert added;
      }

      if (newFieldReference != oldFieldReference) {
        lensBuilder.fixupField(oldFieldReference, newFieldReference);
        newFields[i] = oldField.toTypeSubstitutedField(appView, newFieldReference);
      } else {
        newFields[i] = oldField;
      }
    }

    return newFields;
  }

  @Override
  public DexType mapClassType(DexType type) {
    return mergedClasses.getMergeTargetOrDefault(type);
  }

  @Override
  public void recordClassChange(DexType from, DexType to) {
    // Classes are not changed but in-place updated.
    throw new Unreachable();
  }

  @Override
  public void recordFieldChange(DexField from, DexField to) {
    // Fields are manually changed in 'fixupFields' above.
    throw new Unreachable();
  }

  @Override
  public void recordMethodChange(DexMethod from, DexMethod to) {
    // Methods are manually changed in 'fixupMethods' above.
    throw new Unreachable();
  }
}
