// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.VerticalClassMerger.IllegalAccessDetector;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodJavaSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.SingletonEquivalence;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multiset.Entry;
import com.google.common.collect.Streams;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This optimization merges all classes that only have static members and private virtual methods.
 *
 * <p>If a merge candidate does not access any package-private or protected members, then it is
 * merged into a global representative. Otherwise it is merged into a representative for its
 * package. If no such representatives exist, then the merge candidate is promoted to be the
 * representative.
 *
 * <p>Note that, when merging a merge candidate X into Y, this optimization merely moves the members
 * of X into Y -- it does not change all occurrences of X in the program into Y. This makes the
 * optimization more applicable, because it would otherwise not be possible to merge two classes if
 * they inherited from, say, X' and Y' (since multiple inheritance is not allowed).
 */
public class StaticClassMerger {

  private static final String GLOBAL = "<global>";

  // There are 52 characters in [a-zA-Z], so with a capacity just below 52 the minifier should be
  // able to find single-character names for all members, but around 30 appears to work better in
  // practice.
  private static final int HEURISTIC_FOR_CAPACITY_OF_REPRESENTATIVES = 30;

  private class Representative {

    private final DexProgramClass clazz;

    // Put all members of this class into buckets, where each bucket represent members that have a
    // conflicting signature, and therefore must be given distinct names.
    private final HashMultiset<Wrapper<DexField>> fieldBuckets = HashMultiset.create();
    private final HashMultiset<Wrapper<DexMethod>> methodBuckets = HashMultiset.create();

    public Representative(DexProgramClass clazz) {
      this.clazz = clazz;
      include(clazz);
    }

    // Includes the members of `clazz` in `fieldBuckets` and `methodBuckets`.
    public void include(DexProgramClass clazz) {
      for (DexEncodedField field : clazz.fields()) {
        Wrapper<DexField> wrapper = fieldEquivalence.wrap(field.field);
        fieldBuckets.add(wrapper);
      }
      for (DexEncodedMethod method : clazz.methods()) {
        Wrapper<DexMethod> wrapper = methodEquivalence.wrap(method.method);
        methodBuckets.add(wrapper);
      }
    }

    // Returns true if this representative should no longer be used. The current heuristic is to
    // stop using a representative when the number of members with the same signature (ignoring the
    // name) exceeds a given threshold. This way it is unlikely that we will not be able to find a
    // single-character name for all members.
    public boolean isFull() {
      int numberOfNamesNeeded = 1;
      for (Entry<Wrapper<DexField>> entry : fieldBuckets.entrySet()) {
        numberOfNamesNeeded = Math.max(entry.getCount(), numberOfNamesNeeded);
      }
      for (Entry<Wrapper<DexMethod>> entry : methodBuckets.entrySet()) {
        numberOfNamesNeeded = Math.max(entry.getCount(), numberOfNamesNeeded);
      }
      return numberOfNamesNeeded > HEURISTIC_FOR_CAPACITY_OF_REPRESENTATIVES;
    }
  }

  private final AppView<? extends AppInfoWithLiveness> appView;

  /** The equivalence that should be used for the member buckets in {@link Representative}. */
  private final Equivalence<DexField> fieldEquivalence;
  private final Equivalence<DexMethod> methodEquivalence;

  private final Map<String, Representative> representatives = new HashMap<>();

  private final BiMap<DexField, DexField> fieldMapping = HashBiMap.create();
  private final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();

  private int numberOfMergedClasses = 0;

  public StaticClassMerger(
      AppView<? extends AppInfoWithLiveness> appView, InternalOptions options) {
    this.appView = appView;
    if (options
        .getProguardConfiguration().isOverloadAggressivelyWithoutUseUniqueClassMemberNames()) {
      fieldEquivalence = FieldSignatureEquivalence.getEquivalenceIgnoreName();
      methodEquivalence = MethodSignatureEquivalence.getEquivalenceIgnoreName();
    } else {
      fieldEquivalence = new SingletonEquivalence<>();
      methodEquivalence = MethodJavaSignatureEquivalence.getEquivalenceIgnoreName();
    }
  }

  public GraphLense run() {
    for (DexProgramClass clazz : appView.appInfo().app.classesWithDeterministicOrder()) {
      if (satisfiesMergeCriteria(clazz)) {
        merge(clazz);
      }
    }
    if (Log.ENABLED) {
      Log.info(
          getClass(),
          "Merged %s classes with %s members.",
          numberOfMergedClasses,
          fieldMapping.size() + methodMapping.size());
    }
    return buildGraphLense();
  }

