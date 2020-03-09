// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.analysis.InitializedClassesInInstanceMethodsAnalysis.InitializedClassesInInstanceMethods;
import com.android.tools.r8.graph.classmerging.HorizontallyMergedLambdaClasses;
import com.android.tools.r8.graph.classmerging.MergedClassesCollection;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.ir.analysis.proto.GeneratedExtensionRegistryShrinker;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteBuilderShrinker;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteShrinker;
import com.android.tools.r8.ir.analysis.proto.ProtoShrinker;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.ir.desugar.PrefixRewritingMapper;
import com.android.tools.r8.ir.optimize.CallSiteOptimizationInfoPropagator;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.ir.optimize.library.LibraryMethodOptimizer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryModeledPredicate;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class AppView<T extends AppInfo> implements DexDefinitionSupplier, LibraryModeledPredicate {

  private enum WholeProgramOptimizations {
    ON,
    OFF
  }

  private T appInfo;
  private AppServices appServices;
  private final DexItemFactory dexItemFactory;
  private final WholeProgramOptimizations wholeProgramOptimizations;
  private GraphLense graphLense;
  private final InternalOptions options;
  private RootSet rootSet;
  private final AbstractValueFactory abstractValueFactory = new AbstractValueFactory();
  private final InstanceFieldInitializationInfoFactory instanceFieldInitializationInfoFactory =
      new InstanceFieldInitializationInfoFactory();

  // Desugared library prefix rewriter.
  public final PrefixRewritingMapper rewritePrefix;

  // Optimizations.
  private final CallSiteOptimizationInfoPropagator callSiteOptimizationInfoPropagator;
  private final LibraryMethodOptimizer libraryMethodOptimizer;
  private final ProtoShrinker protoShrinker;

  // Optimization results.
  private boolean allCodeProcessed = false;
  private Predicate<DexType> classesEscapingIntoLibrary = Predicates.alwaysTrue();
  private InitializedClassesInInstanceMethods initializedClassesInInstanceMethods;
  private Set<DexMethod> unneededVisibilityBridgeMethods = ImmutableSet.of();
  private HorizontallyMergedLambdaClasses horizontallyMergedLambdaClasses;
  private VerticallyMergedClasses verticallyMergedClasses;
  private EnumValueInfoMapCollection unboxedEnums = EnumValueInfoMapCollection.empty();

  private Map<DexClass, DexValueString> sourceDebugExtensions = new IdentityHashMap<>();

  private AppView(
      T appInfo, WholeProgramOptimizations wholeProgramOptimizations, InternalOptions options) {
    this(
        appInfo,
        wholeProgramOptimizations,
        options,
        appInfo == null
            ? PrefixRewritingMapper.empty()
            : options.desugaredLibraryConfiguration.createPrefixRewritingMapper(options));
  }

  private AppView(
      T appInfo,
      WholeProgramOptimizations wholeProgramOptimizations,
      InternalOptions options,
      PrefixRewritingMapper mapper) {
    this.appInfo = appInfo;
    this.dexItemFactory = appInfo != null ? appInfo.dexItemFactory() : null;
    this.wholeProgramOptimizations = wholeProgramOptimizations;
    this.graphLense = GraphLense.getIdentityLense();
    this.options = options;
    this.rewritePrefix = mapper;

    if (enableWholeProgramOptimizations() && options.isCallSiteOptimizationEnabled()) {
      this.callSiteOptimizationInfoPropagator =
          new CallSiteOptimizationInfoPropagator(withLiveness());
    } else {
      this.callSiteOptimizationInfoPropagator = null;
    }

    this.libraryMethodOptimizer = new LibraryMethodOptimizer(this);

    if (enableWholeProgramOptimizations() && options.protoShrinking().isProtoShrinkingEnabled()) {
      this.protoShrinker = new ProtoShrinker(withLiveness());
    } else {
      this.protoShrinker = null;
    }
  }

  @Override
  public boolean isModeled(DexType type) {
    return libraryMethodOptimizer.isModeled(type);
  }

  public static <T extends AppInfo> AppView<T> createForD8(T appInfo, InternalOptions options) {
    return new AppView<>(appInfo, WholeProgramOptimizations.OFF, options);
  }

  public static <T extends AppInfo> AppView<T> createForD8(
      T appInfo, InternalOptions options, PrefixRewritingMapper mapper) {
    return new AppView<>(appInfo, WholeProgramOptimizations.OFF, options, mapper);
  }

  public static <T extends AppInfo> AppView<T> createForR8(T appInfo, InternalOptions options) {
    return new AppView<>(appInfo, WholeProgramOptimizations.ON, options);
  }

  public static <T extends AppInfo> AppView<T> createForL8(
      T appInfo, InternalOptions options, PrefixRewritingMapper mapper) {
    return new AppView<>(appInfo, WholeProgramOptimizations.OFF, options, mapper);
  }

  public AbstractValueFactory abstractValueFactory() {
    return abstractValueFactory;
  }

  public InstanceFieldInitializationInfoFactory instanceFieldInitializationInfoFactory() {
    return instanceFieldInitializationInfoFactory;
  }

  public T appInfo() {
    return appInfo;
  }

  public <U extends T> AppView<U> setAppInfo(U appInfo) {
    assert !appInfo.isObsolete();
    AppInfo previous = this.appInfo;
    this.appInfo = appInfo;
    if (appInfo != previous) {
      previous.markObsolete();
    }
    @SuppressWarnings("unchecked")
    AppView<U> appViewWithSpecializedAppInfo = (AppView<U>) this;
    return appViewWithSpecializedAppInfo;
  }

  public boolean isAllCodeProcessed() {
    return allCodeProcessed;
  }

  public void setAllCodeProcessed() {
    allCodeProcessed = true;
  }

  public GraphLense clearCodeRewritings() {
    return graphLense = graphLense.withCodeRewritingsApplied();
  }

  public AppServices appServices() {
    return appServices;
  }

  public void setAppServices(AppServices appServices) {
    this.appServices = appServices;
  }

  public boolean isClassEscapingIntoLibrary(DexType type) {
    assert type.isClassType();
    return classesEscapingIntoLibrary.test(type);
  }

  public void setClassesEscapingIntoLibrary(Predicate<DexType> classesEscapingIntoLibrary) {
    this.classesEscapingIntoLibrary = classesEscapingIntoLibrary;
  }

  public void setSourceDebugExtensionForType(DexClass clazz, DexValueString sourceDebugExtension) {
    this.sourceDebugExtensions.put(clazz, sourceDebugExtension);
  }

  public DexValueString getSourceDebugExtensionForType(DexClass clazz) {
    return this.sourceDebugExtensions.get(clazz);
  }

  @Override
  public final DexDefinition definitionFor(DexReference reference) {
    return appInfo().definitionFor(reference);
  }

  @Override
  public final DexEncodedField definitionFor(DexField field) {
    return appInfo().definitionFor(field);
  }

  @Override
  public final DexEncodedMethod definitionFor(DexMethod method) {
    return appInfo().definitionFor(method);
  }

  @Override
  public final DexClass definitionFor(DexType type) {
    return appInfo().definitionFor(type);
  }

  @Override
  public final DexProgramClass definitionForProgramType(DexType type) {
    return appInfo.app().programDefinitionFor(type);
  }

  public OptionalBool isInterface(DexType type) {
    assert type.isClassType();
    // Without whole program information we should not assume anything about any other class than
    // the current holder in a given context.
    if (enableWholeProgramOptimizations()) {
      DexClass clazz = definitionFor(type);
      if (clazz == null) {
        return OptionalBool.unknown();
      }
      return OptionalBool.of(clazz.isInterface());
    }
    return OptionalBool.unknown();
  }

  @Override
  public DexItemFactory dexItemFactory() {
    return dexItemFactory;
  }

  public boolean enableWholeProgramOptimizations() {
    return wholeProgramOptimizations == WholeProgramOptimizations.ON;
  }

  public CallSiteOptimizationInfoPropagator callSiteOptimizationInfoPropagator() {
    return callSiteOptimizationInfoPropagator;
  }

  public LibraryMethodOptimizer libraryMethodOptimizer() {
    return libraryMethodOptimizer;
  }

  public ProtoShrinker protoShrinker() {
    return protoShrinker;
  }

  public void withGeneratedExtensionRegistryShrinker(
      Consumer<GeneratedExtensionRegistryShrinker> consumer) {
    if (protoShrinker != null && protoShrinker.generatedExtensionRegistryShrinker != null) {
      consumer.accept(protoShrinker.generatedExtensionRegistryShrinker);
    }
  }

  public <U> U withGeneratedExtensionRegistryShrinker(
      Function<GeneratedExtensionRegistryShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedExtensionRegistryShrinker != null) {
      return fn.apply(protoShrinker.generatedExtensionRegistryShrinker);
    }
    return defaultValue;
  }

  public void withGeneratedMessageLiteShrinker(Consumer<GeneratedMessageLiteShrinker> consumer) {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteShrinker != null) {
      consumer.accept(protoShrinker.generatedMessageLiteShrinker);
    }
  }

  public <E extends Throwable> void withGeneratedMessageLiteBuilderShrinker(
      ThrowingConsumer<GeneratedMessageLiteBuilderShrinker, E> consumer) throws E {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteBuilderShrinker != null) {
      consumer.accept(protoShrinker.generatedMessageLiteBuilderShrinker);
    }
  }

  public <U> U withGeneratedMessageLiteShrinker(
      Function<GeneratedMessageLiteShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteShrinker != null) {
      return fn.apply(protoShrinker.generatedMessageLiteShrinker);
    }
    return defaultValue;
  }

  public <U> U withGeneratedMessageLiteBuilderShrinker(
      Function<GeneratedMessageLiteBuilderShrinker, U> fn, U defaultValue) {
    if (protoShrinker != null && protoShrinker.generatedMessageLiteBuilderShrinker != null) {
      return fn.apply(protoShrinker.generatedMessageLiteBuilderShrinker);
    }
    return defaultValue;
  }

  public GraphLense graphLense() {
    return graphLense;
  }

  /** @return true if the graph lens changed, otherwise false. */
  public boolean setGraphLense(GraphLense graphLense) {
    if (graphLense != this.graphLense) {
      this.graphLense = graphLense;
      return true;
    }
    return false;
  }

  public void setInitializedClassesInInstanceMethods(
      InitializedClassesInInstanceMethods initializedClassesInInstanceMethods) {
    this.initializedClassesInInstanceMethods = initializedClassesInInstanceMethods;
  }

  public <U> U withInitializedClassesInInstanceMethods(
      Function<InitializedClassesInInstanceMethods, U> fn, U defaultValue) {
    if (initializedClassesInInstanceMethods != null) {
      return fn.apply(initializedClassesInInstanceMethods);
    }
    return defaultValue;
  }

  public InternalOptions options() {
    return options;
  }

  public RootSet rootSet() {
    return rootSet;
  }

  public void setRootSet(RootSet rootSet) {
    assert this.rootSet == null : "Root set should never be recomputed";
    this.rootSet = rootSet;
  }

  public Set<DexMethod> unneededVisibilityBridgeMethods() {
    return unneededVisibilityBridgeMethods;
  }

  public void setUnneededVisibilityBridgeMethods(Set<DexMethod> unneededVisibilityBridgeMethods) {
    this.unneededVisibilityBridgeMethods = unneededVisibilityBridgeMethods;
  }

  public MergedClassesCollection allMergedClasses() {
    MergedClassesCollection collection = new MergedClassesCollection();
    if (horizontallyMergedLambdaClasses != null) {
      collection.add(horizontallyMergedLambdaClasses);
    }
    if (verticallyMergedClasses != null) {
      collection.add(verticallyMergedClasses);
    }
    return collection;
  }

  // Get the result of horizontal lambda class merging. Returns null if horizontal lambda class
  // merging has not been run.
  public HorizontallyMergedLambdaClasses horizontallyMergedLambdaClasses() {
    return horizontallyMergedLambdaClasses;
  }

  public void setHorizontallyMergedLambdaClasses(
      HorizontallyMergedLambdaClasses horizontallyMergedLambdaClasses) {
    this.horizontallyMergedLambdaClasses = horizontallyMergedLambdaClasses;
  }

  // Get the result of vertical class merging. Returns null if vertical class merging has not been
  // run.
  public VerticallyMergedClasses verticallyMergedClasses() {
    return verticallyMergedClasses;
  }

  public void setVerticallyMergedClasses(VerticallyMergedClasses verticallyMergedClasses) {
    this.verticallyMergedClasses = verticallyMergedClasses;
  }

  public EnumValueInfoMapCollection unboxedEnums() {
    return unboxedEnums;
  }

  public void setUnboxedEnums(EnumValueInfoMapCollection unboxedEnums) {
    this.unboxedEnums = unboxedEnums;
  }

  public boolean validateUnboxedEnumsHaveBeenPruned() {
    for (DexType unboxedEnum : unboxedEnums.enumSet()) {
      assert definitionForProgramType(unboxedEnum) == null
          : "Enum " + unboxedEnum + " has been unboxed but is still in the program.";
      assert appInfo().withLiveness().wasPruned(unboxedEnum)
          : "Enum " + unboxedEnum + " has been unboxed but was not pruned.";
    }
    return true;
  }

  @SuppressWarnings("unchecked")
  public AppView<AppInfoWithClassHierarchy> withClassHierarchy() {
    return appInfo.hasClassHierarchy()
        ? (AppView<AppInfoWithClassHierarchy>) this
        : null;
  }

  @SuppressWarnings("unchecked")
  public AppView<AppInfoWithSubtyping> withSubtyping() {
    return appInfo.hasSubtyping()
        ? (AppView<AppInfoWithSubtyping>) this
        : null;
  }

  public AppView<AppInfoWithLiveness> withLiveness() {
    @SuppressWarnings("unchecked")
    AppView<AppInfoWithLiveness> appViewWithLiveness = (AppView<AppInfoWithLiveness>) this;
    return appViewWithLiveness;
  }

  public OptionalBool isSubtype(DexType subtype, DexType supertype) {
    return appInfo().hasSubtyping()
        ? OptionalBool.of(appInfo().withSubtyping().isSubtype(subtype, supertype))
        : OptionalBool.unknown();
  }
}
