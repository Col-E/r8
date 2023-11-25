// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public abstract class ClassConverter {

  protected final AppView<?> appView;
  private final PrimaryD8L8IRConverter converter;
  private final D8MethodProcessor methodProcessor;
  private final InterfaceProcessor interfaceProcessor;

  ClassConverter(
      AppView<?> appView,
      PrimaryD8L8IRConverter converter,
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor) {
    this.appView = appView;
    this.converter = converter;
    this.methodProcessor = methodProcessor;
    this.interfaceProcessor = interfaceProcessor;
  }

  public static ClassConverter create(
      AppView<?> appView,
      PrimaryD8L8IRConverter converter,
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor) {
    return appView.options().desugarSpecificOptions().allowAllDesugaredInput
        ? new LibraryDesugaredClassConverter(
            appView, converter, methodProcessor, interfaceProcessor)
        : new DefaultClassConverter(appView, converter, methodProcessor, interfaceProcessor);
  }

  public ClassConverterResult convertClasses(ExecutorService executorService)
      throws ExecutionException {
    ClassConverterResult.Builder resultBuilder = ClassConverterResult.builder();
    internalConvertClasses(resultBuilder, executorService);
    notifyAllClassesConverted();
    return resultBuilder.build();
  }

  private static Deque<List<DexProgramClass>> getDeterministicNestWaves(
      Collection<DexProgramClass> classes) {
    Map<DexType, List<DexProgramClass>> nestGroups = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      if (clazz.isInANest()) {
        nestGroups.computeIfAbsent(clazz.getNestHost(), k -> new ArrayList<>()).add(clazz);
      }
    }
    if (nestGroups.isEmpty()) {
      return ImmutableDeque.of();
    }
    int maxGroupSize = 0;
    for (List<DexProgramClass> members : nestGroups.values()) {
      maxGroupSize = Math.max(maxGroupSize, members.size());
      members.sort(Comparator.comparing(DexClass::getType));
    }
    Deque<List<DexProgramClass>> processingList = new ArrayDeque<>(maxGroupSize);
    for (int i = 0; i < maxGroupSize; i++) {
      List<DexProgramClass> wave = new ArrayList<>(nestGroups.size());
      final int index = i;
      MapUtils.removeIf(
          nestGroups,
          (host, members) -> {
            wave.add(members.get(index));
            return index + 1 == members.size();
          });
      processingList.add(wave);
    }
    return processingList;
  }

  private static List<DexProgramClass> filterOutClassesInNests(
      Collection<DexProgramClass> classes) {
    List<DexProgramClass> filtered = new ArrayList<>(classes.size());
    for (DexProgramClass clazz : classes) {
      if (!clazz.isInANest()) {
        filtered.add(clazz);
      }
    }
    return filtered;
  }

  private void internalConvertClasses(
      ClassConverterResult.Builder resultBuilder, ExecutorService executorService)
      throws ExecutionException {
    Collection<DexProgramClass> classes = appView.appInfo().classes();
    ProfileCollectionAdditions profileCollectionAdditions =
        methodProcessor.getProfileCollectionAdditions();
    CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer =
        CfClassSynthesizerDesugaringEventConsumer.createForD8(appView, profileCollectionAdditions);
    converter.classSynthesisDesugaring(executorService, classSynthesizerEventConsumer);
    if (!classSynthesizerEventConsumer.getSynthesizedClasses().isEmpty()) {
      classes =
          ImmutableList.<DexProgramClass>builder()
              .addAll(classes)
              .addAll(classSynthesizerEventConsumer.getSynthesizedClasses())
              .build();
    }

    CfInstructionDesugaringEventConsumer instructionDesugaringEventConsumerForPrepareStep =
        CfInstructionDesugaringEventConsumer.createForD8(
            appView, profileCollectionAdditions, resultBuilder, methodProcessor);
    converter.prepareDesugaring(instructionDesugaringEventConsumerForPrepareStep, executorService);
    assert instructionDesugaringEventConsumerForPrepareStep.verifyNothingToFinalize();

    // When adding nest members to the wave we must do so deterministically.
    Deque<List<DexProgramClass>> nestProcessingWaves = getDeterministicNestWaves(classes);
    Collection<DexProgramClass> wave;
    if (nestProcessingWaves.isEmpty()) {
      wave = classes;
    } else {
      List<DexProgramClass> firstWave = filterOutClassesInNests(classes);
      firstWave.addAll(nestProcessingWaves.removeFirst());
      wave = firstWave;
    }

    while (!wave.isEmpty()) {
      // TODO(b/179755192): Avoid marking classes as scheduled by building up waves of methods.
      for (DexProgramClass clazz : wave) {
        methodProcessor.addScheduled(clazz);
      }

      CfInstructionDesugaringEventConsumer instructionDesugaringEventConsumerForWave =
          CfInstructionDesugaringEventConsumer.createForD8(
              appView, profileCollectionAdditions, resultBuilder, methodProcessor);

      // Process the wave and wait for all IR processing to complete.
      methodProcessor.newWave();
      checkWaveDeterminism(wave);
      ThreadUtils.processItems(
          wave,
          clazz -> convertClass(clazz, instructionDesugaringEventConsumerForWave),
          appView.options().getThreadingModule(),
          executorService);
      methodProcessor.awaitMethodProcessing();

      // Finalize the desugaring of the processed classes. This may require processing (and
      // reprocessing) of some methods.
      List<ProgramMethod> needsProcessing =
          instructionDesugaringEventConsumerForWave.finalizeDesugaring();
      if (!needsProcessing.isEmpty()) {
        // Create a new processor context to ensure unique method processing contexts.
        methodProcessor.newWave();

        // Process the methods that require reprocessing. These are all simple bridge methods and
        // should therefore not lead to additional desugaring.
        ThreadUtils.processItems(
            needsProcessing,
            method -> {
              DexEncodedMethod definition = method.getDefinition();
              if (definition.isProcessed()) {
                definition.markNotProcessed();
              }
              methodProcessor.processMethod(method, instructionDesugaringEventConsumerForWave);
              if (interfaceProcessor != null) {
                interfaceProcessor.processMethod(method, instructionDesugaringEventConsumerForWave);
              }
            },
            appView.options().getThreadingModule(),
            executorService);

        // Verify there is nothing to finalize once method processing finishes.
        methodProcessor.awaitMethodProcessing();
        assert instructionDesugaringEventConsumerForWave.verifyNothingToFinalize();
      }

      if (!nestProcessingWaves.isEmpty()) {
        wave = nestProcessingWaves.removeFirst();
      } else {
        break;
      }
    }
  }

  private void checkWaveDeterminism(Collection<DexProgramClass> wave) {
    appView
        .options()
        .testing
        .checkDeterminism(
            checker -> {
              // There is no constraint on the order within the wave so sort them to have a
              // deterministic log.
              List<DexProgramClass> sorted = new ArrayList<>(wave);
              sorted.sort(Comparator.comparing(DexClass::getType));
              checker.accept(
                  lineCallback -> {
                    for (DexProgramClass clazz : sorted) {
                      lineCallback.onLine(clazz.getType().toDescriptorString());
                    }
                  });
            });
  }

  abstract void convertClass(
      DexProgramClass clazz, CfInstructionDesugaringEventConsumer desugaringEventConsumer);

  void convertMethods(
      DexProgramClass clazz, CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
    converter.convertMethods(clazz, desugaringEventConsumer, methodProcessor, interfaceProcessor);
  }

  abstract void notifyAllClassesConverted();

  static class DefaultClassConverter extends ClassConverter {

    DefaultClassConverter(
        AppView<?> appView,
        PrimaryD8L8IRConverter converter,
        D8MethodProcessor methodProcessor,
        InterfaceProcessor interfaceProcessor) {
      super(appView, converter, methodProcessor, interfaceProcessor);
    }

    @Override
    void convertClass(
        DexProgramClass clazz, CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
      convertMethods(clazz, desugaringEventConsumer);
    }

    @Override
    void notifyAllClassesConverted() {
      // Intentionally empty.
    }
  }

  static class LibraryDesugaredClassConverter extends ClassConverter {

    private final Set<DexType> alreadyLibraryDesugared = SetUtils.newConcurrentHashSet();

    LibraryDesugaredClassConverter(
        AppView<?> appView,
        PrimaryD8L8IRConverter converter,
        D8MethodProcessor methodProcessor,
        InterfaceProcessor interfaceProcessor) {
      super(appView, converter, methodProcessor, interfaceProcessor);
    }

    @Override
    void convertClass(
        DexProgramClass clazz, CfInstructionDesugaringEventConsumer desugaringEventConsumer) {
      // Classes which has already been through library desugaring will not go through IR
      // processing again.
      LibraryDesugaredChecker libraryDesugaredChecker = new LibraryDesugaredChecker(appView);
      if (libraryDesugaredChecker.isClassLibraryDesugared(clazz)) {
        alreadyLibraryDesugared.add(clazz.getType());
      } else {
        convertMethods(clazz, desugaringEventConsumer);
      }
    }

    @Override
    void notifyAllClassesConverted() {
      appView.setAlreadyLibraryDesugared(alreadyLibraryDesugared);
    }
  }
}