  private GraphLense buildGraphLense() {
    if (!fieldMapping.isEmpty() || !methodMapping.isEmpty()) {
      BiMap<DexField, DexField> originalFieldSignatures = fieldMapping.inverse();
      BiMap<DexMethod, DexMethod> originalMethodSignatures = methodMapping.inverse();
      return new NestedGraphLense(
          ImmutableMap.of(),
          methodMapping,
          fieldMapping,
          originalFieldSignatures,
          originalMethodSignatures,
          appView.graphLense(),
          appView.dexItemFactory());
    }
    return appView.graphLense();
  }

  private boolean satisfiesMergeCriteria(DexProgramClass clazz) {
    if (appView.appInfo().neverMerge.contains(clazz.type)) {
      return false;
    }
    if (clazz.staticFields().length + clazz.directMethods().length + clazz.virtualMethods().length
        == 0) {
      return false;
    }
    if (clazz.instanceFields().length > 0) {
      return false;
    }
    if (Arrays.stream(clazz.staticFields())
        .anyMatch(field -> appView.appInfo().isPinned(field.field))) {
      return false;
    }
    if (Arrays.stream(clazz.directMethods()).anyMatch(DexEncodedMethod::isInitializer)) {
      return false;
    }
    if (!Arrays.stream(clazz.virtualMethods()).allMatch(DexEncodedMethod::isPrivateMethod)) {
      return false;
    }
    if (Streams.stream(clazz.methods())
        .anyMatch(
            method ->
                method.accessFlags.isNative()
                    || appView.appInfo().isPinned(method.method)
                    // TODO(christofferqa): Remove the invariant that the graph lense should not
                    // modify any methods from the sets alwaysInline and noSideEffects.
                    || appView.appInfo().alwaysInline.contains(method.method)
                    || appView.appInfo().noSideEffects.keySet().contains(method))) {
      return false;
    }
    if (clazz.classInitializationMayHaveSideEffects(appView.appInfo())) {
      // This could have a negative impact on inlining.
      //
      // See {@link com.android.tools.r8.ir.optimize.DefaultInliningOracle#canInlineStaticInvoke}
      // for further details.
      //
      // Note that this will be true for all classes that inherit from or implement a library class.
      return false;
    }
    return true;
  }

  private boolean isValidRepresentative(DexProgramClass clazz) {
    // Disallow interfaces from being representatives, since interface methods require desugaring.
    return !clazz.isInterface();
  }

  private boolean merge(DexProgramClass clazz) {
    assert satisfiesMergeCriteria(clazz);

    String pkg = clazz.type.getPackageDescriptor();
    return mayMergeAcrossPackageBoundaries(clazz)
        ? mergeGlobally(clazz, pkg)
        : mergeInsidePackage(clazz, pkg);
  }

  private boolean mergeGlobally(DexProgramClass clazz, String pkg) {
    Representative globalRepresentative = representatives.get(GLOBAL);
    if (globalRepresentative == null) {
      if (isValidRepresentative(clazz)) {
        // Make the current class the global representative.
        setRepresentative(GLOBAL, getOrCreateRepresentative(clazz, pkg));
      } else {
        clearRepresentative(GLOBAL);
      }

      // Do not attempt to merge this class inside its own package, because that could lead to
      // an increase in the global representative, which is not desirable.
      return false;
    } else {
      // Check if we can merge the current class into the current global representative.
      globalRepresentative.include(clazz);

      if (globalRepresentative.isFull()) {
        if (isValidRepresentative(clazz)) {
          // Make the current class the global representative instead.
          setRepresentative(GLOBAL, getOrCreateRepresentative(clazz, pkg));
        } else {
          clearRepresentative(GLOBAL);
        }

        // Do not attempt to merge this class inside its own package, because that could lead to
        // an increase in the global representative, which is not desirable.
        return false;
      } else {
        // Merge this class into the global representative.
        moveMembersFromSourceToTarget(clazz, globalRepresentative.clazz);
        return true;
      }
    }
  }

