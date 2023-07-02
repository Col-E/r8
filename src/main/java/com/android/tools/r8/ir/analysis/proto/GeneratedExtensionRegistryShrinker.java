// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeUtils;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.DefaultTreePrunerConfiguration;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.TreePrunerConfiguration;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/**
 * This optimization is responsible for pruning dead proto extensions.
 *
 * <p>When using proto lite, a registry for all proto extensions is created. The generated extension
 * registry roughly looks as follows:
 *
 * <pre>
 *   class GeneratedExtensionRegistry {
 *     public static GeneratedMessageLite$GeneratedExtension findLiteExtensionByNumber(
 *         MessageLite message, int number) {
 *       ...
 *       switch (...) {
 *         case ...:
 *           return SomeExtension.extensionField;
 *         case ...:
 *           return SomeOtherExtension.extensionField;
 *         ... // Many other cases.
 *         default:
 *           return null;
 *       }
 *     }
 *   }
 * </pre>
 *
 * <p>We consider an extension to be dead if it is only accessed via a static-get instruction inside
 * the GeneratedExtensionRegistry. For such dead extensions, we simply rewrite the static-get
 * instructions inside the GeneratedExtensionRegistry to null. This ensures that the extensions will
 * be removed as a result of tree shaking.
 */
public class GeneratedExtensionRegistryShrinker {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final ProtoReferences references;

  private final Map<DexType, Map<DexField, Mode>> removedExtensionFields = new IdentityHashMap<>();

  GeneratedExtensionRegistryShrinker(
      AppView<AppInfoWithLiveness> appView, ProtoReferences references) {
    assert appView.options().protoShrinking().enableGeneratedExtensionRegistryShrinking;
    this.appView = appView;
    this.options = appView.options();
    this.references = references;
  }

  /**
   * Will be run after tree shaking. This populates the set {@link #removedExtensionFields}. This
   * set is used by the member value propagation, which rewrites all reads of these fields by
   * const-null.
   *
   * <p>For the second round of tree pruning, this method will return a non-default {@link
   * TreePrunerConfiguration} that specifies that all fields that are only referenced from a {@code
   * findLiteExtensionByNumber()} method should be removed. This is safe because we will revisit all
   * of these methods and replace the reads of these fields by null.
   */
  public TreePrunerConfiguration run(Enqueuer.Mode mode) {
    forEachDeadProtoExtensionField(field -> recordDeadProtoExtensionField(field, mode));
    appView.appInfo().getFieldAccessInfoCollection().removeIf((field, info) -> wasRemoved(field));
    return createTreePrunerConfiguration(mode);
  }

  private void recordDeadProtoExtensionField(DexField field, Enqueuer.Mode mode) {
    assert mode.isInitialTreeShaking() || mode.isFinalTreeShaking();
    removedExtensionFields
        .computeIfAbsent(field.getHolderType(), ignore -> new IdentityHashMap<>())
        .put(field, mode);
  }

  private TreePrunerConfiguration createTreePrunerConfiguration(Enqueuer.Mode mode) {
    if (mode.isFinalTreeShaking()) {
      return new DefaultTreePrunerConfiguration() {

        @Override
        public boolean isReachableOrReferencedField(
            AppInfoWithLiveness appInfo, DexEncodedField field) {
          return !wasRemoved(field.getReference())
              && super.isReachableOrReferencedField(appInfo, field);
        }
      };
    }
    return DefaultTreePrunerConfiguration.getInstance();
  }

  /**
   * If {@param method} is a class initializer that initializes a dead proto extension field, then
   * forcefully remove the field assignment and all the code that contributes to the initialization
   * of the value of the field assignment.
   */
  public void rewriteCode(DexEncodedMethod method, IRCode code) {
    if (method.isClassInitializer()
        && removedExtensionFields.containsKey(method.getHolderType())
        && code.metadata().mayHaveStaticPut()) {
      rewriteClassInitializer(code);
    }
  }

