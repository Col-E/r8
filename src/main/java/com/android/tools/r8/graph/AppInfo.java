// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.origin.GlobalSyntheticOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.CommittedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class AppInfo implements DexDefinitionSupplier {

  private final DexApplication app;
  private final DexItemFactory dexItemFactory;
  private final MainDexInfo mainDexInfo;
  private final SyntheticItems syntheticItems;

  // Set when a new AppInfo replaces a previous one. All public methods should verify that the
  // current instance is not obsolete, to ensure that we almost use the most recent AppInfo.
  private final BooleanBox obsolete;

  public static AppInfo createInitialAppInfo(
      DexApplication application, GlobalSyntheticsStrategy globalSyntheticsStrategy) {
    return createInitialAppInfo(application, globalSyntheticsStrategy, MainDexInfo.none());
  }

  public static AppInfo createInitialAppInfo(
      DexApplication application,
      GlobalSyntheticsStrategy globalSyntheticsStrategy,
      MainDexInfo mainDexInfo) {
    return new AppInfo(
        SyntheticItems.createInitialSyntheticItems(application, globalSyntheticsStrategy),
        mainDexInfo);
  }

  public AppInfo(CommittedItems committedItems, MainDexInfo mainDexInfo) {
    this(
        committedItems.getApplication(),
        committedItems.toSyntheticItems(),
        mainDexInfo,
        new BooleanBox());
  }

  // For desugaring.
  // This is a view onto the app info and is the only place the pending synthetics are shared.
  AppInfo(AppInfoWithClassHierarchy.CreateDesugaringViewOnAppInfo witness, AppInfo appInfo) {
    this(appInfo.app, appInfo.syntheticItems, appInfo.mainDexInfo, appInfo.obsolete);
    assert witness != null;
  }

  private AppInfo(
      DexApplication application,
      SyntheticItems syntheticItems,
      MainDexInfo mainDexInfo,
      BooleanBox obsolete) {
    this.app = application;
    this.dexItemFactory = application.dexItemFactory;
    this.mainDexInfo = mainDexInfo;
    this.syntheticItems = syntheticItems;
    this.obsolete = obsolete;
  }

  public AppInfo prunedCopyFrom(
      PrunedItems prunedItems, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    assert getClass() == AppInfo.class;
    assert checkIfObsolete();
    assert prunedItems.getPrunedApp() == app();
    if (prunedItems.isEmpty()) {
      return this;
    }
    timing.begin("Pruning AppInfo");
    AppInfo result =
        new AppInfo(
            getSyntheticItems().commitPrunedItems(prunedItems),
            getMainDexInfo().withoutPrunedItems(prunedItems));
    timing.end();
    return result;
  }

  public AppInfo rebuildWithMainDexInfo(MainDexInfo mainDexInfo) {
    assert checkIfObsolete();
    return new AppInfo(app, syntheticItems, mainDexInfo, new BooleanBox());
  }

  public InternalOptions options() {
    return app.options;
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

  public MainDexInfo getMainDexInfo() {
    assert checkIfObsolete();
    return mainDexInfo;
  }

  public SyntheticItems getSyntheticItems() {
    assert checkIfObsolete();
    return syntheticItems;
  }

  public Collection<DexProgramClass> classes() {
    assert checkIfObsolete();
    return app.classes();
  }

  public Collection<DexProgramClass> classesWithDeterministicOrder() {
    assert checkIfObsolete();
    return app.classesWithDeterministicOrder();
  }

  public void forEachMethod(Consumer<ProgramMethod> consumer) {
    for (DexProgramClass clazz : classes()) {
      clazz.forEachProgramMethod(consumer);
    }
  }

  @Override
  public ClassResolutionResult contextIndependentDefinitionForWithResolutionResult(DexType type) {
    assert checkIfObsolete();
    return syntheticItems.definitionFor(
        type, app::contextIndependentDefinitionForWithResolutionResult);
  }

  @Override
  public DexClass definitionFor(DexType type) {
    return definitionForWithoutExistenceAssert(type);
  }

  public final DexClass definitionForWithoutExistenceAssert(DexType type) {
    assert checkIfObsolete();
    return syntheticItems
        .definitionFor(type, app::contextIndependentDefinitionForWithResolutionResult)
        .toSingleClassWithProgramOverLibrary();
  }

  public final boolean hasDefinitionForWithoutExistenceAssert(DexType type) {
    return definitionForWithoutExistenceAssert(type) != null;
  }

  @SuppressWarnings("ReferenceEquality")
  public DexClass definitionForDesugarDependency(DexClass dependent, DexType type) {
    if (dependent.type == type) {
      return dependent;
    }
    DexClass definition = definitionFor(type);
    if (definition != null && !definition.isLibraryClass() && !dependent.isLibraryClass()) {
      reportDependencyEdge(dependent, definition);
    }
    return definition;
  }

  public void reportDependencyEdge(DexClass dependent, DexClass dependency) {
    assert !dependent.isLibraryClass();
    assert !dependency.isLibraryClass();
    DesugarGraphConsumer consumer = options().desugarGraphConsumer;
    if (consumer == null) {
      return;
    }
    Origin dependencyOrigin = dependency.getOrigin();
    Collection<Origin> dependentOrigins =
        getSyntheticItems().getSynthesizingOrigin(dependent.getType());
    if (dependentOrigins.isEmpty()) {
      reportDependencyEdge(consumer, dependencyOrigin, dependent.getOrigin());
    } else {
      for (Origin dependentOrigin : dependentOrigins) {
        reportDependencyEdge(consumer, dependencyOrigin, dependentOrigin);
      }
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private void reportDependencyEdge(
      DesugarGraphConsumer consumer, Origin dependencyOrigin, Origin dependentOrigin) {
    if (dependencyOrigin == GlobalSyntheticOrigin.instance()
        || dependentOrigin == GlobalSyntheticOrigin.instance()) {
      // D8/R8 does not report edges to synthetic classes that D8/R8 generates.
      return;
    }
    if (dependentOrigin != dependencyOrigin) {
      consumer.accept(dependentOrigin, dependencyOrigin);
    }
  }

  /**
   * Lookup static method on the method holder, or answers null.
   *
   * @param method the method to lookup
   * @param context the method the invoke is contained in, i.e., the caller.
   * @return The actual target for {@code method} if on the holder, or {@code null}.
   */
  public final DexClassAndMethod lookupStaticTargetOnItself(
      DexMethod method, ProgramMethod context) {
    if (!method.getHolderType().isIdenticalTo(context.getHolderType())) {
      return null;
    }
    DexClassAndMethod singleTarget = context.getHolder().lookupDirectClassMethod(method);
    if (singleTarget != null && singleTarget.getAccessFlags().isStatic()) {
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
  public final DexClassAndMethod lookupDirectTargetOnItself(
      DexMethod method, ProgramMethod context) {
    if (!method.getHolderType().isIdenticalTo(context.getHolderType())) {
      return null;
    }
    DexClassAndMethod singleTarget = context.getHolder().lookupDirectClassMethod(method);
    if (singleTarget != null && !singleTarget.getAccessFlags().isStatic()) {
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

  public final FieldResolutionResult resolveField(DexField field, ProgramMethod context) {
    return resolveFieldOn(field.holder, field, context);
  }

  @SuppressWarnings("ReferenceEquality")
  public FieldResolutionResult resolveFieldOn(DexType type, DexField field, ProgramMethod context) {
    // Only allow resolution if the field is declared in the context.
    if (type != context.getHolderType()) {
      return FieldResolutionResult.failure();
    }
    DexProgramClass clazz = context.getHolder();
    DexEncodedField definition = clazz.lookupField(field);
    return definition != null
        ? FieldResolutionResult.createSingleFieldResolutionResult(clazz, clazz, definition)
        : FieldResolutionResult.unknown();
  }

  public void notifyHorizontalClassMergerFinished(
      HorizontalClassMerger.Mode horizontalClassMergerMode) {
    // Intentionally empty.
  }
}