  private boolean mergeInsidePackage(DexProgramClass clazz, String pkg) {
    Representative packageRepresentative = representatives.get(pkg);
    if (packageRepresentative != null) {
      if (isValidRepresentative(clazz)
          && clazz.accessFlags.isMoreVisibleThan(packageRepresentative.clazz.accessFlags)) {
        // Use `clazz` as a representative for this package instead.
        Representative newRepresentative = getOrCreateRepresentative(clazz, pkg);
        newRepresentative.include(packageRepresentative.clazz);

        if (!newRepresentative.isFull()) {
          setRepresentative(pkg, newRepresentative);
          moveMembersFromSourceToTarget(packageRepresentative.clazz, clazz);
          return true;
        }

        // We are not allowed to merge members into a class that is less visible.
        return false;
      }

      // Merge current class into the representative of this package if it has room.
      packageRepresentative.include(clazz);

      // If there is room, then merge, otherwise fall-through to update the representative of this
      // package.
      if (!packageRepresentative.isFull()) {
        moveMembersFromSourceToTarget(clazz, packageRepresentative.clazz);
        return true;
      }
    }

    // We were unable to use the current representative for this package (if any).
    if (isValidRepresentative(clazz)) {
      setRepresentative(pkg, getOrCreateRepresentative(clazz, pkg));
    }
    return false;
  }

  private Representative getOrCreateRepresentative(DexProgramClass clazz, String pkg) {
    Representative globalRepresentative = representatives.get(GLOBAL);
    if (globalRepresentative != null && globalRepresentative.clazz == clazz) {
      return globalRepresentative;
    }
    Representative packageRepresentative = representatives.get(pkg);
    if (packageRepresentative != null && packageRepresentative.clazz == clazz) {
      return packageRepresentative;
    }
    return new Representative(clazz);
  }

  private void setRepresentative(String pkg, Representative representative) {
    assert isValidRepresentative(representative.clazz);
    if (Log.ENABLED) {
      if (pkg.equals(GLOBAL)) {
        Log.info(
            getClass(),
            "Making %s the global representative",
            representative.clazz.type.toSourceString());
      } else {
        Log.info(
            getClass(),
            "Making %s the representative for package %s",
            representative.clazz.type.toSourceString(),
            pkg);
      }
    }
    representatives.put(pkg, representative);
  }

  private void clearRepresentative(String pkg) {
    if (Log.ENABLED) {
      if (pkg.equals(GLOBAL)) {
        Log.info(getClass(), "Removing the global representative");
      } else {
        Log.info(getClass(), "Removing the representative for package %s", pkg);
      }
    }
    representatives.remove(pkg);
  }

  private boolean mayMergeAcrossPackageBoundaries(DexProgramClass clazz) {
    // Check that the class is public. Otherwise, accesses to `clazz` from within its current
    // package may become illegal.
    if (!clazz.accessFlags.isPublic()) {
      return false;
    }
    // Check that all of the members are private or public.
    if (!Arrays.stream(clazz.directMethods())
        .allMatch(method -> method.accessFlags.isPrivate() || method.accessFlags.isPublic())) {
      return false;
    }
    if (!Arrays.stream(clazz.staticFields())
        .allMatch(method -> method.accessFlags.isPrivate() || method.accessFlags.isPublic())) {
      return false;
    }

    // Note that a class is only considered a candidate if it has no instance fields and all of its
    // virtual methods are private. Therefore, we don't need to consider check if there are any
    // package-private or protected instance fields or virtual methods here.
    assert Arrays.stream(clazz.instanceFields()).count() == 0;
    assert Arrays.stream(clazz.virtualMethods()).allMatch(method -> method.accessFlags.isPrivate());

    // Check that no methods access package-private or protected members.
    IllegalAccessDetector registry = new IllegalAccessDetector(appView, clazz);
    for (DexEncodedMethod method : clazz.methods()) {
      registry.setContext(method);
      method.registerCodeReferences(registry);
      if (registry.foundIllegalAccess()) {
        return false;
      }
    }
    return true;
  }

  private void moveMembersFromSourceToTarget(
      DexProgramClass sourceClass, DexProgramClass targetClass) {
    if (Log.ENABLED) {
      Log.info(
          getClass(),
          "Merging %s into %s",
          sourceClass.type.toSourceString(),
          targetClass.type.toSourceString());
    }

    assert targetClass.accessFlags.isAtLeastAsVisibleAs(sourceClass.accessFlags);
    assert sourceClass.instanceFields().length == 0;
    assert targetClass.instanceFields().length == 0;

    numberOfMergedClasses++;

    // Move members from source to target.
    targetClass.setDirectMethods(
        mergeMethods(sourceClass.directMethods(), targetClass.directMethods(), targetClass));
    targetClass.setVirtualMethods(
        mergeMethods(sourceClass.virtualMethods(), targetClass.virtualMethods(), targetClass));
    targetClass.setStaticFields(
        mergeFields(sourceClass.staticFields(), targetClass.staticFields(), targetClass));

    // Cleanup source.
    sourceClass.setDirectMethods(DexEncodedMethod.EMPTY_ARRAY);
    sourceClass.setVirtualMethods(DexEncodedMethod.EMPTY_ARRAY);
    sourceClass.setStaticFields(DexEncodedField.EMPTY_ARRAY);
  }

