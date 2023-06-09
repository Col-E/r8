// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.horizontalclassmerging.code.SyntheticInitializerConverter;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.profile.art.ArtProfileCompletenessChecker;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class HorizontalClassMerger {

  public enum Mode {
    INITIAL,
    FINAL;

    public boolean isInitial() {
      return this == INITIAL;
    }

    public boolean isFinal() {
      return this == FINAL;
    }
  }

  private final AppView<?> appView;
  private final Mode mode;
  private final HorizontalClassMergerOptions options;

  private HorizontalClassMerger(AppView<?> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
    this.options = appView.options().horizontalClassMergerOptions();
  }

  public static HorizontalClassMerger createForInitialClassMerging(
      AppView<AppInfoWithLiveness> appView) {
    return new HorizontalClassMerger(appView, Mode.INITIAL);
  }

  public static HorizontalClassMerger createForFinalClassMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new HorizontalClassMerger(appView, Mode.FINAL);
  }

  public static HorizontalClassMerger createForD8ClassMerging(AppView<?> appView) {
    assert appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics();
    return new HorizontalClassMerger(appView, Mode.FINAL);
  }

  public void runIfNecessary(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    runIfNecessary(executorService, timing, null);
  }

  public void runIfNecessary(
      ExecutorService executorService, Timing timing, RuntimeTypeCheckInfo runtimeTypeCheckInfo)
      throws ExecutionException {
    if (options.isEnabled(mode)) {
      timing.begin("HorizontalClassMerger (" + mode.toString() + ")");
      IRCodeProvider codeProvider =
          appView.hasClassHierarchy()
              ? IRCodeProvider.create(appView.withClassHierarchy())
              : IRCodeProvider.createThrowing();
      run(runtimeTypeCheckInfo, codeProvider, executorService, timing);

      assert ArtProfileCompletenessChecker.verify(appView);

      // Clear type elements cache after IR building.
      appView.dexItemFactory().clearTypeElementsCache();

      timing.end();
    } else {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty(), mode);
    }
  }

  private void run(
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      IRCodeProvider codeProvider,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    // Run the policies on all program classes to produce a final grouping.
    List<Policy> policies =
        PolicyScheduler.getPolicies(appView, codeProvider, mode, runtimeTypeCheckInfo);
    Collection<MergeGroup> groups =
        new PolicyExecutor().run(getInitialGroups(), policies, executorService, timing);

    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty(), mode);
      return;
    }

    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();

    // Merge the classes.
    List<ClassMerger> classMergers = initializeClassMergers(codeProvider, lensBuilder, groups);
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    SyntheticArgumentClass syntheticArgumentClass =
        mode.isInitial()
            ? new SyntheticArgumentClass.Builder(appView.withLiveness()).build(groups)
            : null;
    SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder =
        SyntheticInitializerConverter.builder(appView, codeProvider, mode);
    List<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfos = new ArrayList<>();
    PrunedItems prunedItems =
        applyClassMergers(
            classMergers,
            profileCollectionAdditions,
            syntheticArgumentClass,
            syntheticInitializerConverterBuilder,
            virtuallyMergedMethodsKeepInfos::add);

    SyntheticInitializerConverter syntheticInitializerConverter =
        syntheticInitializerConverterBuilder.build();
    assert syntheticInitializerConverter.isEmpty() || appView.enableWholeProgramOptimizations();
    syntheticInitializerConverter.convertClassInitializers(executorService);

    // Generate the graph lens.
    HorizontallyMergedClasses mergedClasses =
        HorizontallyMergedClasses.builder().addMergeGroups(groups).build();
    appView.setHorizontallyMergedClasses(mergedClasses, mode);

    HorizontalClassMergerGraphLens horizontalClassMergerGraphLens =
        createLens(
            mergedClasses, lensBuilder, mode, profileCollectionAdditions, syntheticArgumentClass);
    profileCollectionAdditions =
        profileCollectionAdditions.rewriteMethodReferences(
            horizontalClassMergerGraphLens::getNextMethodToInvoke);

    assert verifyNoCyclesInInterfaceHierarchies(appView, groups);

    FieldAccessInfoCollectionModifier fieldAccessInfoCollectionModifier = null;
    if (mode.isInitial()) {
      fieldAccessInfoCollectionModifier = createFieldAccessInfoCollectionModifier(groups);
    } else {
      assert groups.stream().noneMatch(MergeGroup::hasClassIdField);
    }

    // Set the new graph lens before finalizing any synthetic code.
    appView.setGraphLens(horizontalClassMergerGraphLens);
    codeProvider.setGraphLens(horizontalClassMergerGraphLens);

    // Finalize synthetic code.
    transformIncompleteCode(groups, horizontalClassMergerGraphLens, executorService);
    syntheticInitializerConverter.convertInstanceInitializers(executorService);

    // Must rewrite AppInfoWithLiveness before pruning the merged classes, to ensure that allocation
    // sites, fields accesses, etc. are correctly transferred to the target classes.
    DexApplication newApplication = getNewApplication(mergedClasses);
    if (appView.enableWholeProgramOptimizations()) {
      // Prune keep info.
      KeepInfoCollection keepInfo = appView.getKeepInfo();
      keepInfo.mutate(mutator -> mutator.removeKeepInfoForMergedClasses(prunedItems));
      assert appView.hasClassHierarchy();
      appView.rewriteWithLensAndApplication(
          horizontalClassMergerGraphLens, newApplication.toDirect(), executorService, timing);
    } else {
      assert mode.isFinal();
      SyntheticItems syntheticItems = appView.appInfo().getSyntheticItems();
      assert !syntheticItems.hasPendingSyntheticClasses();
      appView
          .withoutClassHierarchy()
          .setAppInfo(
              new AppInfo(
                  syntheticItems.commitRewrittenWithLens(
                      newApplication, horizontalClassMergerGraphLens, timing),
                  appView
                      .appInfo()
                      .getMainDexInfo()
                      .rewrittenWithLens(syntheticItems, horizontalClassMergerGraphLens, timing)));
      appView.rewriteWithD8Lens(horizontalClassMergerGraphLens, timing);
    }

    // Amend art profile collection.
    profileCollectionAdditions
        .setArtProfileCollection(appView.getArtProfileCollection())
        .setStartupProfile(appView.getStartupProfile())
        .commit(appView);

    // Record where the synthesized $r8$classId fields are read and written.
    if (fieldAccessInfoCollectionModifier != null) {
      fieldAccessInfoCollectionModifier.modify(appView.withLiveness());
    }

    appView.pruneItems(
        prunedItems.toBuilder().setPrunedApp(appView.app()).build(), executorService);

    amendKeepInfo(horizontalClassMergerGraphLens, virtuallyMergedMethodsKeepInfos);
  }

  private void amendKeepInfo(
      HorizontalClassMergerGraphLens horizontalClassMergerGraphLens,
      List<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfos) {
    if (!appView.enableWholeProgramOptimizations()) {
      assert virtuallyMergedMethodsKeepInfos.isEmpty();
      return;
    }
    appView
        .getKeepInfo()
        .mutate(
            keepInfo -> {
              for (VirtuallyMergedMethodsKeepInfo virtuallyMergedMethodsKeepInfo :
                  virtuallyMergedMethodsKeepInfos) {
                DexMethod representative = virtuallyMergedMethodsKeepInfo.getRepresentative();
                MethodLookupResult lookupResult =
                    horizontalClassMergerGraphLens.lookupMethod(
                        representative,
                        null,
                        InvokeType.VIRTUAL,
                        horizontalClassMergerGraphLens.getPrevious());
                ProgramMethod mergedMethod =
                    asProgramMethodOrNull(appView.definitionFor(lookupResult.getReference()));
                if (mergedMethod != null) {
                  keepInfo.joinMethod(
                      mergedMethod,
                      info -> info.merge(virtuallyMergedMethodsKeepInfo.getKeepInfo()));
                  continue;
                }
                assert false;
              }
            });
  }

  private FieldAccessInfoCollectionModifier createFieldAccessInfoCollectionModifier(
      Collection<MergeGroup> groups) {
    assert mode.isInitial();
    FieldAccessInfoCollectionModifier.Builder builder =
        new FieldAccessInfoCollectionModifier.Builder();
    for (MergeGroup group : groups) {
      if (group.hasClassIdField()) {
        DexProgramClass target = group.getTarget();
        target.forEachProgramInstanceInitializerMatching(
            definition -> definition.getCode().isHorizontalClassMergerCode(),
            method -> builder.recordFieldWrittenInContext(group.getClassIdField(), method));
        target.forEachProgramVirtualMethodMatching(
            definition ->
                definition.hasCode() && definition.getCode().isHorizontalClassMergerCode(),
            method -> builder.recordFieldReadInContext(group.getClassIdField(), method));
      }
    }
    return builder.build();
  }

  private void transformIncompleteCode(
      Collection<MergeGroup> groups,
      HorizontalClassMergerGraphLens horizontalClassMergerGraphLens,
      ExecutorService executorService)
      throws ExecutionException {
    if (!appView.hasClassHierarchy()) {
      assert verifyNoIncompleteCode(groups, executorService);
      return;
    }
    ThreadUtils.processItems(
        groups,
        group -> {
          DexProgramClass target = group.getTarget();
          target.forEachProgramMethodMatching(
              definition ->
                  definition.hasCode()
                      && definition.getCode().isIncompleteHorizontalClassMergerCode(),
              method -> {
                IncompleteHorizontalClassMergerCode code =
                    (IncompleteHorizontalClassMergerCode) method.getDefinition().getCode();
                method
                    .setCode(
                        code.toCfCode(
                            appView.withClassHierarchy(), method, horizontalClassMergerGraphLens),
                        appView);
              });
        },
        executorService);
  }

  private boolean verifyNoIncompleteCode(
      Collection<MergeGroup> groups, ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        groups,
        group -> {
          assert !group
                  .getTarget()
                  .methods(
                      method ->
                          method.hasCode()
                              && method.getCode().isIncompleteHorizontalClassMergerCode())
                  .iterator()
                  .hasNext()
              : "Expected no incomplete code";
        },
        executorService);
    return true;
  }

  private DexApplication getNewApplication(HorizontallyMergedClasses mergedClasses) {
    // In the second round of class merging, we must forcefully remove the merged classes from the
    // application, since we won't run tree shaking before writing the application.
    if (mode.isInitial()) {
      return appView.app();
    } else {
      return appView
          .app()
          .builder()
          .removeProgramClasses(
              clazz -> mergedClasses.hasBeenMergedIntoDifferentType(clazz.getType()))
          .build();
    }
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private List<MergeGroup> getInitialGroups() {
    MergeGroup initialClassGroup = new MergeGroup();
    MergeGroup initialInterfaceGroup = new MergeGroup();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (clazz.isInterface()) {
        initialInterfaceGroup.add(clazz);
      } else {
        initialClassGroup.add(clazz);
      }
    }
    List<MergeGroup> initialGroups = new LinkedList<>();
    initialGroups.add(initialClassGroup);
    initialGroups.add(initialInterfaceGroup);
    initialGroups.removeIf(MergeGroup::isTrivial);
    return initialGroups;
  }

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private List<ClassMerger> initializeClassMergers(
      IRCodeProvider codeProvider,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Collection<MergeGroup> groups) {
    List<ClassMerger> classMergers = new ArrayList<>(groups.size());
    for (MergeGroup group : groups) {
      assert group.isNonTrivial();
      assert group.hasInstanceFieldMap();
      assert group.hasTarget();
      classMergers.add(
          new ClassMerger.Builder(appView, codeProvider, group, mode).build(lensBuilder));
    }
    appView.dexItemFactory().clearTypeElementsCache();
    return classMergers;
  }

  /** Merges all class groups using {@link ClassMerger}. */
  private PrunedItems applyClassMergers(
      Collection<ClassMerger> classMergers,
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
    for (ClassMerger merger : classMergers) {
      merger.mergeGroup(
          profileCollectionAdditions,
          prunedItemsBuilder,
          syntheticArgumentClass,
          syntheticInitializerConverterBuilder,
          virtuallyMergedMethodsKeepInfoConsumer);
    }
    return prunedItemsBuilder.build();
  }

  /**
   * Fix all references to merged classes using the {@link TreeFixer}. Construct a graph lens
   * containing all changes performed by horizontal class merging.
   */
  private HorizontalClassMergerGraphLens createLens(
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Mode mode,
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass) {
    return new TreeFixer(
            appView,
            mergedClasses,
            lensBuilder,
            mode,
            profileCollectionAdditions,
            syntheticArgumentClass)
        .fixupTypeReferences();
  }

  private static boolean verifyNoCyclesInInterfaceHierarchies(
      AppView<?> appView, Collection<MergeGroup> groups) {
    for (MergeGroup group : groups) {
      if (group.isClassGroup()) {
        continue;
      }
      assert appView.hasClassHierarchy();
      DexProgramClass interfaceClass = group.getTarget();
      appView
          .withClassHierarchy()
          .appInfo()
          .traverseSuperTypes(
              interfaceClass,
              (superType, subclass, isInterface) -> {
                assert superType != interfaceClass.getType()
                    : "Interface " + interfaceClass.getTypeName() + " inherits from itself";
                return TraversalContinuation.doContinue();
              });
    }
    return true;
  }
}
