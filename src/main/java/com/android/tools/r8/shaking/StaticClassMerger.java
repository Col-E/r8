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
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.VerticalClassMerger.IllegalAccessDetector;
import com.android.tools.r8.utils.FieldSignatureEquivalence;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
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

  private final AppView<? extends AppInfoWithLiveness> appView;

  private final Map<String, DexProgramClass> representatives = new HashMap<>();

  private DexProgramClass globalRepresentative = null;

  private final BiMap<DexField, DexField> fieldMapping = HashBiMap.create();
  private final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();

  public StaticClassMerger(AppView<? extends AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public GraphLense run() {
    // Visit the program classes in a top-down order according to the class hierarchy.
    Iterable<DexProgramClass> classes = appView.appInfo().app.classesWithDeterministicOrder();
    TopDownClassHierarchyTraversal.visit(appView, classes, clazz -> {
      if (satisfiesMergeCriteria(clazz)) {
        merge(clazz);
      }
    });
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
    if (clazz.accessFlags.isInterface()) {
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
    return true;
  }

  private boolean merge(DexProgramClass clazz) {
    assert satisfiesMergeCriteria(clazz);

    String pkg = clazz.type.getPackageDescriptor();
    DexProgramClass representativeForPackage = representatives.get(pkg);

    if (mayMergeAcrossPackageBoundaries(clazz)) {
      if (globalRepresentative != null) {
        // Merge this class into the global representative.
        moveMembersFromSourceToTarget(clazz, globalRepresentative);
        return true;
      }

      // Make the current class the global representative as well as the representative for this
      // package.
      if (Log.ENABLED) {
        Log.info(getClass(), "Making %s a global representative", clazz.type.toSourceString(), pkg);
        Log.info(getClass(), "Making %s a representative for %s", clazz.type.toSourceString(), pkg);
      }
      globalRepresentative = clazz;
      representatives.put(pkg, clazz);

      if (representativeForPackage != null) {
        // If there was a previous representative for this package, we can merge it into the current
        // class that has just become the global representative.
        moveMembersFromSourceToTarget(representativeForPackage, clazz);
        return true;
      }

      // No merge.
      return false;
    }

    if (representativeForPackage != null) {
      if (clazz.accessFlags.isMoreVisibleThan(representativeForPackage.accessFlags)) {
        // Use `clazz` as a representative for this package instead.
        representatives.put(pkg, clazz);
        moveMembersFromSourceToTarget(representativeForPackage, clazz);
        return true;
      }

      // Merge current class into the representative for this package.
      moveMembersFromSourceToTarget(clazz, representativeForPackage);
      return true;
    }

    // No merge.
    if (Log.ENABLED) {
      Log.info(getClass(), "Making %s a representative for %s", clazz.type.toSourceString(), pkg);
    }
    representatives.put(pkg, clazz);
    return false;
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
    IllegalAccessDetector registry = new IllegalAccessDetector(appView.appInfo(), clazz);
    for (DexEncodedMethod method : clazz.methods()) {
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
      methodMapping.put(originalMethod, sourceMethodAfterMove.method);
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
      fieldMapping.put(originalField, sourceFieldAfterMove.field);
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