  private DexEncodedMethod[] mergeMethods(
      DexEncodedMethod[] sourceMethods,
      DexEncodedMethod[] targetMethods,
      DexProgramClass targetClass) {
    DexEncodedMethod[] result = new DexEncodedMethod[sourceMethods.length + targetMethods.length];

    // Move all target methods to result.
    System.arraycopy(targetMethods, 0, result, 0, targetMethods.length);

    // Move source methods to result one by one, renaming them if needed.
    MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
    Set<Wrapper<DexMethod>> existingMethods =
        Arrays.stream(targetMethods)
            .map(targetMethod -> equivalence.wrap(targetMethod.method))
            .collect(Collectors.toSet());

    Predicate<DexMethod> availableMethodSignatures =
        method -> !existingMethods.contains(equivalence.wrap(method));

    int i = targetMethods.length;
    for (DexEncodedMethod sourceMethod : sourceMethods) {
      DexEncodedMethod sourceMethodAfterMove =
          renameMethodIfNeeded(sourceMethod, targetClass, availableMethodSignatures);
      result[i++] = sourceMethodAfterMove;

      DexMethod originalMethod =
          methodMapping.inverse().getOrDefault(sourceMethod.method, sourceMethod.method);
      methodMapping.forcePut(originalMethod, sourceMethodAfterMove.method);

      existingMethods.add(equivalence.wrap(sourceMethodAfterMove.method));
    }

    return result;
  }

  private DexEncodedField[] mergeFields(
      DexEncodedField[] sourceFields, DexEncodedField[] targetFields, DexProgramClass targetClass) {
    DexEncodedField[] result = new DexEncodedField[sourceFields.length + targetFields.length];

    // Move all target fields to result.
    System.arraycopy(targetFields, 0, result, 0, targetFields.length);

    // Move source fields to result one by one, renaming them if needed.
    FieldSignatureEquivalence equivalence = FieldSignatureEquivalence.get();
    Set<Wrapper<DexField>> existingFields =
        Arrays.stream(targetFields)
            .map(targetField -> equivalence.wrap(targetField.field))
            .collect(Collectors.toSet());

    Predicate<DexField> availableFieldSignatures =
        field -> !existingFields.contains(equivalence.wrap(field));

    int i = targetFields.length;
    for (DexEncodedField sourceField : sourceFields) {
      DexEncodedField sourceFieldAfterMove =
          renameFieldIfNeeded(sourceField, targetClass, availableFieldSignatures);
      result[i++] = sourceFieldAfterMove;

      DexField originalField =
          fieldMapping.inverse().getOrDefault(sourceField.field, sourceField.field);
      fieldMapping.forcePut(originalField, sourceFieldAfterMove.field);

      existingFields.add(equivalence.wrap(sourceFieldAfterMove.field));
    }

    return result;
  }

  private DexEncodedMethod renameMethodIfNeeded(
      DexEncodedMethod method,
      DexProgramClass targetClass,
      Predicate<DexMethod> availableMethodSignatures) {
    assert !method.accessFlags.isConstructor();
    DexString oldName = method.method.name;
    DexMethod newSignature =
        appView.dexItemFactory().createMethod(targetClass.type, method.method.proto, oldName);
    if (!availableMethodSignatures.test(newSignature)) {
      int count = 1;
      do {
        DexString newName = appView.dexItemFactory().createString(oldName.toSourceString() + count);
        newSignature =
            appView.dexItemFactory().createMethod(targetClass.type, method.method.proto, newName);
        count++;
      } while (!availableMethodSignatures.test(newSignature));
    }
    return method.toTypeSubstitutedMethod(newSignature);
  }

  private DexEncodedField renameFieldIfNeeded(
      DexEncodedField field,
      DexProgramClass targetClass,
      Predicate<DexField> availableFieldSignatures) {
    DexString oldName = field.field.name;
    DexField newSignature =
        appView.dexItemFactory().createField(targetClass.type, field.field.type, oldName);
    if (!availableFieldSignatures.test(newSignature)) {
      int count = 1;
      do {
        DexString newName = appView.dexItemFactory().createString(oldName.toSourceString() + count);
        newSignature =
            appView.dexItemFactory().createField(targetClass.type, field.field.type, newName);
        count++;
      } while (!availableFieldSignatures.test(newSignature));
    }
    return field.toTypeSubstitutedField(newSignature);
  }
}