  private void rewriteClassInitializer(IRCode code) {
    List<StaticPut> toBeRemoved = new ArrayList<>();
    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      if (wasRemoved(staticPut.getField())) {
        toBeRemoved.add(staticPut);
      }
    }
    for (StaticPut instruction : toBeRemoved) {
      if (!instruction.hasBlock()) {
        // Already removed.
        continue;
      }
      IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(code, instruction);
    }
  }

  public boolean wasRemoved(DexField field) {
    return removedExtensionFields
        .getOrDefault(field.getHolderType(), Collections.emptyMap())
        .containsKey(field);
  }

  public void postOptimizeGeneratedExtensionRegistry(
      IRConverter converter, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    timing.begin("[Proto] Post optimize generated extension registry");
    ProgramMethodSet wave =
        ProgramMethodSet.create(this::forEachMethodThatRequiresPostOptimization);
    MethodProcessorEventConsumer eventConsumer = MethodProcessorEventConsumer.empty();
    OneTimeMethodProcessor methodProcessor =
        OneTimeMethodProcessor.create(wave, eventConsumer, appView);
    methodProcessor.forEachWaveWithExtension(
        (method, methodProcessingContext) ->
            converter.processDesugaredMethod(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                methodProcessor,
                methodProcessingContext,
                MethodConversionOptions.forPostLirPhase(appView)),
        executorService);
    timing.end();
  }

  private void forEachMethodThatRequiresPostOptimization(Consumer<ProgramMethod> consumer) {
    forEachClassInitializerWithRemovedExtensionFields(consumer, Enqueuer.Mode.FINAL_TREE_SHAKING);
    forEachFindLiteExtensionByNumberMethod(consumer);
  }

  private void forEachClassInitializerWithRemovedExtensionFields(
      Consumer<ProgramMethod> consumer, Enqueuer.Mode modeOfInterest) {
    Set<DexType> classesWithRemovedExtensionFieldsInModeOfInterest = Sets.newIdentityHashSet();
    removedExtensionFields
        .values()
        .forEach(
            removedExtensionFieldsForHolder ->
                removedExtensionFieldsForHolder.forEach(
                    (field, mode) -> {
                      if (mode == modeOfInterest) {
                        classesWithRemovedExtensionFieldsInModeOfInterest.add(
                            field.getHolderType());
                      }
                    }));
    classesWithRemovedExtensionFieldsInModeOfInterest.forEach(
        type -> {
          DexProgramClass clazz = asProgramClassOrNull(appView.appInfo().definitionFor(type));
          if (clazz != null && clazz.hasClassInitializer()) {
            consumer.accept(clazz.getProgramClassInitializer());
          }
        });
  }

  private void forEachFindLiteExtensionByNumberMethod(Consumer<ProgramMethod> consumer) {
    appView
        .appInfo()
        .forEachInstantiatedSubType(
            references.extensionRegistryLiteType,
            clazz ->
                clazz.forEachProgramMethodMatching(
                    definition ->
                        references.isFindLiteExtensionByNumberMethod(definition.getReference()),
                    consumer),
            lambda -> {
              assert false;
            });
  }

  public void handleFailedOrUnknownFieldResolution(
      DexField fieldReference, ProgramMethod context, Enqueuer.Mode mode) {
    if (mode.isTreeShaking() && references.isFindLiteExtensionByNumberMethod(context)) {
      recordDeadProtoExtensionField(fieldReference, mode);
    }
  }

  public boolean isDeadProtoExtensionField(DexField fieldReference) {
    AppInfoWithLiveness appInfo = appView.appInfo();
    return isDeadProtoExtensionField(
        appInfo.resolveField(fieldReference),
        appInfo.getFieldAccessInfoCollection(),
        appInfo.getKeepInfo());
  }

  public boolean isDeadProtoExtensionField(
      FieldResolutionResult resolutionResult,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection,
      KeepInfoCollection keepInfo) {
    ProgramField field = resolutionResult.getSingleProgramField();
    return field != null && isDeadProtoExtensionField(field, fieldAccessInfoCollection, keepInfo);
  }

  public boolean isDeadProtoExtensionField(
      ProgramField field,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection,
      KeepInfoCollection keepInfo) {
    if (keepInfo.getFieldInfo(field).isPinned(options)) {
      return false;
    }

    if (field.getReference().type != references.generatedExtensionType) {
      return false;
    }

    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.getReference());
    if (fieldAccessInfo == null) {
      return false;
    }

    // Multiple GeneratedExtensionRegistries exist in Chrome; 1 per feature split.
    return fieldAccessInfo.isReadOnlyInMethodSatisfying(
        references::isFindLiteExtensionByNumberMethod);
  }

  private void forEachDeadProtoExtensionField(Consumer<DexField> consumer) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(
        info -> {
          DexField field = info.getField();
          if (isDeadProtoExtensionField(field)) {
            consumer.accept(field);
          }
        });
  }
}
