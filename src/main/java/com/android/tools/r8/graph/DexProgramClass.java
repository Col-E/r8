// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.Resource.Origin;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.utils.ProgramResource;
import com.android.tools.r8.utils.ProgramResource.Kind;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DexProgramClass extends DexClass implements Supplier<DexProgramClass> {
  private final ProgramResource.Kind originKind;
  private DexEncodedArray staticValues;
  private final Collection<DexProgramClass> synthesizedFrom;

  public DexProgramClass(
      DexType type,
      ProgramResource.Kind originKind,
      Origin origin,
      DexAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods) {
    this(
        type,
        originKind,
        origin,
        accessFlags,
        superType,
        interfaces,
        sourceFile,
        classAnnotations,
        staticFields,
        instanceFields,
        directMethods,
        virtualMethods,
        Collections.emptyList());
  }

  public DexProgramClass(
      DexType type,
      ProgramResource.Kind originKind,
      Origin origin,
      DexAccessFlags accessFlags,
      DexType superType,
      DexTypeList interfaces,
      DexString sourceFile,
      DexAnnotationSet classAnnotations,
      DexEncodedField[] staticFields,
      DexEncodedField[] instanceFields,
      DexEncodedMethod[] directMethods,
      DexEncodedMethod[] virtualMethods,
      Collection<DexProgramClass> synthesizedDirectlyFrom) {
    super(sourceFile, interfaces, accessFlags, superType, type, staticFields,
        instanceFields, directMethods, virtualMethods, classAnnotations, origin);
    assert classAnnotations != null;
    this.originKind = originKind;
    this.synthesizedFrom = accumulateSynthesizedFrom(new HashSet<>(), synthesizedDirectlyFrom);
  }

  public boolean originatesFromDexResource() {
    return originKind == Kind.DEX;
  }

  public boolean originatesFromClassResource() {
    return originKind == Kind.CLASS;
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection indexedItems) {
    if (indexedItems.addClass(this)) {
      type.collectIndexedItems(indexedItems);
      if (superType != null) {
        superType.collectIndexedItems(indexedItems);
      } else {
        assert type.toDescriptorString().equals("Ljava/lang/Object;");
      }
      if (sourceFile != null) {
        sourceFile.collectIndexedItems(indexedItems);
      }
      if (annotations != null) {
        annotations.collectIndexedItems(indexedItems);
      }
      if (interfaces != null) {
        interfaces.collectIndexedItems(indexedItems);
      }
      collectAll(indexedItems, staticFields);
      collectAll(indexedItems, instanceFields);
      collectAll(indexedItems, directMethods);
      collectAll(indexedItems, virtualMethods);
    }
  }

  public Collection<DexProgramClass> getSynthesizedFrom() {
    return synthesizedFrom;
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    if (hasAnnotations()) {
      mixedItems.setAnnotationsDirectoryForClass(this, new DexAnnotationDirectory(this));
    }
  }

  @Override
  public void addDependencies(MixedSectionCollection collector) {
    // We only have a class data item if there are methods or fields.
    if (hasMethodsOrFields()) {
      collector.add(this);
      collectAll(collector, directMethods);
      collectAll(collector, virtualMethods);
      collectAll(collector, staticFields);
      collectAll(collector, instanceFields);
    }
    if (annotations != null) {
      annotations.collectMixedSectionItems(collector);
    }
    if (interfaces != null) {
      interfaces.collectMixedSectionItems(collector);
    }
    annotations.collectMixedSectionItems(collector);
  }

  @Override
  public String toString() {
    return type.toString();
  }

  @Override
  public String toSourceString() {
    return type.toSourceString();
  }

  @Override
  public boolean isProgramClass() {
    return true;
  }

  @Override
  public DexProgramClass asProgramClass() {
    return this;
  }

  public boolean hasMethodsOrFields() {
    int numberOfFields = staticFields().length + instanceFields().length;
    int numberOfMethods = directMethods().length + virtualMethods().length;
    return numberOfFields + numberOfMethods > 0;
  }

  public boolean hasAnnotations() {
    return !annotations.isEmpty()
        || hasAnnotations(virtualMethods)
        || hasAnnotations(directMethods)
        || hasAnnotations(staticFields)
        || hasAnnotations(instanceFields);
  }

  boolean hasOnlyInternalizableAnnotations() {
    return !hasAnnotations(virtualMethods)
        && !hasAnnotations(directMethods)
        && !hasAnnotations(staticFields)
        && !hasAnnotations(instanceFields);
  }

  private boolean hasAnnotations(DexEncodedField[] fields) {
    return fields != null && Arrays.stream(fields).anyMatch(DexEncodedField::hasAnnotation);
  }

  private boolean hasAnnotations(DexEncodedMethod[] methods) {
    return methods != null && Arrays.stream(methods).anyMatch(DexEncodedMethod::hasAnnotation);
  }

  private static Collection<DexProgramClass> accumulateSynthesizedFrom(
      Set<DexProgramClass> accumulated,
      Collection<DexProgramClass> toAccumulate) {
    for (DexProgramClass dexProgramClass : toAccumulate) {
      if (dexProgramClass.synthesizedFrom.isEmpty()) {
        accumulated.add(dexProgramClass);
      } else {
        accumulateSynthesizedFrom(accumulated, dexProgramClass.synthesizedFrom);
      }
    }
    return accumulated;
  }

  public void setStaticValues(DexEncodedArray staticValues) {
    this.staticValues = staticValues;
  }

  public DexEncodedArray getStaticValues() {
    return staticValues;
  }

  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    assert !virtualMethod.accessFlags.isStatic();
    assert !virtualMethod.accessFlags.isPrivate();
    assert !virtualMethod.accessFlags.isConstructor();
    virtualMethods = Arrays.copyOf(virtualMethods, virtualMethods.length + 1);
    virtualMethods[virtualMethods.length - 1] = virtualMethod;
  }

  public void addStaticMethod(DexEncodedMethod staticMethod) {
    assert staticMethod.accessFlags.isStatic();
    assert !staticMethod.accessFlags.isPrivate();
    directMethods = Arrays.copyOf(directMethods, directMethods.length + 1);
    directMethods[directMethods.length - 1] = staticMethod;
  }

  public void removeStaticMethod(DexEncodedMethod staticMethod) {
    assert staticMethod.accessFlags.isStatic();
    DexEncodedMethod[] newDirectMethods = new DexEncodedMethod[directMethods.length - 1];
    int toIndex = 0;
    for (int fromIndex = 0; fromIndex < directMethods.length; fromIndex++) {
      if (directMethods[fromIndex] != staticMethod) {
        newDirectMethods[toIndex++] = directMethods[fromIndex];
      }
    }
    directMethods = newDirectMethods;
  }

  public synchronized void sortMembers() {
    sortEncodedFields(staticFields);
    sortEncodedFields(instanceFields);
    sortEncodedMethods(directMethods);
    sortEncodedMethods(virtualMethods);
  }

  private void sortEncodedFields(DexEncodedField[] fields) {
    Arrays.sort(fields, Comparator.comparing(a -> a.field));
  }

  private void sortEncodedMethods(DexEncodedMethod[] methods) {
    Arrays.sort(methods, Comparator.comparing(a -> a.method));
  }

  @Override
  public DexProgramClass get() {
    return this;
  }
}
