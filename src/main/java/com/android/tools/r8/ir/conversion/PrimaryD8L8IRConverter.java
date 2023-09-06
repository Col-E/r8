// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor.ExcludeDexResources;
import static com.android.tools.r8.ir.desugar.lambda.D8LambdaDesugaring.rewriteEnclosingLambdaMethodAttributes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformerEventConsumer;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceApplicationRewriter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.ir.desugar.itf.L8InnerOuterAttributeEraser;
import com.android.tools.r8.ir.desugar.lambda.LambdaDeserializationMethodRemover;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class PrimaryD8L8IRConverter extends IRConverter {

  private final Timing timing;

  public PrimaryD8L8IRConverter(AppView<AppInfo> appView, Timing timing) {
    super(appView);
    this.timing = timing;
  }

  @SuppressWarnings("BadImport")
  public void convert(AppView<AppInfo> appView, ExecutorService executorService)
      throws ExecutionException {
    LambdaDeserializationMethodRemover.run(appView);
    workaroundAbstractMethodOnNonAbstractClassVerificationBug(executorService);
    DexApplication application = appView.appInfo().app();
    ProfileCollectionAdditions profileCollectionAdditions =
        ProfileCollectionAdditions.create(appView);
    D8MethodProcessor methodProcessor =
        new D8MethodProcessor(profileCollectionAdditions, this, executorService);
    InterfaceProcessor interfaceProcessor = InterfaceProcessor.create(appView);

    timing.begin("IR conversion");

    convertClasses(methodProcessor, interfaceProcessor, executorService);

    reportNestDesugarDependencies();
    clearNestAttributes();

    if (instanceInitializerOutliner != null) {
      processSimpleSynthesizeMethods(
          instanceInitializerOutliner.getSynthesizedMethods(), executorService);
    }
    if (assertionErrorTwoArgsConstructorRewriter != null) {
      processSimpleSynthesizeMethods(
          assertionErrorTwoArgsConstructorRewriter.getSynthesizedMethods(), executorService);
    }

    application = commitPendingSyntheticItems(appView, application);

    postProcessingDesugaringForD8(methodProcessor, interfaceProcessor, executorService);

    application = commitPendingSyntheticItems(appView, application);

    // Build a new application with jumbo string info,
    Builder<?> builder = application.builder();

    if (appView.options().isDesugaredLibraryCompilation()) {
      new EmulatedInterfaceApplicationRewriter(appView).rewriteApplication(builder);
      new L8InnerOuterAttributeEraser(appView).run();
    }

    processCovariantReturnTypeAnnotations(builder, profileCollectionAdditions);

    timing.end();

    application = builder.build();
    appView.setAppInfo(
        new AppInfo(
            appView.appInfo().getSyntheticItems().commit(application),
            appView.appInfo().getMainDexInfo()));

    profileCollectionAdditions.commit(appView);
  }

  void convertMethods(
      DexProgramClass clazz,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor) {
    // When converting all methods on a class always convert <clinit> first.
    ProgramMethod classInitializer = clazz.getProgramClassInitializer();

    // TODO(b/179755192): We currently need to copy the class' methods, to avoid a
    //  ConcurrentModificationException from the insertion of methods due to invoke-special
    //  desugaring. By building up waves of methods in the class converter, we would not need to
    //  iterate the methods of a class during while its methods are being processed, which avoids
    //  the need to copy the method list.
    List<ProgramMethod> methods = ListUtils.newArrayList(clazz::forEachProgramMethod);
    if (classInitializer != null) {
      methodProcessor.processMethod(classInitializer, desugaringEventConsumer);
    }

    for (ProgramMethod method : methods) {
      if (!method.getDefinition().isClassInitializer()) {
        methodProcessor.processMethod(method, desugaringEventConsumer);
        if (interfaceProcessor != null) {
          interfaceProcessor.processMethod(method, desugaringEventConsumer);
        }
      }
    }

    // The class file version is downgraded after compilation. Some of the desugaring might need
    // the initial class file version to determine how far a method can be downgraded.
    if (options.isGeneratingClassFiles() && clazz.hasClassFileVersion()) {
      clazz.downgradeInitialClassFileVersion(
          appView.options().classFileVersionAfterDesugaring(clazz.getInitialClassFileVersion()));
    }
  }

  void convertMethod(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexEncodedMethod definition = method.getDefinition();
    if (options.isGeneratingClassFiles() && definition.hasClassFileVersion()) {
      definition.downgradeClassFileVersion(
          appView.options().classFileVersionAfterDesugaring(definition.getClassFileVersion()));
    }
    if (definition.getCode() == null) {
      return;
    }
    if (!options.methodMatchesFilter(definition)) {
      return;
    }
    checkPrefixMerging(method);
    if (options.isGeneratingClassFiles()
        || !(options.passthroughDexCode && definition.getCode().isDexCode())) {
      // We do not process in call graph order, so anything could be a leaf.
      rewriteNonDesugaredCode(
          method,
          desugaringEventConsumer,
          simpleOptimizationFeedback,
          methodProcessor,
          methodProcessingContext);
    } else {
      assert definition.getCode().isDexCode();
    }
  }

  private void checkPrefixMerging(ProgramMethod method) {
    if (!appView.options().enableNeverMergePrefixes) {
      return;
    }
    DexString descriptor = method.getHolderType().descriptor;
    for (DexString neverMergePrefix : neverMerge.getPrefixes()) {
      if (descriptor.startsWith(neverMergePrefix)) {
        seenNeverMergePrefix.getAndSet(true);
      } else {
        for (DexString exceptionPrefix : neverMerge.getExceptionPrefixes()) {
          if (!descriptor.startsWith(exceptionPrefix)) {
            seenNotNeverMergePrefix.getAndSet(true);
            break;
          }
        }
      }
      // Don't mix.
      // TODO(b/168001352): Consider requiring that no 'never merge' prefix is ever seen as a
      //  passthrough object.
      if (seenNeverMergePrefix.get() && seenNotNeverMergePrefix.get()) {
        throw new CompilationError(getOrComputeConflictingPrefixesErrorMessage(neverMergePrefix));
      }
    }
  }

  private synchronized String getOrComputeConflictingPrefixesErrorMessage(
      DexString neverMergePrefix) {
    if (conflictingPrefixesErrorMessage != null) {
      return conflictingPrefixesErrorMessage;
    }
    StringBuilder message = new StringBuilder();
    message
        .append("Merging DEX file containing classes with prefix")
        .append(neverMerge.getPrefixes().size() > 1 ? "es " : " ");
    for (int i = 0; i < neverMerge.getPrefixes().size(); i++) {
      message
          .append("'")
          .append(neverMerge.getPrefixes().get(i).toString().substring(1).replace('/', '.'))
          .append("'")
          .append(i < neverMerge.getPrefixes().size() - 1 ? ", " : "");
    }
    if (!neverMerge.getExceptionPrefixes().isEmpty()) {
      message
          .append(" with other classes, except classes with prefix")
          .append(neverMerge.getExceptionPrefixes().size() > 1 ? "es " : " ");
      for (int i = 0; i < neverMerge.getExceptionPrefixes().size(); i++) {
        message
            .append("'")
            .append(
                neverMerge.getExceptionPrefixes().get(i).toString().substring(1).replace('/', '.'))
            .append("'")
            .append(i < neverMerge.getExceptionPrefixes().size() - 1 ? ", " : "");
      }
      message.append(",");
    } else {
      message.append(" with classes with any other prefixes");
    }
    message.append(" is not allowed: ");
    boolean first = true;
    int limit = 11;
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (!clazz.type.descriptor.startsWith(neverMergePrefix)) {
        if (hasExceptionPrefix(clazz)) {
          continue;
        }
        if (limit-- < 0) {
          message.append("..");
          break;
        }
        if (first) {
          first = false;
        } else {
          message.append(", ");
        }
        message.append(clazz.type);
      }
    }
    message.append(".");
    conflictingPrefixesErrorMessage = message.toString();
    return conflictingPrefixesErrorMessage;
  }

  private boolean hasExceptionPrefix(DexProgramClass clazz) {
    for (DexString exceptionPrefix : neverMerge.getExceptionPrefixes()) {
      if (clazz.type.descriptor.startsWith(exceptionPrefix)) {
        return true;
      }
    }
    return false;
  }

  void classSynthesisDesugaring(
      ExecutorService executorService,
      CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer)
      throws ExecutionException {
    CfClassSynthesizerDesugaringCollection.create(appView)
        .synthesizeClasses(executorService, classSynthesizerEventConsumer);
  }

  private DexApplication commitPendingSyntheticItems(
      AppView<AppInfo> appView, DexApplication application) {
    if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      appView.setAppInfo(
          new AppInfo(
              appView.appInfo().getSyntheticItems().commit(application),
              appView.appInfo().getMainDexInfo()));
      application = appView.appInfo().app();
    }
    return application;
  }

  private void convertClasses(
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor,
      ExecutorService executorService)
      throws ExecutionException {
    ClassConverterResult classConverterResult =
        ClassConverter.create(appView, this, methodProcessor, interfaceProcessor)
            .convertClasses(executorService);

    // The synthesis of accessibility bridges in nest based access desugaring will schedule and
    // await the processing of synthesized methods.
    synthesizeBridgesForNestBasedAccessesOnClasspath(methodProcessor, executorService);

    // There should be no outstanding method processing.
    methodProcessor.verifyNoPendingMethodProcessing();

    rewriteEnclosingLambdaMethodAttributes(
        appView, classConverterResult.getForcefullyMovedLambdaMethods());

    instructionDesugaring.withDesugaredLibraryAPIConverter(
        DesugaredLibraryAPIConverter::generateTrackingWarnings);
  }

  private void postProcessingDesugaringForD8(
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor,
      ExecutorService executorService)
      throws ExecutionException {
    CfPostProcessingDesugaringEventConsumer eventConsumer =
        CfPostProcessingDesugaringEventConsumer.createForD8(
            appView,
            methodProcessor.getProfileCollectionAdditions(),
            methodProcessor,
            instructionDesugaring);
    methodProcessor.newWave();
    InterfaceMethodProcessorFacade interfaceDesugaring =
        instructionDesugaring.getInterfaceMethodPostProcessingDesugaringD8(
            ExcludeDexResources, interfaceProcessor);
    CfPostProcessingDesugaringCollection.create(appView, interfaceDesugaring, m -> true)
        .postProcessingDesugaring(appView.appInfo().classes(), eventConsumer, executorService);
    methodProcessor.awaitMethodProcessing();
    eventConsumer.finalizeDesugaring();
  }

  void prepareDesugaring(
      CfInstructionDesugaringEventConsumer desugaringEventConsumer, ExecutorService executorService)
      throws ExecutionException {
    // Prepare desugaring by collecting all the synthetic methods required on program classes.
    ProgramAdditions programAdditions = new ProgramAdditions();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          clazz.forEachProgramMethodMatching(
              method -> method.hasCode() && method.getCode().isCfCode(),
              method ->
                  instructionDesugaring.prepare(method, desugaringEventConsumer, programAdditions));
        },
        executorService);
    programAdditions.apply(executorService);
  }

  @SuppressWarnings("BadImport")
  private void processCovariantReturnTypeAnnotations(
      Builder<?> builder, ProfileCollectionAdditions profileCollectionAdditions) {
    if (covariantReturnTypeAnnotationTransformer != null) {
      covariantReturnTypeAnnotationTransformer.process(
          builder,
          CovariantReturnTypeAnnotationTransformerEventConsumer.create(profileCollectionAdditions));
    }
  }

  Timing rewriteNonDesugaredCode(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    return ExceptionUtils.withOriginAndPositionAttachmentHandler(
        method.getOrigin(),
        new MethodPosition(method.getReference().asMethodReference()),
        () ->
            rewriteNonDesugaredCodeInternal(
                method,
                desugaringEventConsumer,
                feedback,
                methodProcessor,
                methodProcessingContext));
  }

  @SuppressWarnings("UnusedVariable")
  private Timing rewriteNonDesugaredCodeInternal(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    boolean didDesugar = desugar(method, desugaringEventConsumer, methodProcessingContext);
    return rewriteDesugaredCodeInternal(
        method,
        feedback,
        methodProcessor,
        methodProcessingContext,
        MethodConversionOptions.forD8(appView));
  }

  private boolean desugar(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      MethodProcessingContext methodProcessingContext) {
    // Due to some mandatory desugarings, we need to run desugaring even if desugaring is disabled.
    if (!method.getDefinition().getCode().isCfCode()) {
      return false;
    }
    instructionDesugaring.scan(method, desugaringEventConsumer);
    if (instructionDesugaring.needsDesugaring(method)) {
      instructionDesugaring.desugar(method, methodProcessingContext, desugaringEventConsumer);
      return true;
    }
    return false;
  }

  private void clearNestAttributes() {
    instructionDesugaring.withD8NestBasedAccessDesugaring(
        D8NestBasedAccessDesugaring::clearNestAttributes);
  }

  private void reportNestDesugarDependencies() {
    instructionDesugaring.withD8NestBasedAccessDesugaring(
        D8NestBasedAccessDesugaring::reportDesugarDependencies);
  }

  private void synthesizeBridgesForNestBasedAccessesOnClasspath(
      D8MethodProcessor methodProcessor, ExecutorService executorService)
      throws ExecutionException {
    instructionDesugaring.withD8NestBasedAccessDesugaring(
        d8NestBasedAccessDesugaring ->
            d8NestBasedAccessDesugaring.synthesizeBridgesForNestBasedAccessesOnClasspath(
                methodProcessor, executorService));
    methodProcessor.awaitMethodProcessing();
  }
}
