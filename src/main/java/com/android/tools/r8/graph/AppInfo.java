// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMap.Builder;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AppInfo implements DexDefinitionSupplier {

  private final DexApplication app;
  private final DexItemFactory dexItemFactory;

  // TODO(b/151804585): Remove this cache.
  private final ConcurrentHashMap<DexType, Map<DexField, DexEncodedField>> fieldDefinitionsCache;

  // For some optimizations, e.g. optimizing synthetic classes, we may need to resolve the current
  // class being optimized.
  private final ConcurrentHashMap<DexType, DexProgramClass> synthesizedClasses;

  // Set when a new AppInfo replaces a previous one. All public methods should verify that the
  // current instance is not obsolete, to ensure that we almost use the most recent AppInfo.
  private final BooleanBox obsolete;

  public AppInfo(DexApplication application) {
    this(application, new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new BooleanBox());
  }

  // For desugaring.
  protected AppInfo(AppInfo appInfo) {
    this(appInfo.app, appInfo.fieldDefinitionsCache, appInfo.synthesizedClasses, appInfo.obsolete);
  }

  // For AppInfoWithLiveness.
  protected AppInfo(AppInfoWithClassHierarchy previous) {
    this(
        ((AppInfo) previous).app,
        new ConcurrentHashMap<>(((AppInfo) previous).fieldDefinitionsCache),
        new ConcurrentHashMap<>(((AppInfo) previous).synthesizedClasses),
        new BooleanBox());
  }

  private AppInfo(
      DexApplication application,
      ConcurrentHashMap<DexType, Map<DexField, DexEncodedField>> fieldDefinitionsCache,
      ConcurrentHashMap<DexType, DexProgramClass> synthesizedClasses,
      BooleanBox obsolete) {
    this.app = application;
    this.dexItemFactory = application.dexItemFactory;
    this.fieldDefinitionsCache = fieldDefinitionsCache;
    this.synthesizedClasses = synthesizedClasses;
    this.obsolete = obsolete;
  }

  protected InternalOptions options() {
    return app.options;
  }

  public void copyMetadataFromPrevious(AppInfo previous) {
    this.synthesizedClasses.putAll(previous.synthesizedClasses);
  }

  public boolean isObsolete() {
    return obsolete.get();
  }

  public void markObsolete() {
    obsolete.set();
  }

  public void unsetObsolete() {
    obsolete.unset();
  }

  public boolean checkIfObsolete() {
    assert !isObsolete();
    return true;
  }

  public DexApplication app() {
    assert checkIfObsolete();
    return app;
  }

  @Override
  public DexItemFactory dexItemFactory() {
    assert checkIfObsolete();
    return dexItemFactory;
  }

  public void addSynthesizedClass(DexProgramClass clazz) {
    assert checkIfObsolete();
    assert clazz.type.isD8R8SynthesizedClassType();
    DexProgramClass previous = synthesizedClasses.put(clazz.type, clazz);
    invalidateFieldCacheFor(clazz.type);
    assert previous == null || previous == clazz;
  }

  public Collection<DexProgramClass> synthesizedClasses() {
    assert checkIfObsolete();
    return Collections.unmodifiableCollection(synthesizedClasses.values());
  }

  private Map<DexField, DexEncodedField> computeFieldDefinitions(DexType type) {
    Builder<DexField, DexEncodedField> builder = ImmutableMap.builder();
    DexClass clazz = definitionFor(type);
    if (clazz != null) {
      clazz.forEachField(field -> builder.put(field.field, field));
    }
    return builder.build();
  }

  public Collection<DexProgramClass> classes() {
    assert checkIfObsolete();
    return app.classes();
  }

  public Iterable<DexProgramClass> classesWithDeterministicOrder() {
    assert checkIfObsolete();
    return app.classesWithDeterministicOrder();
  }

  @Override
  public DexDefinition definitionFor(DexReference reference) {
    assert checkIfObsolete();
    if (reference.isDexType()) {
      return definitionFor(reference.asDexType());
    }
    if (reference.isDexMethod()) {
      return definitionFor(reference.asDexMethod());
    }
    assert reference.isDexField();
    return definitionFor(reference.asDexField());
  }

  @Override
  public DexClass definitionFor(DexType type) {
    return definitionForWithoutExistenceAssert(type);
  }

  public final DexClass definitionForWithoutExistenceAssert(DexType type) {
    assert checkIfObsolete();
    DexProgramClass cached = synthesizedClasses.get(type);
    if (cached != null) {
      assert app.definitionFor(type) == null;
      return cached;
    }
    return app.definitionFor(type);
  }

  public DexClass definitionForDesugarDependency(DexClass dependent, DexType type) {
    if (dependent.type == type) {
      return dependent;
    }
    DexClass definition = definitionFor(type);
    if (definition != null && !definition.isLibraryClass() && dependent.isProgramClass()) {
      InterfaceMethodRewriter.reportDependencyEdge(
          dependent.asProgramClass(), definition, options());
    }
    return definition;
  }

  @Override
  public DexProgramClass definitionForProgramType(DexType type) {
    return app.programDefinitionFor(type);
  }

  public Origin originFor(DexType type) {
    assert checkIfObsolete();
    DexClass definition = app.definitionFor(type);
    return definition == null ? Origin.unknown() : definition.origin;
  }

  @Override
  public DexEncodedMethod definitionFor(DexMethod method) {
    assert checkIfObsolete();
    assert method.holder.isClassType();
    if (!method.holder.isClassType()) {
      return null;
    }
    DexClass clazz = definitionFor(method.holder);
    if (clazz == null) {
      return null;
    }
    return clazz.getMethodCollection().getMethod(method);
  }

  @Override
  public DexEncodedField definitionFor(DexField field) {
    assert checkIfObsolete();
    return getFieldDefinitions(field.holder).get(field);
  }

  private Map<DexField, DexEncodedField> getFieldDefinitions(DexType type) {
    return fieldDefinitionsCache.computeIfAbsent(type, this::computeFieldDefinitions);
  }

  public void invalidateFieldCacheFor(DexType type) {
    fieldDefinitionsCache.remove(type);
  }

  /**
   * Lookup static method on the method holder, or answers null.
   *
   * @param method the method to lookup
   * @param context the method the invoke is contained in, i.e., the caller.
   * @return The actual target for {@code method} if on the holder, or {@code null}.
   */
  public final DexEncodedMethod lookupStaticTargetOnItself(
      DexMethod method, ProgramMethod context) {
    if (method.holder != context.getHolderType()) {
      return null;
    }
    DexEncodedMethod singleTarget = context.getHolder().lookupDirectMethod(method);
    if (singleTarget != null && singleTarget.isStatic()) {
      return singleTarget;
    }
    return null;
  }

  /**
   * Lookup direct method on the method holder, or answers null.
   *
   * @param method the method to lookup
   * @param context the method the invoke is contained in, i.e., the caller.
   * @return The actual target for {@code method} if on the holder, or {@code null}.
   */
  public final DexEncodedMethod lookupDirectTargetOnItself(
      DexMethod method, ProgramMethod context) {
    if (method.holder != context.getHolderType()) {
      return null;
    }
    DexEncodedMethod singleTarget = context.getHolder().lookupDirectMethod(method);
    if (singleTarget != null && !singleTarget.isStatic()) {
      return singleTarget;
    }
    return null;
  }

  public boolean hasClassHierarchy() {
    assert checkIfObsolete();
    return false;
  }

  public AppInfoWithClassHierarchy withClassHierarchy() {
    assert checkIfObsolete();
    return null;
  }

  public boolean hasLiveness() {
    assert checkIfObsolete();
    return false;
  }

  public AppInfoWithLiveness withLiveness() {
    assert checkIfObsolete();
    return null;
  }

  public boolean isInMainDexList(DexType type) {
    assert checkIfObsolete();
    return app.mainDexList.contains(type);
  }

  public final FieldResolutionResult resolveField(DexField field, ProgramMethod context) {
    return resolveFieldOn(field.holder, field, context);
  }

  public FieldResolutionResult resolveFieldOn(DexType type, DexField field, ProgramMethod context) {
    // Only allow resolution if the field is declared in the context.
    if (type != context.getHolderType()) {
      return FieldResolutionResult.failure();
    }
    DexProgramClass clazz = context.getHolder();
    DexEncodedField definition = clazz.lookupField(field);
    return definition != null
        ? new SuccessfulFieldResolutionResult(clazz, clazz, definition)
        : FieldResolutionResult.unknown();
  }
}
