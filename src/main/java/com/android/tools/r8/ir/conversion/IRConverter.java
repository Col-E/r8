// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter.Flavor.ExcludeDexResources;
import static com.android.tools.r8.ir.desugar.lambda.D8LambdaDesugaring.rewriteEnclosingLambdaMethodAttributes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.fieldaccess.FieldAccessAnalysis;
import com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.InstanceFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringEventConsumer.D8CfInstructionDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CfPostProcessingDesugaringEventConsumer.D8CfPostProcessingDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformer;
import com.android.tools.r8.ir.desugar.ProgramAdditions;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceApplicationRewriter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodProcessorFacade;
import com.android.tools.r8.ir.desugar.itf.InterfaceProcessor;
import com.android.tools.r8.ir.desugar.itf.L8InnerOuterAttributeEraser;
import com.android.tools.r8.ir.desugar.lambda.LambdaDeserializationMethodRemover;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.optimize.AssertionErrorTwoArgsConstructorRewriter;
import com.android.tools.r8.ir.optimize.AssertionsRewriter;
import com.android.tools.r8.ir.optimize.AssumeInserter;
import com.android.tools.r8.ir.optimize.CheckNotNullConverter;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.Devirtualizer;
import com.android.tools.r8.ir.optimize.DynamicTypeOptimization;
import com.android.tools.r8.ir.optimize.IdempotentFunctionCallCanonicalizer;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InstanceInitializerOutliner;
import com.android.tools.r8.ir.optimize.NaturalIntLoopRemover;
import com.android.tools.r8.ir.optimize.RedundantFieldLoadAndStoreElimination;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriter;
import com.android.tools.r8.ir.optimize.classinliner.ClassInliner;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer;
import com.android.tools.r8.ir.optimize.enums.EnumValueOptimizer;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoCollector;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.membervaluepropagation.D8MemberValuePropagation;
import com.android.tools.r8.ir.optimize.membervaluepropagation.MemberValuePropagation;
import com.android.tools.r8.ir.optimize.membervaluepropagation.R8MemberValuePropagation;
import com.android.tools.r8.ir.optimize.outliner.Outliner;
import com.android.tools.r8.ir.optimize.string.StringBuilderAppendOptimizer;
import com.android.tools.r8.ir.optimize.string.StringOptimizer;
import com.android.tools.r8.lightir.IR2LIRConverter;
import com.android.tools.r8.lightir.LIR2IRConverter;
import com.android.tools.r8.lightir.LIRCode;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagator;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorIROptimizer;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.LibraryMethodOverrideAnalysis;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.NeverMergeGroup;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class IRConverter {

  public final AppView<?> appView;

  private final Timing timing;
  public final Outliner outliner;
  private final ClassInitializerDefaultsOptimization classInitializerDefaultsOptimization;
  private final CfInstructionDesugaringCollection instructionDesugaring;
  private final FieldAccessAnalysis fieldAccessAnalysis;
  private final LibraryMethodOverrideAnalysis libraryMethodOverrideAnalysis;
  private final StringOptimizer stringOptimizer;
  private final IdempotentFunctionCallCanonicalizer idempotentFunctionCallCanonicalizer;
  private final ClassInliner classInliner;
  private final InternalOptions options;
  private final CfgPrinter printer;
  public final CodeRewriter codeRewriter;
  public final AssertionErrorTwoArgsConstructorRewriter assertionErrorTwoArgsConstructorRewriter;
  private final NaturalIntLoopRemover naturalIntLoopRemover = new NaturalIntLoopRemover();
  public final MemberValuePropagation<?> memberValuePropagation;
  private final LensCodeRewriter lensCodeRewriter;
  private final Inliner inliner;
  private final IdentifierNameStringMarker identifierNameStringMarker;
  private final Devirtualizer devirtualizer;
  private final CovariantReturnTypeAnnotationTransformer covariantReturnTypeAnnotationTransformer;
  private final StringSwitchRemover stringSwitchRemover;
  private final TypeChecker typeChecker;
  private final ServiceLoaderRewriter serviceLoaderRewriter;
  private final EnumValueOptimizer enumValueOptimizer;
  private final EnumUnboxer enumUnboxer;
  private final InstanceInitializerOutliner instanceInitializerOutliner;

  public final AssumeInserter assumeInserter;
  private final DynamicTypeOptimization dynamicTypeOptimization;

  final AssertionsRewriter assertionsRewriter;
  public final DeadCodeRemover deadCodeRemover;

  private final MethodOptimizationInfoCollector methodOptimizationInfoCollector;

  private final OptimizationFeedbackDelayed delayedOptimizationFeedback =
      new OptimizationFeedbackDelayed();
  private final OptimizationFeedback simpleOptimizationFeedback =
      OptimizationFeedbackSimple.getInstance();
  private DexString highestSortingString;

  private List<Action> onWaveDoneActions = null;
  private final Set<DexMethod> prunedMethodsInWave = Sets.newIdentityHashSet();

  private final NeverMergeGroup<DexString> neverMerge;
  // Use AtomicBoolean to satisfy TSAN checking (see b/153714743).
  AtomicBoolean seenNotNeverMergePrefix = new AtomicBoolean();
  AtomicBoolean seenNeverMergePrefix = new AtomicBoolean();

  /**
   * The argument `appView` is used to determine if whole program optimizations are allowed or not
   * (i.e., whether we are running R8). See {@link AppView#enableWholeProgramOptimizations()}.
   */
  public IRConverter(AppView<?> appView, Timing timing, CfgPrinter printer) {
    assert appView.options() != null;
    assert appView.options().programConsumer != null;
    assert timing != null;
    this.timing = timing;
    this.appView = appView;
    this.options = appView.options();
    this.printer = printer;
    this.codeRewriter = new CodeRewriter(appView);
    this.assertionErrorTwoArgsConstructorRewriter =
        new AssertionErrorTwoArgsConstructorRewriter(appView);
    this.classInitializerDefaultsOptimization =
        new ClassInitializerDefaultsOptimization(appView, this);
    this.stringOptimizer = new StringOptimizer(appView);
    this.deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
    this.assertionsRewriter = new AssertionsRewriter(appView);
    this.idempotentFunctionCallCanonicalizer = new IdempotentFunctionCallCanonicalizer(appView);
    this.neverMerge =
        options.neverMerge.map(
            prefix ->
                options.itemFactory.createString(
                    "L" + DescriptorUtils.getPackageBinaryNameFromJavaType(prefix)));
    if (options.isDesugaredLibraryCompilation()) {
      // Specific L8 Settings, performs all desugaring including L8 specific desugaring.
      //
      // The following desugarings are required for L8 specific desugaring:
      // - DesugaredLibraryRetargeter for retarget core library members.
      // - InterfaceMethodRewriter for emulated interfaces,
      // - Lambda desugaring since interface method desugaring does not support invoke-custom
      //   rewriting,
      // - DesugaredLibraryAPIConverter to duplicate APIs.
      //
      // The following desugaring are present so all desugaring is performed cf to cf in L8, and
      // the second L8 phase can just run with Desugar turned off:
      // - InterfaceMethodRewriter for non L8 specific interface method desugaring,
      // - twr close resource desugaring,
      // - nest based access desugaring,
      // - invoke-special desugaring.
      assert options.desugarState.isOn();
      this.instructionDesugaring =
          CfInstructionDesugaringCollection.create(appView, appView.apiLevelCompute());
      this.covariantReturnTypeAnnotationTransformer = null;
      this.dynamicTypeOptimization = null;
      this.classInliner = null;
      this.fieldAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.outliner = Outliner.empty();
      this.memberValuePropagation = null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.typeChecker = null;
      this.stringSwitchRemover = null;
      this.serviceLoaderRewriter = null;
      this.methodOptimizationInfoCollector = null;
      this.enumValueOptimizer = null;
      this.enumUnboxer = EnumUnboxer.empty();
      this.assumeInserter = null;
      this.instanceInitializerOutliner = null;
      return;
    }
    this.instructionDesugaring =
        appView.enableWholeProgramOptimizations()
            ? CfInstructionDesugaringCollection.empty()
            : CfInstructionDesugaringCollection.create(appView, appView.apiLevelCompute());
    this.covariantReturnTypeAnnotationTransformer =
        options.processCovariantReturnTypeAnnotations
            ? new CovariantReturnTypeAnnotationTransformer(this, appView.dexItemFactory())
            : null;
    if (appView.options().desugarState.isOn()
        && appView.options().apiModelingOptions().enableOutliningOfMethods) {
      this.instanceInitializerOutliner = new InstanceInitializerOutliner(appView);
    } else {
      this.instanceInitializerOutliner = null;
    }
    if (appView.enableWholeProgramOptimizations()) {
      assert appView.appInfo().hasLiveness();
      assert appView.rootSet() != null;
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      assumeInserter = new AssumeInserter(appViewWithLiveness);
      this.classInliner =
          options.enableClassInlining && options.inlinerOptions().enableInlining
              ? new ClassInliner()
              : null;
      this.dynamicTypeOptimization = new DynamicTypeOptimization(appViewWithLiveness);
      this.fieldAccessAnalysis = new FieldAccessAnalysis(appViewWithLiveness);
      this.libraryMethodOverrideAnalysis =
          options.enableTreeShakingOfLibraryMethodOverrides
              ? new LibraryMethodOverrideAnalysis(appViewWithLiveness)
              : null;
      this.enumUnboxer = EnumUnboxer.create(appViewWithLiveness);
      this.lensCodeRewriter = new LensCodeRewriter(appViewWithLiveness, enumUnboxer);
      this.inliner = new Inliner(appViewWithLiveness, this, lensCodeRewriter);
      this.outliner = Outliner.create(appViewWithLiveness);
      this.memberValuePropagation = new R8MemberValuePropagation(appViewWithLiveness);
      this.methodOptimizationInfoCollector =
          new MethodOptimizationInfoCollector(appViewWithLiveness, this);
      if (options.isMinifying()) {
        this.identifierNameStringMarker = new IdentifierNameStringMarker(appViewWithLiveness);
      } else {
        this.identifierNameStringMarker = null;
      }
      this.devirtualizer =
          options.enableDevirtualization ? new Devirtualizer(appViewWithLiveness) : null;
      this.typeChecker = new TypeChecker(appViewWithLiveness, VerifyTypesHelper.create(appView));
      this.serviceLoaderRewriter =
          options.enableServiceLoaderRewriting
              ? new ServiceLoaderRewriter(appViewWithLiveness, appView.apiLevelCompute())
              : null;
      this.enumValueOptimizer =
          options.enableEnumValueOptimization ? new EnumValueOptimizer(appViewWithLiveness) : null;
    } else {
      AppView<AppInfo> appViewWithoutClassHierarchy = appView.withoutClassHierarchy();
      this.assumeInserter = null;
      this.classInliner = null;
      this.dynamicTypeOptimization = null;
      this.fieldAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.outliner = Outliner.empty();
      this.memberValuePropagation =
          options.isGeneratingDex()
              ? new D8MemberValuePropagation(appViewWithoutClassHierarchy)
              : null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.typeChecker = null;
      this.serviceLoaderRewriter = null;
      this.methodOptimizationInfoCollector = null;
      this.enumValueOptimizer = null;
      this.enumUnboxer = EnumUnboxer.empty();
    }
    this.stringSwitchRemover =
        options.isStringSwitchConversionEnabled()
            ? new StringSwitchRemover(appView, identifierNameStringMarker)
            : null;
  }

  /** Create an IR converter for processing methods with full program optimization disabled. */
  public IRConverter(AppView<?> appView, Timing timing) {
    this(appView, timing, null);
  }

  public IRConverter(AppInfo appInfo, Timing timing, CfgPrinter printer) {
    this(AppView.createForD8(appInfo), timing, printer);
  }

  public Inliner getInliner() {
    return inliner;
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

  private void reportNestDesugarDependencies() {
    instructionDesugaring.withD8NestBasedAccessDesugaring(
        D8NestBasedAccessDesugaring::reportDesugarDependencies);
  }

  private void clearNestAttributes() {
    instructionDesugaring.withD8NestBasedAccessDesugaring(
        D8NestBasedAccessDesugaring::clearNestAttributes);
  }

  private void processCovariantReturnTypeAnnotations(Builder<?> builder) {
    if (covariantReturnTypeAnnotationTransformer != null) {
      covariantReturnTypeAnnotationTransformer.process(builder);
    }
  }

  public void convert(AppView<AppInfo> appView, ExecutorService executor)
      throws ExecutionException {
    LambdaDeserializationMethodRemover.run(appView);
    workaroundAbstractMethodOnNonAbstractClassVerificationBug(executor);
    DexApplication application = appView.appInfo().app();
    D8MethodProcessor methodProcessor = new D8MethodProcessor(this, executor);
    InterfaceProcessor interfaceProcessor =
        appView.options().isInterfaceMethodDesugaringEnabled()
            ? new InterfaceProcessor(appView)
            : null;

    timing.begin("IR conversion");

    convertClasses(methodProcessor, interfaceProcessor, executor);

    reportNestDesugarDependencies();
    clearNestAttributes();

    if (instanceInitializerOutliner != null) {
      processSimpleSynthesizeMethods(instanceInitializerOutliner.getSynthesizedMethods(), executor);
    }
    if (assertionErrorTwoArgsConstructorRewriter != null) {
      processSimpleSynthesizeMethods(
          assertionErrorTwoArgsConstructorRewriter.getSynthesizedMethods(), executor);
    }

    application = commitPendingSyntheticItemsD8(appView, application);

    postProcessingDesugaringForD8(methodProcessor, interfaceProcessor, executor);

    application = commitPendingSyntheticItemsD8(appView, application);

    // Build a new application with jumbo string info,
    Builder<?> builder = application.builder().setHighestSortingString(highestSortingString);

    if (appView.options().isDesugaredLibraryCompilation()) {
      new EmulatedInterfaceApplicationRewriter(appView).rewriteApplication(builder);
      new L8InnerOuterAttributeEraser(appView).run();
    }
    processCovariantReturnTypeAnnotations(builder);

    timing.end();

    application = builder.build();
    appView.setAppInfo(
        new AppInfo(
            appView.appInfo().getSyntheticItems().commit(application),
            appView.appInfo().getMainDexInfo()));
  }

  private DexApplication commitPendingSyntheticItemsD8(
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

  private static void commitPendingSyntheticItemsR8(AppView<AppInfoWithLiveness> appView) {
    if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithLiveness(appView.getSyntheticItems().commit(appView.appInfo().app())));
    }
  }

  public void classSynthesisDesugaring(
      ExecutorService executorService,
      CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer)
      throws ExecutionException {
    CfClassSynthesizerDesugaringCollection.create(appView)
        .synthesizeClasses(executorService, classSynthesizerEventConsumer);
  }

  private void postProcessingDesugaringForD8(
      D8MethodProcessor methodProcessor,
      InterfaceProcessor interfaceProcessor,
      ExecutorService executorService)
      throws ExecutionException {
    D8CfPostProcessingDesugaringEventConsumer eventConsumer =
        CfPostProcessingDesugaringEventConsumer.createForD8(methodProcessor, instructionDesugaring);
    methodProcessor.newWave();
    InterfaceMethodProcessorFacade interfaceDesugaring =
        instructionDesugaring.getInterfaceMethodPostProcessingDesugaringD8(
            ExcludeDexResources, interfaceProcessor);
    CfPostProcessingDesugaringCollection.create(appView, interfaceDesugaring)
        .postProcessingDesugaring(
            appView.appInfo().classes(), m -> true, eventConsumer, executorService);
    methodProcessor.awaitMethodProcessing();
    eventConsumer.finalizeDesugaring();
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

  public void prepareDesugaringForD8(ExecutorService executorService) throws ExecutionException {
    // Prepare desugaring by collecting all the synthetic methods required on program classes.
    ProgramAdditions programAdditions = new ProgramAdditions();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          clazz.forEachProgramMethodMatching(
              method -> method.hasCode() && method.getCode().isCfCode(),
              method -> instructionDesugaring.prepare(method, programAdditions));
        },
        executorService);
    programAdditions.apply(executorService);
  }

  void convertMethods(
      DexProgramClass clazz,
      D8CfInstructionDesugaringEventConsumer desugaringEventConsumer,
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
    if (!options.isGeneratingClassFiles()) {
      updateHighestSortingStrings(definition);
    }
  }

  private boolean needsIRConversion(ProgramMethod method) {
    if (method.getDefinition().getCode().isThrowNullCode()) {
      return false;
    }
    if (appView.enableWholeProgramOptimizations()) {
      return true;
    }
    if (options.testing.forceIRForCfToCfDesugar) {
      return true;
    }
    assert method.getDefinition().getCode().isCfCode();
    return !options.isGeneratingClassFiles();
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
                    neverMerge
                        .getExceptionPrefixes()
                        .get(i)
                        .toString()
                        .substring(1)
                        .replace('/', '.'))
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
        throw new CompilationError(message.toString());
      }
    }
  }

  private void workaroundAbstractMethodOnNonAbstractClassVerificationBug(
      ExecutorService executorService) throws ExecutionException {
    if (!options.canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug()) {
      return;
    }
    assert delayedOptimizationFeedback.noUpdatesLeft();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          if (!clazz.isAbstract()) {
            clazz.forEachProgramMethodMatching(
                DexEncodedMethod::isAbstract, method -> method.convertToThrowNullMethod(appView));
          }
        },
        executorService);
  }

  public DexApplication optimize(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    // Desugaring happens in the enqueuer.
    assert instructionDesugaring.isEmpty();

    workaroundAbstractMethodOnNonAbstractClassVerificationBug(executorService);

    // The process is in two phases in general.
    // 1) Subject all DexEncodedMethods to optimization, except some optimizations that require
    //    reprocessing IR code of methods, e.g., outlining, double-inlining, class staticizer, etc.
    //    - a side effect is candidates for those optimizations are identified.
    // 2) Revisit DexEncodedMethods for the collected candidates.

    printPhase("Primary optimization pass");

    GraphLens graphLensForPrimaryOptimizationPass = appView.graphLens();

    // Setup optimizations for the primary optimization pass.
    appView.withArgumentPropagator(
        argumentPropagator -> argumentPropagator.initializeCodeScanner(executorService, timing));
    enumUnboxer.prepareForPrimaryOptimizationPass(graphLensForPrimaryOptimizationPass);
    outliner.prepareForPrimaryOptimizationPass(graphLensForPrimaryOptimizationPass);

    if (fieldAccessAnalysis != null) {
      fieldAccessAnalysis.fieldAssignmentTracker().initialize();
    }

    // Process the application identifying outlining candidates.
    OptimizationFeedbackDelayed feedback = delayedOptimizationFeedback;
    PostMethodProcessor.Builder postMethodProcessorBuilder =
        new PostMethodProcessor.Builder(graphLensForPrimaryOptimizationPass);
    {
      timing.begin("Build primary method processor");
      PrimaryMethodProcessor primaryMethodProcessor =
          PrimaryMethodProcessor.create(appView.withLiveness(), executorService, timing);
      timing.end();
      timing.begin("IR conversion phase 1");
      assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
      primaryMethodProcessor.forEachMethod(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, feedback, primaryMethodProcessor, methodProcessingContext),
          this::waveStart,
          this::waveDone,
          timing,
          executorService);
      lastWaveDone(postMethodProcessorBuilder, executorService);
      assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
      timing.end();
    }

    // The field access info collection is not maintained during IR processing.
    appView.appInfo().withLiveness().getFieldAccessInfoCollection().destroyAccessContexts();

    // Assure that no more optimization feedback left after primary processing.
    assert feedback.noUpdatesLeft();
    appView.setAllCodeProcessed();

    // All the code has been processed so the rewriting required by the lenses is done everywhere,
    // we clear lens code rewriting so that the lens rewriter can be re-executed in phase 2 if new
    // lenses with code rewriting are added.
    appView.clearCodeRewritings();

    // Commit synthetics from the primary optimization pass.
    commitPendingSyntheticItemsR8(appView);

    // Post processing:
    //   1) Second pass for methods whose collected call site information become more precise.
    //   2) Second inlining pass for dealing with double inline callers.
    printPhase("Post optimization pass");

    // Analyze the data collected by the argument propagator, use the analysis result to update
    // the parameter optimization infos, and rewrite the application.
    // TODO(b/199237357): Automatically rewrite state when lens changes.
    enumUnboxer.rewriteWithLens();
    outliner.rewriteWithLens();
    appView.withArgumentPropagator(
        argumentPropagator ->
            argumentPropagator.tearDownCodeScanner(
                this, postMethodProcessorBuilder, executorService, timing));

    if (libraryMethodOverrideAnalysis != null) {
      libraryMethodOverrideAnalysis.finish();
    }

    if (!options.debug) {
      new TrivialFieldAccessReprocessor(appView.withLiveness(), postMethodProcessorBuilder)
          .run(executorService, feedback, timing);
    }

    outliner.rewriteWithLens();
    enumUnboxer.unboxEnums(appView, this, postMethodProcessorBuilder, executorService, feedback);
    appView.unboxedEnums().checkEnumsUnboxed(appView);

    GraphLens graphLensForSecondaryOptimizationPass = appView.graphLens();

    outliner.rewriteWithLens();

    timing.begin("IR conversion phase 2");
    timing.begin("Build post method processor");
    PostMethodProcessor postMethodProcessor =
        postMethodProcessorBuilder.build(appView, executorService, timing);
    timing.end();
    if (postMethodProcessor != null) {
      assert !options.debug;
      assert appView.graphLens() == graphLensForSecondaryOptimizationPass;
      timing.begin("Process code");
      postMethodProcessor.forEachMethod(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, feedback, postMethodProcessor, methodProcessingContext),
          feedback,
          executorService,
          timing);
      timing.end();
      timing.begin("Update visible optimization info");
      feedback.updateVisibleOptimizationInfo();
      timing.end();
      assert appView.graphLens() == graphLensForSecondaryOptimizationPass;
    }
    timing.end();

    enumUnboxer.unsetRewriter();

    // All the code that should be impacted by the lenses inserted between phase 1 and phase 2
    // have now been processed and rewritten, we clear code lens rewriting so that the class
    // staticizer and phase 3 does not perform again the rewriting.
    appView.clearCodeRewritings();

    // Commit synthetics before creating a builder (otherwise the builder will not include the
    // synthetics.)
    commitPendingSyntheticItemsR8(appView);

    // Build a new application with jumbo string info.
    Builder<?> builder = appView.appInfo().app().builder();
    builder.setHighestSortingString(highestSortingString);

    if (serviceLoaderRewriter != null) {
      processSimpleSynthesizeMethods(
          serviceLoaderRewriter.getServiceLoadMethods(), executorService);
    }

    if (instanceInitializerOutliner != null) {
      processSimpleSynthesizeMethods(
          instanceInitializerOutliner.getSynthesizedMethods(), executorService);
    }
    if (assertionErrorTwoArgsConstructorRewriter != null) {
      processSimpleSynthesizeMethods(
          assertionErrorTwoArgsConstructorRewriter.getSynthesizedMethods(), executorService);
    }

    // Update optimization info for all synthesized methods at once.
    feedback.updateVisibleOptimizationInfo();

    // TODO(b/127694949): Adapt to PostOptimization.
    outliner.performOutlining(this, feedback, executorService, timing);
    clearDexMethodCompilationState();

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInFields(executorService);
    }

    if (Log.ENABLED) {
      if (idempotentFunctionCallCanonicalizer != null) {
        idempotentFunctionCallCanonicalizer.logResults();
      }
      if (libraryMethodOverrideAnalysis != null) {
        libraryMethodOverrideAnalysis.logResults();
      }
      if (stringOptimizer != null) {
        stringOptimizer.logResult();
      }
    }

    // Assure that no more optimization feedback left after post processing.
    assert feedback.noUpdatesLeft();
    return builder.build();
  }

  private void waveStart(ProgramMethodSet wave) {
    onWaveDoneActions = Collections.synchronizedList(new ArrayList<>());
  }

  private void waveDone(ProgramMethodSet wave, ExecutorService executorService)
      throws ExecutionException {
    delayedOptimizationFeedback.refineAppInfoWithLiveness(appView.appInfo().withLiveness());
    delayedOptimizationFeedback.updateVisibleOptimizationInfo();
    fieldAccessAnalysis.fieldAssignmentTracker().waveDone(wave, delayedOptimizationFeedback);
    appView.withArgumentPropagator(ArgumentPropagator::publishDelayedReprocessingCriteria);
    if (appView.options().protoShrinking().enableRemoveProtoEnumSwitchMap()) {
      appView.protoShrinker().protoEnumSwitchMapRemover.updateVisibleStaticFieldValues();
    }
    enumUnboxer.updateEnumUnboxingCandidatesInfo();
    assert delayedOptimizationFeedback.noUpdatesLeft();
    onWaveDoneActions.forEach(com.android.tools.r8.utils.Action::execute);
    onWaveDoneActions = null;
    if (!prunedMethodsInWave.isEmpty()) {
      appView.pruneItems(
          PrunedItems.builder()
              .setRemovedMethods(prunedMethodsInWave)
              .setPrunedApp(appView.appInfo().app())
              .build(),
          executorService);
      prunedMethodsInWave.clear();
    }
  }

  private void lastWaveDone(
      PostMethodProcessor.Builder postMethodProcessorBuilder, ExecutorService executorService)
      throws ExecutionException {
    if (inliner != null) {
      inliner.onLastWaveDone(postMethodProcessorBuilder, executorService, timing);
    }

    // Ensure determinism of method-to-reprocess set.
    appView.testing().checkDeterminism(postMethodProcessorBuilder::dump);
  }

  public void addWaveDoneAction(com.android.tools.r8.utils.Action action) {
    if (!appView.enableWholeProgramOptimizations()) {
      throw new Unreachable("addWaveDoneAction() should never be used in D8.");
    }
    if (!isInWave()) {
      throw new Unreachable("Attempt to call addWaveDoneAction() outside of wave.");
    }
    onWaveDoneActions.add(action);
  }

  public boolean isInWave() {
    return onWaveDoneActions != null;
  }

  private void processSimpleSynthesizeMethods(
      List<ProgramMethod> serviceLoadMethods, ExecutorService executorService)
      throws ExecutionException {
    ThreadUtils.processItems(
        serviceLoadMethods, this::processAndFinalizeSimpleSynthesiedMethod, executorService);
  }

  private void processAndFinalizeSimpleSynthesiedMethod(ProgramMethod method) {
    IRCode code = method.buildIR(appView);
    assert code != null;
    codeRewriter.rewriteMoveResult(code);
    removeDeadCodeAndFinalizeIR(code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  private void clearDexMethodCompilationState() {
    appView.appInfo().classes().forEach(this::clearDexMethodCompilationState);
  }

  private void clearDexMethodCompilationState(DexProgramClass clazz) {
    clazz.forEachMethod(DexEncodedMethod::markNotProcessed);
  }

  /**
   * This will replace the Dex code in the method with the Dex code generated from the provided IR.
   *
   * <p>This method is *only* intended for testing, where tests manipulate the IR and need runnable
   * Dex code.
   *
   * @param code the IR code for the method
   */
  public void replaceCodeForTesting(IRCode code) {
    ProgramMethod method = code.context();
    DexEncodedMethod definition = method.getDefinition();
    assert code.isConsistentSSA(appView);
    Timing timing = Timing.empty();
    deadCodeRemover.run(code, timing);
    method.setCode(
        new IRToDexFinalizer(appView, deadCodeRemover)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), timing),
        appView);
    if (Log.ENABLED) {
      Log.debug(
          getClass(),
          "Resulting dex code for %s:\n%s",
          method.toSourceString(),
          logCode(options, definition));
    }
  }

  public void optimizeSynthesizedMethods(
      List<ProgramMethod> programMethods, ExecutorService executorService)
      throws ExecutionException {
    // Process the generated class, but don't apply any outlining.
    ProgramMethodSet methods = ProgramMethodSet.create(programMethods::forEach);
    processMethodsConcurrently(methods, executorService);
  }

  public void optimizeSynthesizedMethod(ProgramMethod synthesizedMethod) {
    if (!synthesizedMethod.getDefinition().isProcessed()) {
      // Process the generated method, but don't apply any outlining.
      OneTimeMethodProcessor methodProcessor =
          OneTimeMethodProcessor.create(synthesizedMethod, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingContext));
    }
  }

  public void processClassesConcurrently(
      Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodSet wave = ProgramMethodSet.create();
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethod(wave::add);
    }
    processMethodsConcurrently(wave, executorService);
  }

  public void processMethodsConcurrently(ProgramMethodSet wave, ExecutorService executorService)
      throws ExecutionException {
    if (!wave.isEmpty()) {
      OneTimeMethodProcessor methodProcessor = OneTimeMethodProcessor.create(wave, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingContext),
          executorService);
    }
  }

  private String logCode(InternalOptions options, DexEncodedMethod method) {
    return options.useSmaliSyntax ? method.toSmaliString(null) : method.codeToString();
  }

  // TODO(b/140766440): Make this receive a list of CodeOptimizations to conduct.
  public Timing processDesugaredMethod(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    DexEncodedMethod definition = method.getDefinition();
    Code code = definition.getCode();
    boolean matchesMethodFilter = options.methodMatchesFilter(definition);
    if (code != null && matchesMethodFilter) {
      return rewriteDesugaredCode(method, feedback, methodProcessor, methodProcessingContext);
    } else {
      // Mark abstract methods as processed as well.
      definition.markProcessed(ConstraintWithTarget.NEVER);
    }
    return Timing.empty();
  }

  private static void invertConditionalsForTesting(IRCode code) {
    for (BasicBlock block : code.blocks) {
      if (block.exit().isIf()) {
        block.exit().asIf().invert();
      }
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

  Timing rewriteDesugaredCode(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    return ExceptionUtils.withOriginAndPositionAttachmentHandler(
        method.getOrigin(),
        new MethodPosition(method.getReference().asMethodReference()),
        () ->
            rewriteDesugaredCodeInternal(
                method, feedback, methodProcessor, methodProcessingContext));
  }

  private Timing rewriteNonDesugaredCodeInternal(
      ProgramMethod method,
      CfInstructionDesugaringEventConsumer desugaringEventConsumer,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    boolean didDesugar = desugar(method, desugaringEventConsumer, methodProcessingContext);
    if (Log.ENABLED && didDesugar) {
      Log.debug(
          getClass(),
          "Desugared code for %s:\n%s",
          method.toSourceString(),
          logCode(options, method.getDefinition()));
    }
    return rewriteDesugaredCodeInternal(method, feedback, methodProcessor, methodProcessingContext);
  }

  private Timing rewriteDesugaredCodeInternal(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    if (options.verbose) {
      options.reporter.info(
          new StringDiagnostic("Processing: " + method.toSourceString()));
    }
    if (Log.ENABLED) {
      Log.debug(
          getClass(),
          "Original code for %s:\n%s",
          method.toSourceString(),
          logCode(options, method.getDefinition()));
    }
    if (options.testing.hookInIrConversion != null) {
      options.testing.hookInIrConversion.run();
    }

    if (!needsIRConversion(method) || options.skipIR) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return Timing.empty();
    }

    IRCode code = method.buildIR(appView);
    if (code == null) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return Timing.empty();
    }
    return optimize(code, feedback, methodProcessor, methodProcessingContext);
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

  // TODO(b/140766440): Convert all sub steps an implementer of CodeOptimization
  private Timing optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod context = code.context();
    DexEncodedMethod method = context.getDefinition();
    DexProgramClass holder = context.getHolder();
    assert holder != null;

    Timing timing = Timing.create(context.toSourceString(), options);

    if (Log.ENABLED) {
      Log.debug(getClass(), "Initial (SSA) flow graph for %s:\n%s", method.toSourceString(), code);
    }
    // Compilation header if printing CFGs for this method.
    printC1VisualizerHeader(method);
    String previous = printMethod(code, "Initial IR (SSA)", null);

    if (options.testing.irModifier != null) {
      options.testing.irModifier.accept(code, appView);
    }

    if (options.canHaveArtStringNewInitBug()) {
      timing.begin("Check for new-init issue");
      CodeRewriter.ensureDirectStringNewToInit(code, appView.dexItemFactory());
      timing.end();
    }

    if (options.canHaveInvokeInterfaceToObjectMethodBug()) {
      timing.begin("JDK-8272564 fix rewrite");
      CodeRewriter.rewriteJdk8272564Fix(code, context, appView);
      timing.end();
    }

    boolean isDebugMode = options.debug || context.getOrComputeReachabilitySensitive(appView);

    if (isDebugMode) {
      codeRewriter.simplifyDebugLocals(code);
    }

    if (lensCodeRewriter != null) {
      timing.begin("Lens rewrite");
      lensCodeRewriter.rewrite(code, context, methodProcessor);
      timing.end();
    }

    assert !method.isProcessed() || !isDebugMode
        : "Method already processed: "
            + context.toSourceString()
            + System.lineSeparator()
            + ExceptionUtils.getMainStackTrace();
    assert !method.isProcessed()
            || !appView.enableWholeProgramOptimizations()
            || !appView.appInfo().withLiveness().isNeverReprocessMethod(context)
        : "Unexpected reprocessing of method: " + context.toSourceString();

    if (typeChecker != null && !typeChecker.check(code)) {
      assert appView.enableWholeProgramOptimizations();
      assert options.testing.allowTypeErrors;
      StringDiagnostic warning =
          new StringDiagnostic(
              "The method `"
                  + method.toSourceString()
                  + "` does not type check and will be assumed to be unreachable.");
      options.reporter.warning(warning);
      context.convertToThrowNullMethod(appView);
      return timing;
    }

    // This is the first point in time where we can assert that the types are sound. If this
    // assert fails, then the types that we have inferred are unsound, or the method does not type
    // check. In the latter case, the type checker should be extended to detect the issue such that
    // we will return with a throw-null method above.
    assert code.verifyTypes(appView);
    assert code.isConsistentSSA(appView);

    if (shouldPassThrough(context)) {
      // If the code is pass trough, do not finalize by overwriting the existing code.
      assert appView.enableWholeProgramOptimizations();
      timing.begin("Collect optimization info");
      collectOptimizationInfo(
          context,
          code,
          ClassInitializerDefaultsResult.empty(),
          feedback,
          methodProcessor,
          BytecodeMetadataProvider.builder(),
          timing);
      timing.end();
      markProcessed(code, feedback);
      return timing;
    }

    assertionsRewriter.run(method, code, deadCodeRemover, timing);
    CheckNotNullConverter.runIfNecessary(appView, code);

    if (serviceLoaderRewriter != null) {
      assert appView.appInfo().hasLiveness();
      timing.begin("Rewrite service loaders");
      serviceLoaderRewriter.rewrite(code, methodProcessingContext);
      timing.end();
    }

    if (identifierNameStringMarker != null) {
      timing.begin("Decouple identifier-name strings");
      identifierNameStringMarker.decoupleIdentifierNameStringsInMethod(code);
      timing.end();
      assert code.isConsistentSSA(appView);
    }

    if (memberValuePropagation != null) {
      timing.begin("Propagate member values");
      memberValuePropagation.run(code);
      timing.end();
    }

    if (enumValueOptimizer != null) {
      assert appView.enableWholeProgramOptimizations();
      timing.begin("Remove switch maps");
      enumValueOptimizer.removeSwitchMaps(code);
      timing.end();
    }

    if (instanceInitializerOutliner != null) {
      instanceInitializerOutliner.rewriteInstanceInitializers(
          code, context, methodProcessingContext);
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after disable assertions (SSA)", previous);

    // Update the IR code if collected call site optimization info has something useful.
    // While aggregation of parameter information at call sites would be more precise than static
    // types, those could be still less precise at one single call site, where specific arguments
    // will be passed during (double) inlining. Instead of adding assumptions and removing invalid
    // ones, it's better not to insert assumptions for inlinee in the beginning.
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        context.getOptimizationInfo().getArgumentInfos();
    if (callSiteOptimizationInfo.isConcreteCallSiteOptimizationInfo() && appView.hasLiveness()) {
      ArgumentPropagatorIROptimizer.optimize(
          appView.withLiveness(),
          code,
          callSiteOptimizationInfo.asConcreteCallSiteOptimizationInfo());
    }

    if (assumeInserter != null) {
      assumeInserter.insertAssumeInstructions(code, timing);
    }

    previous = printMethod(code, "IR after inserting assume instructions (SSA)", previous);

    timing.begin("Run proto shrinking tasks");
    appView.withGeneratedExtensionRegistryShrinker(shrinker -> shrinker.rewriteCode(method, code));

    previous = printMethod(code, "IR after generated extension registry shrinking (SSA)", previous);

    appView.withGeneratedMessageLiteShrinker(shrinker -> shrinker.run(code));
    timing.end();

    previous = printMethod(code, "IR after generated message lite shrinking (SSA)", previous);

    if (!isDebugMode && options.inlinerOptions().enableInlining && inliner != null) {
      timing.begin("Inlining");
      inliner.performInlining(code.context(), code, feedback, methodProcessor, timing);
      timing.end();
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after inlining (SSA)", previous);

    if (appView.appInfo().hasLiveness()) {
      // Reflection optimization 1. getClass() / forName() -> const-class
      timing.begin("Rewrite to const class");
      ReflectionOptimizer.rewriteGetClassOrForNameToConstClass(appView.withLiveness(), code);
      timing.end();
    }

    if (!isDebugMode) {
      // Reflection optimization 2. get*Name() with const-class -> const-string
      if (options.enableNameReflectionOptimization
          || options.testing.forceNameReflectionOptimization) {
        timing.begin("Rewrite Class.getName");
        stringOptimizer.rewriteClassGetName(appView, code);
        timing.end();
      }
      // Reflection/string optimization 3. trivial conversion/computation on const-string
      timing.begin("Optimize const strings");
      stringOptimizer.computeTrivialOperationsOnConstString(code);
      stringOptimizer.removeTrivialConversions(code);
      timing.end();
      timing.begin("Optimize library methods");
      appView
          .libraryMethodOptimizer()
          .optimize(code, feedback, methodProcessor, methodProcessingContext);
      timing.end();
      previous = printMethod(code, "IR after class library method optimizer (SSA)", previous);
      assert code.isConsistentSSA(appView);
    }

    assert code.verifyTypes(appView);

    if (devirtualizer != null) {
      assert code.verifyTypes(appView);
      timing.begin("Devirtualize invoke interface");
      devirtualizer.devirtualizeInvokeInterface(code);
      timing.end();
      previous = printMethod(code, "IR after devirtualizer (SSA)", previous);
    }

    assert code.verifyTypes(appView);

    timing.begin("Remove trivial type checks/casts");
    codeRewriter.removeTrivialCheckCastAndInstanceOfInstructions(
        code, context, methodProcessor, methodProcessingContext);
    timing.end();

    if (enumValueOptimizer != null) {
      assert appView.enableWholeProgramOptimizations();
      timing.begin("Rewrite constant enum methods");
      enumValueOptimizer.rewriteConstantEnumMethodCalls(code);
      timing.end();
    }

    timing.begin("Rewrite array length");
    codeRewriter.rewriteKnownArrayLengthCalls(code);
    timing.end();
    timing.begin("Natural Int Loop Remover");
    naturalIntLoopRemover.run(appView, code);
    timing.end();
    timing.begin("Rewrite AssertionError");
    assertionErrorTwoArgsConstructorRewriter.rewrite(code, methodProcessingContext);
    timing.end();
    timing.begin("Run CSE");
    codeRewriter.commonSubexpressionElimination(code);
    timing.end();
    timing.begin("Simplify arrays");
    codeRewriter.simplifyArrayConstruction(code);
    timing.end();
    timing.begin("Rewrite move result");
    codeRewriter.rewriteMoveResult(code);
    timing.end();
    if (options.enableStringConcatenationOptimization && !isDebugMode) {
      timing.begin("Rewrite string concat");
      StringBuilderAppendOptimizer.run(appView, code);
      timing.end();
    }
    timing.begin("Split range invokes");
    codeRewriter.splitRangeInvokeConstants(code);
    timing.end();
    timing.begin("Propogate sparse conditionals");
    new SparseConditionalConstantPropagation(appView, code).run();
    timing.end();
    timing.begin("Rewrite always throwing instructions");
    codeRewriter.optimizeAlwaysThrowingInstructions(code);
    timing.end();
    timing.begin("Simplify control flow");
    if (codeRewriter.simplifyControlFlow(code)) {
      timing.begin("Remove trivial type checks/casts");
      codeRewriter.removeTrivialCheckCastAndInstanceOfInstructions(
          code, context, methodProcessor, methodProcessingContext);
      timing.end();
    }
    timing.end();
    if (options.enableRedundantConstNumberOptimization) {
      timing.begin("Remove const numbers");
      codeRewriter.redundantConstNumberRemoval(code);
      timing.end();
    }
    if (RedundantFieldLoadAndStoreElimination.shouldRun(appView, code)) {
      timing.begin("Remove field loads");
      new RedundantFieldLoadAndStoreElimination(appView, code).run();
      timing.end();
    }

    if (options.testing.invertConditionals) {
      invertConditionalsForTesting(code);
    }

    if (!isDebugMode) {
      timing.begin("Rewrite throw NPE");
      codeRewriter.rewriteThrowNullPointerException(code);
      timing.end();
      previous = printMethod(code, "IR after rewrite throw null (SSA)", previous);
    }

    timing.begin("Optimize class initializers");
    ClassInitializerDefaultsResult classInitializerDefaultsResult =
        classInitializerDefaultsOptimization.optimize(code, feedback);
    timing.end();
    previous = printMethod(code, "IR after class initializer optimisation (SSA)", previous);

    if (Log.ENABLED) {
      Log.debug(getClass(), "Intermediate (SSA) flow graph for %s:\n%s",
          method.toSourceString(), code);
    }
    // Dead code removal. Performed after simplifications to remove code that becomes dead
    // as a result of those simplifications. The following optimizations could reveal more
    // dead code which is removed right before register allocation in performRegisterAllocation.
    deadCodeRemover.run(code, timing);
    assert code.isConsistentSSA(appView);

    previous = printMethod(code, "IR after dead code removal (SSA)", previous);

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR before class inlining (SSA)", previous);

    if (classInliner != null) {
      timing.begin("Inline classes");
      // Class inliner should work before lambda merger, so if it inlines the
      // lambda, it does not get collected by merger.
      assert options.inlinerOptions().enableInlining && inliner != null;
      classInliner.processMethodCode(
          appView.withLiveness(),
          codeRewriter,
          stringOptimizer,
          enumValueOptimizer,
          code.context(),
          code,
          feedback,
          methodProcessor,
          methodProcessingContext,
          inliner,
          new LazyBox<>(
              () ->
                  inliner.createDefaultOracle(
                      code.context(),
                      methodProcessor,
                      // Inlining instruction allowance is not needed for the class inliner since it
                      // always uses a force inlining oracle for inlining.
                      -1)));
      timing.end();
      assert code.isConsistentSSA(appView);
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after class inlining (SSA)", previous);

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR after interface method rewriting (SSA)", previous);

    // TODO(b/140766440): an ideal solution would be putting CodeOptimization for this into
    //  the list for primary processing only.
    outliner.collectOutlineSites(code, timing);

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR after outline handler (SSA)", previous);

    if (code.getConversionOptions().isStringSwitchConversionEnabled()) {
      // Remove string switches prior to canonicalization to ensure that the constants that are
      // being introduced will be canonicalized if possible.
      timing.begin("Remove string switch");
      stringSwitchRemover.run(code);
      timing.end();
    }

    // TODO(mkroghj) Test if shorten live ranges is worth it.
    if (!options.isGeneratingClassFiles()) {
      timing.begin("Canonicalize constants");
      ConstantCanonicalizer constantCanonicalizer =
          new ConstantCanonicalizer(appView, codeRewriter, context, code);
      constantCanonicalizer.canonicalize();
      timing.end();
      previous = printMethod(code, "IR after constant canonicalization (SSA)", previous);
      timing.begin("Create constants for literal instructions");
      codeRewriter.useDedicatedConstantForLitInstruction(code);
      timing.end();
      previous = printMethod(code, "IR after constant literals (SSA)", previous);
      timing.begin("Shorten live ranges");
      codeRewriter.shortenLiveRanges(code, constantCanonicalizer);
      timing.end();
      previous = printMethod(code, "IR after shorten live ranges (SSA)", previous);
    }

    timing.begin("Canonicalize idempotent calls");
    idempotentFunctionCallCanonicalizer.canonicalize(code);
    timing.end();

    previous =
        printMethod(code, "IR after idempotent function call canonicalization (SSA)", previous);

    // Insert code to log arguments if requested.
    if (options.methodMatchesLogArgumentsFilter(method) && !method.isProcessed()) {
      codeRewriter.logArgumentTypes(method, code);
      assert code.isConsistentSSA(appView);
    }

    previous = printMethod(code, "IR after argument type logging (SSA)", previous);

    assert code.verifyTypes(appView);

    deadCodeRemover.run(code, timing);

    BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder =
        BytecodeMetadataProvider.builder();
    if (appView.enableWholeProgramOptimizations()) {
      timing.begin("Collect optimization info");
      collectOptimizationInfo(
          context,
          code,
          classInitializerDefaultsResult,
          feedback,
          methodProcessor,
          bytecodeMetadataProviderBuilder,
          timing);
      timing.end();
    }

    if (assumeInserter != null) {
      timing.begin("Remove assume instructions");
      CodeRewriter.removeAssumeInstructions(appView, code);
      timing.end();
      assert code.isConsistentSSA(appView);

      // TODO(b/214496607): Remove when dynamic types are safe w.r.t. interface assignment rules.
      codeRewriter.rewriteMoveResult(code);
    }

    // Assert that we do not have unremoved non-sense code in the output, e.g., v <- non-null NULL.
    assert code.verifyNoNullabilityBottomTypes();

    assert code.verifyTypes(appView);

    previous =
        printMethod(code, "IR after computation of optimization info summary (SSA)", previous);

    printMethod(code, "Optimized IR (SSA)", previous);
    timing.begin("Finalize IR");
    finalizeIR(
        code,
        feedback,
        bytecodeMetadataProviderBuilder.build(),
        timing);
    timing.end();
    return timing;
  }

  private boolean shouldPassThrough(ProgramMethod method) {
    if (appView.isCfByteCodePassThrough(method.getDefinition())) {
      return true;
    }
    Code code = method.getDefinition().getCode();
    assert !code.isThrowNullCode();
    return code.isDefaultInstanceInitializerCode();
  }

  // Compute optimization info summary for the current method unless it is pinned
  // (in that case we should not be making any assumptions about the behavior of the method).
  public void collectOptimizationInfo(
      ProgramMethod method,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      BytecodeMetadataProvider.Builder bytecodeMetadataProviderBuilder,
      Timing timing) {
    appView.withArgumentPropagator(
        argumentPropagator -> argumentPropagator.scan(method, code, methodProcessor, timing));

    if (methodProcessor.isPrimaryMethodProcessor()) {
      enumUnboxer.analyzeEnums(code, methodProcessor);
    }

    if (inliner != null) {
      inliner.recordCallEdgesForMultiCallerInlining(method, code, methodProcessor, timing);
    }

    if (libraryMethodOverrideAnalysis != null) {
      timing.begin("Analyze library method overrides");
      libraryMethodOverrideAnalysis.analyze(code);
      timing.end();
    }

    if (fieldAccessAnalysis != null) {
      timing.begin("Analyze field accesses");
      fieldAccessAnalysis.recordFieldAccesses(
          code, bytecodeMetadataProviderBuilder, feedback, methodProcessor);
      if (classInitializerDefaultsResult != null) {
        fieldAccessAnalysis.acceptClassInitializerDefaultsResult(classInitializerDefaultsResult);
      }
      timing.end();
    }

    if (appView.getKeepInfo(code.context()).isPinned(options)) {
      return;
    }

    InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos = null;
    StaticFieldValues staticFieldValues = null;
    if (method.getDefinition().isInitializer()) {
      if (method.getDefinition().isClassInitializer()) {
        staticFieldValues =
            StaticFieldValueAnalysis.run(
                appView, code, classInitializerDefaultsResult, feedback, timing);
      } else {
        instanceFieldInitializationInfos =
            InstanceFieldValueAnalysis.run(
                appView, code, classInitializerDefaultsResult, feedback, timing);
      }
    }
    enumUnboxer.recordEnumState(method.getHolder(), staticFieldValues);
    if (appView.options().protoShrinking().enableRemoveProtoEnumSwitchMap()) {
      appView
          .protoShrinker()
          .protoEnumSwitchMapRemover
          .recordStaticValues(method.getHolder(), staticFieldValues);
    }
    methodOptimizationInfoCollector.collectMethodOptimizationInfo(
        method,
        code,
        feedback,
        dynamicTypeOptimization,
        instanceFieldInitializationInfos,
        methodProcessor,
        timing);
  }

  public void removeDeadCodeAndFinalizeIR(
      IRCode code, OptimizationFeedback feedback, Timing timing) {
    if (stringSwitchRemover != null) {
      stringSwitchRemover.run(code);
    }
    deadCodeRemover.run(code, timing);
    finalizeIR(
        code,
        feedback,
        BytecodeMetadataProvider.empty(),
        timing);
  }

  public void finalizeIR(
      IRCode code,
      OptimizationFeedback feedback,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing) {
    if (options.testing.roundtripThroughLIR) {
      code = roundtripThroughLIR(code, feedback, bytecodeMetadataProvider, timing);
    }
    if (options.isGeneratingClassFiles()) {
      timing.begin("IR->CF");
      finalizeToCf(code, feedback, bytecodeMetadataProvider, timing);
      timing.end();
    } else {
      assert options.isGeneratingDex();
      timing.begin("IR->DEX");
      finalizeToDex(code, feedback, bytecodeMetadataProvider, timing);
      timing.end();
    }
  }

  private IRCode roundtripThroughLIR(
      IRCode code,
      OptimizationFeedback feedback,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing) {
    timing.begin("IR->LIR");
    LIRCode lirCode = IR2LIRConverter.translate(code, appView.dexItemFactory());
    timing.end();
    timing.begin("LIR->IR");
    IRCode irCode = LIR2IRConverter.translate(code.context(), lirCode, appView);
    timing.end();
    return irCode;
  }

  private void finalizeToCf(
      IRCode code,
      OptimizationFeedback feedback,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing) {
    ProgramMethod method = code.context();
    method.setCode(
        new IRToCfFinalizer(appView, deadCodeRemover)
            .finalizeCode(code, bytecodeMetadataProvider, timing),
        appView);
    markProcessed(code, feedback);
  }

  private void finalizeToDex(
      IRCode code,
      OptimizationFeedback feedback,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing) {
    ProgramMethod method = code.context();
    DexEncodedMethod definition = method.getDefinition();
    method.setCode(
        new IRToDexFinalizer(appView, deadCodeRemover)
            .finalizeCode(code, bytecodeMetadataProvider, timing),
        appView);
    markProcessed(code, feedback);
    updateHighestSortingStrings(definition);
  }

  public void markProcessed(IRCode code, OptimizationFeedback feedback) {
    // After all the optimizations have take place, we compute whether method should be inlined.
    ProgramMethod method = code.context();
    ConstraintWithTarget state =
        shouldComputeInliningConstraint(method)
            ? inliner.computeInliningConstraint(code)
            : ConstraintWithTarget.NEVER;
    feedback.markProcessed(method.getDefinition(), state);
  }

  private boolean shouldComputeInliningConstraint(ProgramMethod method) {
    if (!options.inlinerOptions().enableInlining || inliner == null) {
      return false;
    }
    DexEncodedMethod definition = method.getDefinition();
    if (definition.isClassInitializer() || method.getOrComputeReachabilitySensitive(appView)) {
      return false;
    }
    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
    if (!keepInfo.isInliningAllowed(options) && !keepInfo.isClassInliningAllowed(options)) {
      return false;
    }
    return true;
  }

  private synchronized void updateHighestSortingStrings(DexEncodedMethod method) {
    Code code = method.getCode();
    assert code.isDexWritableCode();
    DexString highestSortingReferencedString = code.asDexWritableCode().getHighestSortingString();
    if (highestSortingReferencedString != null) {
      if (highestSortingString == null
          || highestSortingReferencedString.compareTo(highestSortingString) > 0) {
        highestSortingString = highestSortingReferencedString;
      }
    }
  }

  private void printC1VisualizerHeader(DexEncodedMethod method) {
    if (printer != null) {
      printer.begin("compilation");
      printer.print("name \"").append(method.toSourceString()).append("\"").ln();
      printer.print("method \"").append(method.toSourceString()).append("\"").ln();
      printer.print("date 0").ln();
      printer.end("compilation");
    }
  }

  public void printPhase(String phase) {
    if (!options.extensiveLoggingFilter.isEmpty()) {
      System.out.println("Entering phase: " + phase);
    }
  }

  public String printMethod(IRCode code, String title, String previous) {
    if (printer != null) {
      printer.resetUnusedValue();
      printer.begin("cfg");
      printer.print("name \"").append(title).append("\"\n");
      code.print(printer);
      printer.end("cfg");
    }
    if (options.extensiveLoggingFilter.size() > 0
        && options.extensiveLoggingFilter.contains(code.method().getReference().toSourceString())) {
      String current = code.toString();
      System.out.println();
      System.out.println("-----------------------------------------------------------------------");
      System.out.println(title);
      System.out.println("-----------------------------------------------------------------------");
      if (previous != null && previous.equals(current)) {
        System.out.println("Unchanged");
      } else {
        System.out.println(current);
      }
      System.out.println("-----------------------------------------------------------------------");
      return current;
    }
    return previous;
  }

  /**
   * Called when a method is pruned as a result of optimizations during IR processing in R8, to
   * allow optimizations that track sets of methods to fixup their state.
   */
  public void onMethodPruned(ProgramMethod method) {
    assert appView.enableWholeProgramOptimizations();
    assert method.getHolder().lookupMethod(method.getReference()) == null;
    appView.withArgumentPropagator(argumentPropagator -> argumentPropagator.onMethodPruned(method));
    enumUnboxer.onMethodPruned(method);
    outliner.onMethodPruned(method);
    if (inliner != null) {
      inliner.onMethodPruned(method);
    }
    prunedMethodsInWave.add(method.getReference());
  }

  /**
   * Called when a method is transformed into an abstract or "throw null" method as a result of
   * optimizations during IR processing in R8.
   */
  public void onMethodCodePruned(ProgramMethod method) {
    assert appView.enableWholeProgramOptimizations();
    assert method.getHolder().lookupMethod(method.getReference()) != null;
    appView.withArgumentPropagator(
        argumentPropagator -> argumentPropagator.onMethodCodePruned(method));
    enumUnboxer.onMethodCodePruned(method);
    outliner.onMethodCodePruned(method);
    if (inliner != null) {
      inliner.onMethodCodePruned(method);
    }
  }
}
