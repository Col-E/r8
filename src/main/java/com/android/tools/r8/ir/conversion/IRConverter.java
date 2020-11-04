// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.Flavor.ExcludeDexResources;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.Flavor.IncludeAllResources;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.EnumValueInfoMapCollection;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.classmerging.HorizontallyMergedLambdaClasses;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.fieldaccess.FieldAccessAnalysis;
import com.android.tools.r8.ir.analysis.fieldaccess.TrivialFieldAccessReprocessor;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.InstanceFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.AlwaysMaterializingDefinition;
import com.android.tools.r8.ir.code.AlwaysMaterializingUser;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformer;
import com.android.tools.r8.ir.desugar.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter.Mode;
import com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.Flavor;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.ir.desugar.StringConcatRewriter;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.ir.optimize.AssertionsRewriter;
import com.android.tools.r8.ir.optimize.AssumeInserter;
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
import com.android.tools.r8.ir.optimize.MemberValuePropagation;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.PeepholeOptimizer;
import com.android.tools.r8.ir.optimize.RedundantFieldLoadElimination;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriter;
import com.android.tools.r8.ir.optimize.classinliner.ClassInliner;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer;
import com.android.tools.r8.ir.optimize.enums.EnumValueOptimizer;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfoCollector;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.lambda.LambdaMerger;
import com.android.tools.r8.ir.optimize.staticizer.ClassStaticizer;
import com.android.tools.r8.ir.optimize.string.StringBuilderOptimizer;
import com.android.tools.r8.ir.optimize.string.StringOptimizer;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryMethodOverrideAnalysis;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IRConverter {

  private static final int PEEPHOLE_OPTIMIZATION_PASSES = 2;

  public final AppView<?> appView;
  public final MainDexTracingResult mainDexClasses;

  private final Timing timing;
  private final Outliner outliner;
  private final ClassInitializerDefaultsOptimization classInitializerDefaultsOptimization;
  private final FieldAccessAnalysis fieldAccessAnalysis;
  private final LibraryMethodOverrideAnalysis libraryMethodOverrideAnalysis;
  private final StringConcatRewriter stringConcatRewriter;
  private final StringOptimizer stringOptimizer;
  private final StringBuilderOptimizer stringBuilderOptimizer;
  private final IdempotentFunctionCallCanonicalizer idempotentFunctionCallCanonicalizer;
  private final LambdaRewriter lambdaRewriter;
  private final D8NestBasedAccessDesugaring d8NestBasedAccessDesugaring;
  private final InterfaceMethodRewriter interfaceMethodRewriter;
  private final TwrCloseResourceRewriter twrCloseResourceRewriter;
  private final BackportedMethodRewriter backportedMethodRewriter;
  private final DesugaredLibraryRetargeter desugaredLibraryRetargeter;
  private final LambdaMerger lambdaMerger;
  private final ClassInliner classInliner;
  private final ClassStaticizer classStaticizer;
  private final InternalOptions options;
  private final CfgPrinter printer;
  private final CodeRewriter codeRewriter;
  private final ConstantCanonicalizer constantCanonicalizer;
  private final MemberValuePropagation memberValuePropagation;
  private final LensCodeRewriter lensCodeRewriter;
  private final Inliner inliner;
  private final IdentifierNameStringMarker identifierNameStringMarker;
  private final Devirtualizer devirtualizer;
  private final CovariantReturnTypeAnnotationTransformer covariantReturnTypeAnnotationTransformer;
  private final StringSwitchRemover stringSwitchRemover;
  private final TypeChecker typeChecker;
  private final DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;
  private final ServiceLoaderRewriter serviceLoaderRewriter;
  private final EnumValueOptimizer enumValueOptimizer;
  private final EnumUnboxer enumUnboxer;

  public final AssumeInserter assumeInserter;
  private final DynamicTypeOptimization dynamicTypeOptimization;

  final AssertionsRewriter assertionsRewriter;
  final DeadCodeRemover deadCodeRemover;

  private final MethodOptimizationInfoCollector methodOptimizationInfoCollector;

  private final OptimizationFeedbackDelayed delayedOptimizationFeedback =
      new OptimizationFeedbackDelayed();
  private final OptimizationFeedback simpleOptimizationFeedback =
      OptimizationFeedbackSimple.getInstance();
  private DexString highestSortingString;

  private List<Action> onWaveDoneActions = null;

  private final List<DexString> neverMergePrefixes;
  // Use AtomicBoolean to satisfy TSAN checking (see b/153714743).
  AtomicBoolean seenNotNeverMergePrefix = new AtomicBoolean();
  AtomicBoolean seenNeverMergePrefix = new AtomicBoolean();

  /**
   * The argument `appView` is used to determine if whole program optimizations are allowed or not
   * (i.e., whether we are running R8). See {@link AppView#enableWholeProgramOptimizations()}.
   */
  public IRConverter(
      AppView<?> appView, Timing timing, CfgPrinter printer, MainDexTracingResult mainDexClasses) {
    assert appView.appInfo().hasLiveness() || appView.graphLens().isIdentityLens();
    assert appView.options() != null;
    assert appView.options().programConsumer != null;
    assert timing != null;
    this.timing = timing;
    this.appView = appView;
    this.options = appView.options();
    this.printer = printer;
    this.mainDexClasses = mainDexClasses;
    this.codeRewriter = new CodeRewriter(appView, this);
    this.constantCanonicalizer = new ConstantCanonicalizer(codeRewriter);
    this.classInitializerDefaultsOptimization =
        new ClassInitializerDefaultsOptimization(appView, this);
    this.stringConcatRewriter = new StringConcatRewriter(appView);
    this.stringOptimizer = new StringOptimizer(appView);
    this.stringBuilderOptimizer = new StringBuilderOptimizer(appView);
    this.deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
    this.assertionsRewriter = new AssertionsRewriter(appView);
    this.idempotentFunctionCallCanonicalizer = new IdempotentFunctionCallCanonicalizer(appView);
    this.neverMergePrefixes =
        options.neverMergePrefixes.stream()
            .map(prefix -> "L" + DescriptorUtils.getPackageBinaryNameFromJavaType(prefix))
            .map(options.itemFactory::createString)
            .collect(Collectors.toList());
    if (options.isDesugaredLibraryCompilation()) {
      // Specific L8 Settings, performs all desugaring including L8 specific desugaring.
      // The following desugaring are required for L8 specific desugaring:
      // - DesugaredLibraryRetargeter for retarget core library members.
      // - InterfaceMethodRewriter for emulated interfaces,
      // - LambdaRewriter since InterfaceMethodDesugaring does not support invokeCustom rewriting,
      // - DesugaredLibraryAPIConverter to duplicate APIs.
      // The following desugaring are present so all desugaring is performed cf to cf in L8, and
      // the second L8 phase can just run with Desugar turned off:
      // - InterfaceMethodRewriter for non L8 specific interface method desugaring,
      // - TwrCloseResourceRewriter,
      // - NestBaseAccessDesugaring.
      assert options.desugarState == DesugarState.ON;
      this.desugaredLibraryRetargeter =
          options.desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()
              ? null
              : new DesugaredLibraryRetargeter(appView);
      this.interfaceMethodRewriter =
          options.desugaredLibraryConfiguration.getEmulateLibraryInterface().isEmpty()
              ? null
              : new InterfaceMethodRewriter(appView, this);
      this.lambdaRewriter = new LambdaRewriter(appView);
      this.desugaredLibraryAPIConverter =
          new DesugaredLibraryAPIConverter(appView, Mode.GENERATE_CALLBACKS_AND_WRAPPERS);
      this.backportedMethodRewriter = new BackportedMethodRewriter(appView);
      this.twrCloseResourceRewriter =
          enableTwrCloseResourceDesugaring() ? new TwrCloseResourceRewriter(appView, this) : null;
      this.d8NestBasedAccessDesugaring =
          options.shouldDesugarNests() ? new D8NestBasedAccessDesugaring(appView) : null;
      this.lambdaMerger = null;
      this.covariantReturnTypeAnnotationTransformer = null;
      this.dynamicTypeOptimization = null;
      this.classInliner = null;
      this.classStaticizer = null;
      this.fieldAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.outliner = null;
      this.memberValuePropagation = null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.typeChecker = null;
      this.stringSwitchRemover = null;
      this.serviceLoaderRewriter = null;
      this.methodOptimizationInfoCollector = null;
      this.enumValueOptimizer = null;
      this.enumUnboxer = null;
      this.assumeInserter = null;
      return;
    }
    this.lambdaRewriter =
        (options.desugarState == DesugarState.ON && !appView.enableWholeProgramOptimizations())
            ? new LambdaRewriter(appView)
            : null;
    this.interfaceMethodRewriter =
        options.isInterfaceMethodDesugaringEnabled()
            ? new InterfaceMethodRewriter(appView, this)
            : null;
    this.twrCloseResourceRewriter =
        (options.desugarState == DesugarState.ON && enableTwrCloseResourceDesugaring())
            ? new TwrCloseResourceRewriter(appView, this)
            : null;
    this.backportedMethodRewriter =
        (options.desugarState == DesugarState.ON && !appView.enableWholeProgramOptimizations())
            ? new BackportedMethodRewriter(appView)
            : null;
    this.desugaredLibraryRetargeter =
        options.desugaredLibraryConfiguration.getRetargetCoreLibMember().isEmpty()
            ? null
            : new DesugaredLibraryRetargeter(appView);
    this.covariantReturnTypeAnnotationTransformer =
        options.processCovariantReturnTypeAnnotations
            ? new CovariantReturnTypeAnnotationTransformer(this, appView.dexItemFactory())
            : null;
    if (appView.enableWholeProgramOptimizations()) {
      assert appView.appInfo().hasLiveness();
      assert appView.rootSet() != null;
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      assumeInserter = new AssumeInserter(appViewWithLiveness);
      this.classInliner =
          options.enableClassInlining && options.enableInlining ? new ClassInliner() : null;
      this.classStaticizer =
          options.enableClassStaticizer ? new ClassStaticizer(appViewWithLiveness, this) : null;
      this.dynamicTypeOptimization = new DynamicTypeOptimization(appViewWithLiveness);
      this.fieldAccessAnalysis =
          FieldAccessAnalysis.enable(options) ? new FieldAccessAnalysis(appViewWithLiveness) : null;
      this.libraryMethodOverrideAnalysis =
          options.enableTreeShakingOfLibraryMethodOverrides
              ? new LibraryMethodOverrideAnalysis(appViewWithLiveness)
              : null;
      this.lambdaMerger =
          options.enableLambdaMerging ? new LambdaMerger(appViewWithLiveness) : null;
      this.enumUnboxer = options.enableEnumUnboxing ? new EnumUnboxer(appViewWithLiveness) : null;
      this.lensCodeRewriter = new LensCodeRewriter(appViewWithLiveness, enumUnboxer);
      this.inliner =
          new Inliner(appViewWithLiveness, mainDexClasses, lambdaMerger, lensCodeRewriter);
      this.outliner = new Outliner(appViewWithLiveness);
      this.memberValuePropagation =
          options.enableValuePropagation ? new MemberValuePropagation(appViewWithLiveness) : null;
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
      this.d8NestBasedAccessDesugaring = null;
      this.serviceLoaderRewriter =
          options.enableServiceLoaderRewriting
              ? new ServiceLoaderRewriter(appViewWithLiveness)
              : null;
      this.desugaredLibraryAPIConverter =
          appView.rewritePrefix.isRewriting()
              ? new DesugaredLibraryAPIConverter(
                  appView, Mode.ASSERT_CALLBACKS_AND_WRAPPERS_GENERATED)
              : null;
      this.enumValueOptimizer =
          options.enableEnumValueOptimization ? new EnumValueOptimizer(appViewWithLiveness) : null;
    } else {
      this.assumeInserter = null;
      this.classInliner = null;
      this.classStaticizer = null;
      this.dynamicTypeOptimization = null;
      this.fieldAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.lambdaMerger = null;
      this.outliner = null;
      this.memberValuePropagation = null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.typeChecker = null;
      this.d8NestBasedAccessDesugaring =
          options.shouldDesugarNests() ? new D8NestBasedAccessDesugaring(appView) : null;
      this.desugaredLibraryAPIConverter =
          appView.rewritePrefix.isRewriting()
              ? new DesugaredLibraryAPIConverter(appView, Mode.GENERATE_CALLBACKS_AND_WRAPPERS)
              : null;
      this.serviceLoaderRewriter = null;
      this.methodOptimizationInfoCollector = null;
      this.enumValueOptimizer = null;
      this.enumUnboxer = null;
    }
    this.stringSwitchRemover =
        options.isStringSwitchConversionEnabled()
            ? new StringSwitchRemover(appView, identifierNameStringMarker)
            : null;
  }

  /** Create an IR converter for processing methods with full program optimization disabled. */
  public IRConverter(AppView<?> appView, Timing timing) {
    this(appView, timing, null, MainDexTracingResult.NONE);
  }

  /** Create an IR converter for processing methods with full program optimization disabled. */
  public IRConverter(AppView<?> appView, Timing timing, CfgPrinter printer) {
    this(appView, timing, printer, MainDexTracingResult.NONE);
  }

  public IRConverter(AppInfo appInfo, Timing timing, CfgPrinter printer) {
    this(AppView.createForD8(appInfo), timing, printer, MainDexTracingResult.NONE);
  }

  private boolean enableTwrCloseResourceDesugaring() {
    return enableTryWithResourcesDesugaring() && !options.canUseTwrCloseResourceMethod();
  }

  private boolean enableTryWithResourcesDesugaring() {
    switch (options.tryWithResourcesDesugaring) {
      case Off:
        return false;
      case Auto:
        return !options.canUseSuppressedExceptions();
    }
    throw new Unreachable();
  }

  private void removeLambdaDeserializationMethods() {
    if (lambdaRewriter != null) {
      lambdaRewriter.removeLambdaDeserializationMethods(appView.appInfo().classes());
    }
  }

  private void desugarNestBasedAccess(Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    if (d8NestBasedAccessDesugaring != null) {
      d8NestBasedAccessDesugaring.desugarNestBasedAccess(builder, executorService, this);
    }
  }

  private void synthesizeLambdaClasses(Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    if (lambdaRewriter != null) {
      assert !appView.enableWholeProgramOptimizations();
      lambdaRewriter.finalizeLambdaDesugaringForD8(builder, this, executorService);
    }
  }

  private void staticizeClasses(OptimizationFeedback feedback, ExecutorService executorService)
      throws ExecutionException {
    if (classStaticizer != null) {
      classStaticizer.staticizeCandidates(feedback, executorService);
    }
  }

  private void collectStaticizerCandidates(DexApplication application) {
    if (classStaticizer != null) {
      classStaticizer.collectCandidates(application);
    }
  }

  private void desugarInterfaceMethods(
      Builder<?> builder,
      Flavor includeAllResources,
      ExecutorService executorService)
      throws ExecutionException {
    if (interfaceMethodRewriter != null) {
      interfaceMethodRewriter.desugarInterfaceMethods(
          builder, includeAllResources, executorService);
    }
  }

  private void synthesizeTwrCloseResourceUtilityClass(
      Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    if (twrCloseResourceRewriter != null) {
      twrCloseResourceRewriter.synthesizeUtilityClass(builder, executorService, options);
    }
  }

  private void processSynthesizedJava8UtilityClasses(ExecutorService executorService)
      throws ExecutionException {
    if (backportedMethodRewriter != null) {
      backportedMethodRewriter.processSynthesizedClasses(this, executorService);
    }
  }

  private void synthesizeRetargetClass(Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    if (desugaredLibraryRetargeter != null) {
      desugaredLibraryRetargeter.synthesizeRetargetClasses(builder, executorService, this);
    }
  }

  private void synthesizeEnumUnboxingUtilityMethods(ExecutorService executorService)
      throws ExecutionException {
    if (enumUnboxer != null) {
      enumUnboxer.synthesizeUtilityMethods(this, executorService);
    }
  }

  private void processCovariantReturnTypeAnnotations(Builder<?> builder) {
    if (covariantReturnTypeAnnotationTransformer != null) {
      covariantReturnTypeAnnotationTransformer.process(builder);
    }
  }

  public void convert(AppView<AppInfo> appView, ExecutorService executor)
      throws ExecutionException {
    removeLambdaDeserializationMethods();
    workaroundAbstractMethodOnNonAbstractClassVerificationBug(
        executor, OptimizationFeedbackIgnore.getInstance());
    DexApplication application = appView.appInfo().app();
    timing.begin("IR conversion");
    ThreadUtils.processItems(application.classes(), this::convertMethods, executor);

    // Build a new application with jumbo string info,
    Builder<?> builder = application.builder();
    builder.setHighestSortingString(highestSortingString);

    desugarNestBasedAccess(builder, executor);
    synthesizeLambdaClasses(builder, executor);
    desugarInterfaceMethods(builder, ExcludeDexResources, executor);
    synthesizeTwrCloseResourceUtilityClass(builder, executor);
    processSynthesizedJava8UtilityClasses(executor);
    synthesizeRetargetClass(builder, executor);

    processCovariantReturnTypeAnnotations(builder);
    generateDesugaredLibraryAPIWrappers(builder, executor);

    timing.end();

    DexApplication app = builder.build();
    appView.setAppInfo(
        new AppInfo(
            appView.appInfo().getSyntheticItems().commit(app),
            appView.appInfo().getMainDexClasses()));
  }

  private void convertMethods(DexProgramClass clazz) {
    boolean isReachabilitySensitive = clazz.hasReachabilitySensitiveAnnotation(options.itemFactory);
    // When converting all methods on a class always convert <clinit> first.
    DexEncodedMethod classInitializer = clazz.getClassInitializer();
    if (classInitializer != null) {
      classInitializer
          .getMutableOptimizationInfo()
          .setReachabilitySensitive(isReachabilitySensitive);
      convertMethod(new ProgramMethod(clazz, classInitializer));
    }
    clazz.forEachProgramMethodMatching(
        definition -> !definition.isClassInitializer(),
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          definition.getMutableOptimizationInfo().setReachabilitySensitive(isReachabilitySensitive);
          convertMethod(method);
        });
  }

  private void convertMethod(ProgramMethod method) {
    DexEncodedMethod definition = method.getDefinition();
    if (definition.getCode() == null) {
      return;
    }
    if (!options.methodMatchesFilter(definition)) {
      return;
    }
    checkPrefixMerging(method);
    if (!needsIRConversion(definition.getCode(), method)) {
      return;
    }
    if (options.isGeneratingClassFiles()
        || !(options.passthroughDexCode && definition.getCode().isDexCode())) {
      // We do not process in call graph order, so anything could be a leaf.
      rewriteCode(
          method, simpleOptimizationFeedback, OneTimeMethodProcessor.create(method, appView), null);
    } else {
      assert definition.getCode().isDexCode();
    }
    if (!options.isGeneratingClassFiles()) {
      updateHighestSortingStrings(definition);
    }
  }

  private boolean needsIRConversion(Code code, ProgramMethod method) {
    if (options.testing.forceIRForCfToCfDesugar) {
      return true;
    }
    if (options.isDesugaredLibraryCompilation()) {
      return true;
    }

    if (!options.cfToCfDesugar) {
      return true;
    }
    if (method.getHolder().isInANest()) {
      return true;
    }

    if (desugaredLibraryAPIConverter != null
        && desugaredLibraryAPIConverter.shouldRegisterCallback(method)) {
      return true;
    }

    NeedsIRDesugarUseRegistry useRegistry =
        new NeedsIRDesugarUseRegistry(
            appView,
            backportedMethodRewriter,
            desugaredLibraryRetargeter,
            interfaceMethodRewriter,
            desugaredLibraryAPIConverter);
    method.registerCodeReferences(useRegistry);

    return useRegistry.needsDesugaring();
  }

  private void checkPrefixMerging(ProgramMethod method) {
    if (!appView.options().enableNeverMergePrefixes) {
      return;
    }
    for (DexString neverMergePrefix : neverMergePrefixes) {
      if (method.getHolderType().descriptor.startsWith(neverMergePrefix)) {
        seenNeverMergePrefix.getAndSet(true);
      } else {
        seenNotNeverMergePrefix.getAndSet(true);
      }
      // Don't mix.
      // TODO(b/168001352): Consider requiring that no 'never merge' prefix is ever seen as a
      //  passthrough object.
      if (seenNeverMergePrefix.get() && seenNotNeverMergePrefix.get()) {
        StringBuilder message = new StringBuilder();
        message
            .append("Merging dex file containing classes with prefix")
            .append(neverMergePrefixes.size() > 1 ? "es " : " ");
        for (int i = 0; i < neverMergePrefixes.size(); i++) {
          message
              .append("'")
              .append(neverMergePrefixes.get(0).toString().substring(1).replace('/', '.'))
              .append("'")
              .append(i < neverMergePrefixes.size() - 1 ? ", " : "");
        }
        message.append(" with classes with any other prefixes is not allowed.");
        throw new CompilationError(message.toString());
      }
    }
  }

  private void workaroundAbstractMethodOnNonAbstractClassVerificationBug(
      ExecutorService executorService, OptimizationFeedback feedback) throws ExecutionException {
    if (!options.canHaveDalvikAbstractMethodOnNonAbstractClassVerificationBug()) {
      return;
    }
    assert delayedOptimizationFeedback.noUpdatesLeft();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          if (!clazz.isAbstract()) {
            clazz.forEachMethod(
                method -> {
                  if (method.isAbstract()) {
                    method.accessFlags.unsetAbstract();
                    finalizeEmptyThrowingCode(method, feedback);
                  }
                });
          }
        },
        executorService);
  }

  public DexApplication optimize(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    // Lambda rewriting happens in the enqueuer.
    assert lambdaRewriter == null;

    DexApplication application = appView.appInfo().app();

    computeReachabilitySensitivity(application);
    collectLambdaMergingCandidates(application);
    collectStaticizerCandidates(application);
    workaroundAbstractMethodOnNonAbstractClassVerificationBug(
        executorService, simpleOptimizationFeedback);

    // The process is in two phases in general.
    // 1) Subject all DexEncodedMethods to optimization, except some optimizations that require
    //    reprocessing IR code of methods, e.g., outlining, double-inlining, class staticizer, etc.
    //    - a side effect is candidates for those optimizations are identified.
    // 2) Revisit DexEncodedMethods for the collected candidates.

    printPhase("Primary optimization pass");
    if (fieldAccessAnalysis != null) {
      fieldAccessAnalysis.fieldAssignmentTracker().initialize();
    }

    // Process the application identifying outlining candidates.
    GraphLens initialGraphLensForIR = appView.graphLens();
    GraphLens graphLensForIR = initialGraphLensForIR;
    OptimizationFeedbackDelayed feedback = delayedOptimizationFeedback;
    PostMethodProcessor.Builder postMethodProcessorBuilder =
        new PostMethodProcessor.Builder(getOptimizationsForPostIRProcessing());
    {
      timing.begin("Build primary method processor");
      PrimaryMethodProcessor primaryMethodProcessor =
          PrimaryMethodProcessor.create(
              appView.withLiveness(), postMethodProcessorBuilder, executorService, timing);
      timing.end();
      timing.begin("IR conversion phase 1");
      if (outliner != null) {
        outliner.createOutlineMethodIdentifierGenerator();
      }
      primaryMethodProcessor.forEachMethod(
          (method, methodProcessingId) ->
              processMethod(method, feedback, primaryMethodProcessor, methodProcessingId),
          this::waveStart,
          this::waveDone,
          timing,
          executorService);
      timing.end();
      assert graphLensForIR == appView.graphLens();
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

    if (libraryMethodOverrideAnalysis != null) {
      libraryMethodOverrideAnalysis.finish();
    }

    // Post processing:
    //   1) Second pass for methods whose collected call site information become more precise.
    //   2) Second inlining pass for dealing with double inline callers.
    printPhase("Post optimization pass");
    if (appView.callSiteOptimizationInfoPropagator() != null) {
      postMethodProcessorBuilder.put(appView.callSiteOptimizationInfoPropagator());
    }
    if (inliner != null) {
      postMethodProcessorBuilder.put(inliner);
    }
    if (enumUnboxer != null) {
      enumUnboxer.unboxEnums(postMethodProcessorBuilder, executorService, feedback);
    } else {
      appView.setUnboxedEnums(EnumValueInfoMapCollection.empty());
    }
    if (!options.debug) {
      new TrivialFieldAccessReprocessor(appView.withLiveness(), postMethodProcessorBuilder)
          .run(executorService, feedback, timing);
    }

    timing.begin("IR conversion phase 2");
    graphLensForIR = appView.graphLens();
    PostMethodProcessor postMethodProcessor =
        postMethodProcessorBuilder.build(appView.withLiveness(), executorService, timing);
    if (postMethodProcessor != null) {
      assert !options.debug;
      postMethodProcessor.forEachWaveWithExtension(feedback, executorService);
      feedback.updateVisibleOptimizationInfo();
      assert graphLensForIR == appView.graphLens();
    }
    timing.end();

    // All the code that should be impacted by the lenses inserted between phase 1 and phase 2
    // have now been processed and rewritten, we clear code lens rewriting so that the class
    // staticizer and phase 3 does not perform again the rewriting.
    appView.clearCodeRewritings();

    // TODO(b/112831361): Implement support for staticizeClasses in CF backend.
    if (!options.isGeneratingClassFiles()) {
      printPhase("Class staticizer post processing");
      // TODO(b/127694949): Adapt to PostOptimization.
      staticizeClasses(feedback, executorService);
      feedback.updateVisibleOptimizationInfo();
      // The class staticizer lens shall not be applied through lens code rewriting or it breaks
      // the lambda merger.
      appView.clearCodeRewritings();
    }

    // Build a new application with jumbo string info.
    Builder<?> builder = appView.appInfo().app().builder();
    builder.setHighestSortingString(highestSortingString);

    printPhase("Interface method desugaring");
    desugarInterfaceMethods(builder, IncludeAllResources, executorService);
    feedback.updateVisibleOptimizationInfo();

    printPhase("Utility classes synthesis");
    synthesizeTwrCloseResourceUtilityClass(builder, executorService);
    processSynthesizedJava8UtilityClasses(executorService);
    synthesizeRetargetClass(builder, executorService);
    synthesizeEnumUnboxingUtilityMethods(executorService);

    printPhase("Lambda merging finalization");
    // TODO(b/127694949): Adapt to PostOptimization.
    finalizeLambdaMerging(application, feedback, builder, executorService, initialGraphLensForIR);

    printPhase("Desugared library API Conversion finalization");
    generateDesugaredLibraryAPIWrappers(builder, executorService);

    if (serviceLoaderRewriter != null && serviceLoaderRewriter.getSynthesizedClass() != null) {
      appView.appInfo().addSynthesizedClass(serviceLoaderRewriter.getSynthesizedClass(), true);
      processSynthesizedServiceLoaderMethods(
          serviceLoaderRewriter.getSynthesizedClass(), executorService);
      builder.addSynthesizedClass(serviceLoaderRewriter.getSynthesizedClass());
    }

    // Update optimization info for all synthesized methods at once.
    feedback.updateVisibleOptimizationInfo();

    // TODO(b/127694949): Adapt to PostOptimization.
    if (outliner != null) {
      printPhase("Outlining");
      timing.begin("IR conversion phase 3");
      ProgramMethodSet methodsSelectedForOutlining = outliner.selectMethodsForOutlining();
      if (!methodsSelectedForOutlining.isEmpty()) {
        forEachSelectedOutliningMethod(
            methodsSelectedForOutlining,
            code -> {
              printMethod(code, "IR before outlining (SSA)", null);
              outliner.identifyOutlineSites(code);
            },
            executorService);
        DexProgramClass outlineClass = outliner.buildOutlinerClass(computeOutlineClassType());
        appView.appInfo().addSynthesizedClass(outlineClass, true);
        optimizeSynthesizedClass(outlineClass, executorService);
        forEachSelectedOutliningMethod(
            methodsSelectedForOutlining,
            code -> {
              outliner.applyOutliningCandidate(code);
              printMethod(code, "IR after outlining (SSA)", null);
              removeDeadCodeAndFinalizeIR(
                  code.context(), code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
            },
            executorService);
        feedback.updateVisibleOptimizationInfo();
        assert outliner.checkAllOutlineSitesFoundAgain();
        builder.addSynthesizedClass(outlineClass);
        clearDexMethodCompilationState(outlineClass);
      }
      timing.end();
    }
    clearDexMethodCompilationState();

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInFields(executorService);
    }

    if (Log.ENABLED) {
      if (appView.callSiteOptimizationInfoPropagator() != null) {
        appView.callSiteOptimizationInfoPropagator().logResults();
      }
      constantCanonicalizer.logResults();
      if (idempotentFunctionCallCanonicalizer != null) {
        idempotentFunctionCallCanonicalizer.logResults();
      }
      if (libraryMethodOverrideAnalysis != null) {
        libraryMethodOverrideAnalysis.logResults();
      }
      if (stringOptimizer != null) {
        stringOptimizer.logResult();
      }
      if (stringBuilderOptimizer != null) {
        stringBuilderOptimizer.logResults();
      }
    }

    // Assure that no more optimization feedback left after post processing.
    assert feedback.noUpdatesLeft();
    assert checkLegacySyntheticsAreInBuilder(appView, builder);
    return builder.build();
  }

  private boolean checkLegacySyntheticsAreInBuilder(
      AppView<AppInfoWithLiveness> appView, Builder<?> builder) {
    Collection<DexProgramClass> inAppInfo =
        appView.appInfo().getSyntheticItems().getLegacyPendingClasses();
    Collection<DexProgramClass> inBuilder = builder.getSynthesizedClasses();
    assert inAppInfo.containsAll(inBuilder);
    assert inBuilder.containsAll(inAppInfo);
    return true;
  }

  private void waveStart(ProgramMethodSet wave) {
    onWaveDoneActions = Collections.synchronizedList(new ArrayList<>());
  }

  private void waveDone(ProgramMethodSet wave) {
    delayedOptimizationFeedback.refineAppInfoWithLiveness(appView.appInfo().withLiveness());
    delayedOptimizationFeedback.updateVisibleOptimizationInfo();
    if (options.enableFieldAssignmentTracker) {
      fieldAccessAnalysis.fieldAssignmentTracker().waveDone(wave, delayedOptimizationFeedback);
    }
    assert delayedOptimizationFeedback.noUpdatesLeft();
    onWaveDoneActions.forEach(com.android.tools.r8.utils.Action::execute);
    onWaveDoneActions = null;
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

  private void computeReachabilitySensitivity(DexApplication application) {
    application.classes().forEach(c -> {
      if (c.hasReachabilitySensitiveAnnotation(options.itemFactory)) {
        c.methods().forEach(m -> m.getMutableOptimizationInfo().setReachabilitySensitive(true));
      }
    });
  }

  private void forEachSelectedOutliningMethod(
      ProgramMethodSet methodsSelectedForOutlining,
      Consumer<IRCode> consumer,
      ExecutorService executorService)
      throws ExecutionException {
    assert !options.skipIR;
    ThreadUtils.processItems(
        methodsSelectedForOutlining,
        method -> {
          IRCode code = method.buildIR(appView);
          assert code != null;
          assert !method.getDefinition().getCode().isOutlineCode();
          // Instead of repeating all the optimizations of rewriteCode(), only run the
          // optimizations needed for outlining: rewriteMoveResult() to remove out-values on
          // StringBuilder/StringBuffer method invocations, and removeDeadCode() to remove
          // unused out-values.
          codeRewriter.rewriteMoveResult(code);
          deadCodeRemover.run(code, Timing.empty());
          CodeRewriter.removeAssumeInstructions(appView, code);
          consumer.accept(code);
        },
        executorService);
  }

  private void processSynthesizedServiceLoaderMethods(
      DexProgramClass synthesizedClass, ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        synthesizedClass::forEachProgramMethod,
        this::forEachSynthesizedServiceLoaderMethod,
        executorService);
  }

  private void forEachSynthesizedServiceLoaderMethod(ProgramMethod method) {
    IRCode code = method.buildIR(appView);
    assert code != null;
    codeRewriter.rewriteMoveResult(code);
    removeDeadCodeAndFinalizeIR(
        method, code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  private void collectLambdaMergingCandidates(DexApplication application) {
    if (lambdaMerger != null) {
      lambdaMerger.collectGroupCandidates(application);
    }
  }

  private void finalizeLambdaMerging(
      DexApplication application,
      OptimizationFeedback feedback,
      Builder<?> builder,
      ExecutorService executorService,
      GraphLens appliedGraphLens)
      throws ExecutionException {
    if (lambdaMerger != null) {
      lambdaMerger.applyLambdaClassMapping(
          application, this, feedback, builder, executorService, appliedGraphLens);
    } else {
      appView.setHorizontallyMergedLambdaClasses(HorizontallyMergedLambdaClasses.empty());
    }
    assert appView.horizontallyMergedLambdaClasses() != null;
  }

  private void generateDesugaredLibraryAPIWrappers(
      DexApplication.Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    if (desugaredLibraryAPIConverter != null) {
      desugaredLibraryAPIConverter.finalizeWrappers(builder, this, executorService);
    }
  }

  private void clearDexMethodCompilationState() {
    appView.appInfo().classes().forEach(this::clearDexMethodCompilationState);
  }

  private void clearDexMethodCompilationState(DexProgramClass clazz) {
    clazz.forEachMethod(DexEncodedMethod::markNotProcessed);
  }

  /**
   * This will replace the Dex code in the method with the Dex code generated from the provided IR.
   * <p>
   * This method is *only* intended for testing, where tests manipulate the IR and need runnable Dex
   * code.
   *
   * @param method the method to replace code for
   * @param code the IR code for the method
   */
  public void replaceCodeForTesting(DexEncodedMethod method, IRCode code) {
    if (Log.ENABLED) {
      Log.debug(getClass(), "Initial (SSA) flow graph for %s:\n%s", method.toSourceString(), code);
    }
    assert code.isConsistentSSA();
    Timing timing = Timing.empty();
    deadCodeRemover.run(code, timing);
    code.traceBlocks();
    RegisterAllocator registerAllocator = performRegisterAllocation(code, method, timing);
    method.setCode(code, registerAllocator, appView);
    if (Log.ENABLED) {
      Log.debug(getClass(), "Resulting dex code for %s:\n%s",
          method.toSourceString(), logCode(options, method));
    }
  }

  // Find an unused name for the outlining class. When multiple runs produces additional
  // outlining the default outlining class might already be present.
  private DexType computeOutlineClassType() {
    DexType result;
    int count = 0;
    String prefix = appView.options().synthesizedClassPrefix.replace('/', '.');
    do {
      String name =
          prefix + OutlineOptions.CLASS_NAME + (count == 0 ? "" : Integer.toString(count));
      count++;
      result = appView.dexItemFactory().createType(DescriptorUtils.javaTypeToDescriptor(name));

    } while (appView.appInfo().definitionForWithoutExistenceAssert(result) != null);
    return result;
  }

  public void optimizeSynthesizedClass(
      DexProgramClass clazz, ExecutorService executorService)
      throws ExecutionException {
    // Process the generated class, but don't apply any outlining.
    SortedProgramMethodSet methods = SortedProgramMethodSet.create(clazz::forEachProgramMethod);
    processMethodsConcurrently(methods, executorService);
  }

  public void optimizeSynthesizedClasses(
      Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    SortedProgramMethodSet methods = SortedProgramMethodSet.create();
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethod(methods::add);
    }
    processMethodsConcurrently(methods, executorService);
  }

  public void optimizeSynthesizedMethod(ProgramMethod synthesizedMethod) {
    if (!synthesizedMethod.getDefinition().isProcessed()) {
      // Process the generated method, but don't apply any outlining.
      OneTimeMethodProcessor methodProcessor =
          OneTimeMethodProcessor.create(synthesizedMethod, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingId) ->
              processMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingId));
    }
  }

  public void processMethodsConcurrently(
      SortedProgramMethodSet wave, ExecutorService executorService) throws ExecutionException {
    if (!wave.isEmpty()) {
      OneTimeMethodProcessor methodProcessor = OneTimeMethodProcessor.create(wave, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingId) ->
              processMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingId),
          executorService);
    }
  }

  private String logCode(InternalOptions options, DexEncodedMethod method) {
    return options.useSmaliSyntax ? method.toSmaliString(null) : method.codeToString();
  }

  List<CodeOptimization> getOptimizationsForPrimaryIRProcessing() {
    // TODO(b/140766440): Remove unnecessary steps once all sub steps are converted.
    return ImmutableList.of(this::optimize);
  }

  List<CodeOptimization> getOptimizationsForPostIRProcessing() {
    // TODO(b/140766440): Remove unnecessary steps once all sub steps are converted.
    return ImmutableList.of(this::optimize);
  }

  // TODO(b/140766440): Make this receive a list of CodeOptimizations to conduct.
  public Timing processMethod(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId) {
    DexEncodedMethod definition = method.getDefinition();
    Code code = definition.getCode();
    boolean matchesMethodFilter = options.methodMatchesFilter(definition);
    if (code != null && matchesMethodFilter) {
      return rewriteCode(method, feedback, methodProcessor, methodProcessingId);
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

  private Timing rewriteCode(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId) {
    return ExceptionUtils.withOriginAndPositionAttachmentHandler(
        method.getOrigin(),
        new MethodPosition(method.getReference().asMethodReference()),
        () -> rewriteCodeInternal(method, feedback, methodProcessor, methodProcessingId));
  }

  private Timing rewriteCodeInternal(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId) {
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
    boolean didDesugar = desugar(method);
    if (Log.ENABLED && didDesugar) {
      Log.debug(
          getClass(),
          "Desugared code for %s:\n%s",
          method.toSourceString(),
          logCode(options, method.getDefinition()));
    }
    if (options.testing.hookInIrConversion != null) {
      options.testing.hookInIrConversion.run();
    }
    if (options.skipIR) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return Timing.empty();
    }
    IRCode code = method.buildIR(appView);
    if (code == null) {
      feedback.markProcessed(method.getDefinition(), ConstraintWithTarget.NEVER);
      return Timing.empty();
    }
    return optimize(code, feedback, methodProcessor, methodProcessingId);
  }

  private boolean desugar(ProgramMethod method) {
    if (options.desugarState != DesugarState.ON) {
      return false;
    }
    if (!method.getDefinition().getCode().isCfCode()) {
      return false;
    }
    boolean didDesugar = false;
    Supplier<AppInfoWithClassHierarchy> lazyAppInfo =
        Suppliers.memoize(() -> appView.appInfoForDesugaring());
    if (lambdaRewriter != null) {
      didDesugar |= lambdaRewriter.desugarLambdas(method, lazyAppInfo.get()) > 0;
    }
    if (backportedMethodRewriter != null) {
      didDesugar |= backportedMethodRewriter.desugar(method, lazyAppInfo.get());
    }
    return didDesugar;
  }

  // TODO(b/140766440): Convert all sub steps an implementer of CodeOptimization
  private Timing optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId) {
    ProgramMethod context = code.context();
    DexEncodedMethod method = context.getDefinition();
    DexProgramClass holder = context.getHolder();
    assert holder != null;

    Timing timing = Timing.create(method.qualifiedName(), options);

    if (Log.ENABLED) {
      Log.debug(getClass(), "Initial (SSA) flow graph for %s:\n%s", method.toSourceString(), code);
    }
    // Compilation header if printing CFGs for this method.
    printC1VisualizerHeader(method);
    String previous = printMethod(code, "Initial IR (SSA)", null);

    if (options.testing.irModifier != null) {
      options.testing.irModifier.accept(code);
    }

    if (options.canHaveArtStringNewInitBug()) {
      timing.begin("Check for new-init issue");
      CodeRewriter.ensureDirectStringNewToInit(code, appView.dexItemFactory());
      timing.end();
    }

    boolean isDebugMode = options.debug || method.getOptimizationInfo().isReachabilitySensitive();

    if (isDebugMode) {
      codeRewriter.simplifyDebugLocals(code);
    }

    if (appView.graphLens().hasCodeRewritings()) {
      assert lensCodeRewriter != null;
      timing.begin("Lens rewrite");
      lensCodeRewriter.rewrite(code, context);
      timing.end();
    }

    assert !method.isProcessed() || !isDebugMode;
    assert !method.isProcessed()
        || !appView.enableWholeProgramOptimizations()
        || !appView.appInfo().withLiveness().neverReprocess.contains(method.method);

    if (lambdaMerger != null) {
      timing.begin("Merge lambdas");
      lambdaMerger.rewriteCode(code.context(), code, inliner, methodProcessor);
      timing.end();
      assert code.isConsistentSSA();
    }

    if (typeChecker != null && !typeChecker.check(code)) {
      assert appView.enableWholeProgramOptimizations();
      assert options.testing.allowTypeErrors;
      StringDiagnostic warning =
          new StringDiagnostic(
              "The method `"
                  + method.toSourceString()
                  + "` does not type check and will be assumed to be unreachable.");
      options.reporter.warning(warning);
      finalizeEmptyThrowingCode(method, feedback);
      return timing;
    }

    // This is the first point in time where we can assert that the types are sound. If this
    // assert fails, then the types that we have inferred are unsound, or the method does not type
    // check. In the latter case, the type checker should be extended to detect the issue such that
    // we will return with finalizeEmptyThrowingCode() above.
    assert code.verifyTypes(appView);
    assert code.isConsistentSSA();

    if (appView.isCfByteCodePassThrough(method)) {
      // If the code is pass trough, do not finalize by overwriting the existing code.
      assert appView.enableWholeProgramOptimizations();
      timing.begin("Collect optimization info");
      collectOptimizationInfo(
          context, code, ClassInitializerDefaultsResult.empty(), feedback, methodProcessor, timing);
      timing.end();
      return timing;
    }

    assertionsRewriter.run(method, code, timing);

    if (serviceLoaderRewriter != null) {
      assert appView.appInfo().hasLiveness();
      timing.begin("Rewrite service loaders");
      serviceLoaderRewriter.rewrite(code, methodProcessingId);
      timing.end();
    }

    if (identifierNameStringMarker != null) {
      timing.begin("Decouple identifier-name strings");
      identifierNameStringMarker.decoupleIdentifierNameStringsInMethod(code);
      timing.end();
      assert code.isConsistentSSA();
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

    previous = printMethod(code, "IR after disable assertions (SSA)", previous);

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

    if (!isDebugMode && options.enableInlining && inliner != null) {
      timing.begin("Inlining");
      inliner.performInlining(code.context(), code, feedback, methodProcessor, timing);
      timing.end();
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after inlining (SSA)", previous);

    if (appView.appInfo().hasLiveness()) {
      // Reflection optimization 1. getClass() / forName() -> const-class
      timing.begin("Rewrite to const class");
      ReflectionOptimizer.rewriteGetClassOrForNameToConstClass(
          appView.withLiveness(), code, mainDexClasses);
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
          .optimize(code, feedback, methodProcessor, methodProcessingId);
      timing.end();
      assert code.isConsistentSSA();
    }

    assert code.verifyTypes(appView);

    if (devirtualizer != null) {
      assert code.verifyTypes(appView);
      timing.begin("Devirtualize invoke interface");
      devirtualizer.devirtualizeInvokeInterface(code);
      timing.end();
    }

    assert code.verifyTypes(appView);

    timing.begin("Remove trivial type checks/casts");
    codeRewriter.removeTrivialCheckCastAndInstanceOfInstructions(
        code, context, methodProcessor, methodProcessingId);
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
    timing.begin("Rewrite AssertionError");
    codeRewriter.rewriteAssertionErrorTwoArgumentConstructor(code, options);
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
    // TODO(b/114002137): for now, string concatenation depends on rewriteMoveResult.
    if (options.enableStringConcatenationOptimization
        && !isDebugMode
        && options.isGeneratingDex()) {
      timing.begin("Rewrite string concat");
      stringBuilderOptimizer.computeTrivialStringConcatenation(code);
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
          code, context, methodProcessor, methodProcessingId);
      timing.end();
    }
    timing.end();
    if (options.enableRedundantConstNumberOptimization) {
      timing.begin("Remove const numbers");
      codeRewriter.redundantConstNumberRemoval(code);
      timing.end();
    }
    if (RedundantFieldLoadElimination.shouldRun(appView, code)) {
      timing.begin("Remove field loads");
      new RedundantFieldLoadElimination(appView, code).run();
      timing.end();
    }

    if (options.testing.invertConditionals) {
      invertConditionalsForTesting(code);
    }

    if (!isDebugMode) {
      timing.begin("Rewrite throw NPE");
      codeRewriter.rewriteThrowNullPointerException(code);
      timing.end();
    }

    timing.begin("Optimize class initializers");
    ClassInitializerDefaultsResult classInitializerDefaultsResult =
        classInitializerDefaultsOptimization.optimize(code, feedback);
    timing.end();

    if (Log.ENABLED) {
      Log.debug(getClass(), "Intermediate (SSA) flow graph for %s:\n%s",
          method.toSourceString(), code);
    }
    // Dead code removal. Performed after simplifications to remove code that becomes dead
    // as a result of those simplifications. The following optimizations could reveal more
    // dead code which is removed right before register allocation in performRegisterAllocation.
    deadCodeRemover.run(code, timing);
    assert code.isConsistentSSA();

    if (options.desugarState == DesugarState.ON && enableTryWithResourcesDesugaring()) {
      timing.begin("Rewrite Throwable suppresed methods");
      codeRewriter.rewriteThrowableAddAndGetSuppressed(code);
      timing.end();
    }

    if (desugaredLibraryRetargeter != null) {
      // The desugaredLibraryRetargeter should run before backportedMethodRewriter to be able to
      // perform backport rewriting before the methods can be retargeted.
      timing.begin("Retarget library methods");
      desugaredLibraryRetargeter.desugar(code);
      timing.end();
    }

    timing.begin("Desugar string concat");
    stringConcatRewriter.desugarStringConcats(method.method, code);
    timing.end();

    previous = printMethod(code, "IR after lambda desugaring (SSA)", previous);

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR before class inlining (SSA)", previous);

    if (classInliner != null) {
      timing.begin("Inline classes");
      // Class inliner should work before lambda merger, so if it inlines the
      // lambda, it does not get collected by merger.
      assert options.enableInlining && inliner != null;
      classInliner.processMethodCode(
          appView.withLiveness(),
          codeRewriter,
          stringOptimizer,
          enumValueOptimizer,
          code.context(),
          code,
          feedback,
          methodProcessor,
          methodProcessingId,
          inliner,
          Suppliers.memoize(
              () ->
                  inliner.createDefaultOracle(
                      code.context(),
                      methodProcessor,
                      options.classInliningInstructionLimit,
                      // Inlining instruction allowance is not needed for the class inliner since it
                      // always uses a force inlining oracle for inlining.
                      -1)));
      timing.end();
      assert code.isConsistentSSA();
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after class inlining (SSA)", previous);

    if (d8NestBasedAccessDesugaring != null) {
      timing.begin("Desugar nest access");
      d8NestBasedAccessDesugaring.rewriteNestBasedAccesses(context, code, appView);
      timing.end();
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after nest based access desugaring (SSA)", previous);

    if (interfaceMethodRewriter != null) {
      timing.begin("Rewrite interface methods");
      interfaceMethodRewriter.rewriteMethodReferences(method, code);
      timing.end();
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after interface method rewriting (SSA)", previous);

    // This pass has to be after interfaceMethodRewriter and BackportedMethodRewriter.
    if (desugaredLibraryAPIConverter != null
        && (!appView.enableWholeProgramOptimizations() || methodProcessor.isPrimary())) {
      timing.begin("Desugar library API");
      desugaredLibraryAPIConverter.desugar(code);
      timing.end();
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after desugared library API Conversion (SSA)", previous);

    if (twrCloseResourceRewriter != null) {
      timing.begin("Rewrite TWR close");
      twrCloseResourceRewriter.rewriteMethodCode(code);
      timing.end();
    }

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR after twr close resource rewriter (SSA)", previous);

    if (lambdaMerger != null) {
      timing.begin("Analyze lambda merging");
      lambdaMerger.analyzeCode(code.context(), code);
      timing.end();
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after lambda merger (SSA)", previous);

    // TODO(b/140766440): an ideal solution would be puttting CodeOptimization for this into
    //  the list for primary processing only.
    if (options.outline.enabled && outliner != null && methodProcessor.isPrimary()) {
      timing.begin("Identify outlines");
      outliner.getOutlineMethodIdentifierGenerator().accept(code);
      timing.end();
      assert code.isConsistentSSA();
    }

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR after outline handler (SSA)", previous);

    if (stringSwitchRemover != null) {
      // Remove string switches prior to canonicalization to ensure that the constants that are
      // being introduced will be canonicalized if possible.
      timing.begin("Remove string switch");
      stringSwitchRemover.run(code);
      timing.end();
    }

    // TODO(mkroghj) Test if shorten live ranges is worth it.
    if (!options.isGeneratingClassFiles()) {
      timing.begin("Canonicalize constants");
      constantCanonicalizer.canonicalize(appView, code);
      timing.end();
      timing.begin("Create constants for literal instructions");
      codeRewriter.useDedicatedConstantForLitInstruction(code);
      timing.end();
      timing.begin("Shorten live ranges");
      codeRewriter.shortenLiveRanges(code);
      timing.end();
    }

    timing.begin("Canonicalize idempotent calls");
    idempotentFunctionCallCanonicalizer.canonicalize(code);
    timing.end();

    previous =
        printMethod(code, "IR after idempotent function call canonicalization (SSA)", previous);

    // Insert code to log arguments if requested.
    if (options.methodMatchesLogArgumentsFilter(method)) {
      codeRewriter.logArgumentTypes(method, code);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after argument type logging (SSA)", previous);

    if (classStaticizer != null) {
      timing.begin("Identify staticizing candidates");
      classStaticizer.examineMethodCode(code);
      timing.end();
    }

    if (enumUnboxer != null && methodProcessor.isPrimary()) {
      enumUnboxer.analyzeEnums(code);
    }

    assert code.verifyTypes(appView);

    deadCodeRemover.run(code, timing);

    if (appView.enableWholeProgramOptimizations()) {
      timing.begin("Collect optimization info");
      collectOptimizationInfo(
          context, code, classInitializerDefaultsResult, feedback, methodProcessor, timing);
      timing.end();
    }

    if (assumeInserter != null) {
      timing.begin("Remove assume instructions");
      CodeRewriter.removeAssumeInstructions(appView, code);
      timing.end();
      assert code.isConsistentSSA();
    }

    // Assert that we do not have unremoved non-sense code in the output, e.g., v <- non-null NULL.
    assert code.verifyNoNullabilityBottomTypes();

    assert code.verifyTypes(appView);

    previous =
        printMethod(code, "IR after computation of optimization info summary (SSA)", previous);

    if (options.canHaveNumberConversionRegisterAllocationBug()) {
      timing.begin("Check number conversion issue");
      codeRewriter.workaroundNumberConversionRegisterAllocationBug(code);
      timing.end();
    }

    printMethod(code, "Optimized IR (SSA)", previous);
    timing.begin("Finalize IR");
    finalizeIR(code, feedback, timing);
    timing.end();
    return timing;
  }

  // Compute optimization info summary for the current method unless it is pinned
  // (in that case we should not be making any assumptions about the behavior of the method).
  public void collectOptimizationInfo(
      ProgramMethod method,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      Timing timing) {
    if (libraryMethodOverrideAnalysis != null) {
      timing.begin("Analyze library method overrides");
      libraryMethodOverrideAnalysis.analyze(code);
      timing.end();
    }

    if (fieldAccessAnalysis != null) {
      timing.begin("Analyze field accesses");
      fieldAccessAnalysis.recordFieldAccesses(code, feedback, methodProcessor);
      if (classInitializerDefaultsResult != null) {
        fieldAccessAnalysis.acceptClassInitializerDefaultsResult(classInitializerDefaultsResult);
      }
      timing.end();
    }

    // Arguments can be changed during the debug mode.
    boolean isDebugMode =
        options.debug || method.getDefinition().getOptimizationInfo().isReachabilitySensitive();
    if (!isDebugMode && appView.callSiteOptimizationInfoPropagator() != null) {
      timing.begin("Collect call-site info");
      appView.callSiteOptimizationInfoPropagator().collectCallSiteOptimizationInfo(code, timing);
      timing.end();
    }

    if (appView.appInfo().withLiveness().isPinned(code.method().method)) {
      return;
    }

    InstanceFieldInitializationInfoCollection instanceFieldInitializationInfos = null;
    if (method.getDefinition().isInitializer()) {
      if (method.getDefinition().isClassInitializer()) {
        StaticFieldValueAnalysis.run(
            appView, code, classInitializerDefaultsResult, feedback, timing);
      } else {
        instanceFieldInitializationInfos =
            InstanceFieldValueAnalysis.run(
                appView, code, classInitializerDefaultsResult, feedback, timing);
      }
    }
    methodOptimizationInfoCollector.collectMethodOptimizationInfo(
        method, code, feedback, dynamicTypeOptimization, instanceFieldInitializationInfos, timing);
  }

  public void removeDeadCodeAndFinalizeIR(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    if (stringSwitchRemover != null) {
      stringSwitchRemover.run(code);
    }
    deadCodeRemover.run(code, timing);
    finalizeIR(code, feedback, timing);
  }

  public void finalizeIR(IRCode code, OptimizationFeedback feedback, Timing timing) {
    code.traceBlocks();
    if (options.isGeneratingClassFiles()) {
      finalizeToCf(code, feedback);
    } else {
      assert options.isGeneratingDex();
      finalizeToDex(code, feedback, timing);
    }
  }

  private void finalizeEmptyThrowingCode(DexEncodedMethod method, OptimizationFeedback feedback) {
    assert options.isGeneratingClassFiles() || options.isGeneratingDex();
    Code emptyThrowingCode = method.buildEmptyThrowingCode(options);
    method.setCode(emptyThrowingCode, appView);
    feedback.markProcessed(method, ConstraintWithTarget.ALWAYS);
  }

  private void finalizeToCf(IRCode code, OptimizationFeedback feedback) {
    DexEncodedMethod method = code.method();
    assert !method.getCode().isDexCode();
    CfBuilder builder = new CfBuilder(appView, method, code);
    CfCode result = builder.build(deadCodeRemover);
    method.setCode(result, appView);
    markProcessed(code, feedback);
  }

  private void finalizeToDex(IRCode code, OptimizationFeedback feedback, Timing timing) {
    DexEncodedMethod method = code.method();
    // Workaround massive dex2oat memory use for self-recursive methods.
    CodeRewriter.disableDex2OatInliningForSelfRecursiveMethods(appView, code);
    // Perform register allocation.
    RegisterAllocator registerAllocator = performRegisterAllocation(code, method, timing);
    timing.begin("Build DEX code");
    method.setCode(code, registerAllocator, appView);
    timing.end();
    updateHighestSortingStrings(method);
    if (Log.ENABLED) {
      Log.debug(getClass(), "Resulting dex code for %s:\n%s",
          method.toSourceString(), logCode(options, method));
    }
    printMethod(code, "Final IR (non-SSA)", null);
    timing.begin("Marking processed");
    markProcessed(code, feedback);
    timing.end();
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
    if (!options.enableInlining || inliner == null) {
      return false;
    }
    DexEncodedMethod definition = method.getDefinition();
    if (definition.isClassInitializer()
        || definition.getOptimizationInfo().isReachabilitySensitive()) {
      return false;
    }
    if (appView.appInfo().hasLiveness()
        && appView.appInfo().withLiveness().isPinned(method.getReference())) {
      return false;
    }
    return true;
  }

  private synchronized void updateHighestSortingStrings(DexEncodedMethod method) {
    DexString highestSortingReferencedString = method.getCode().asDexCode().highestSortingString;
    if (highestSortingReferencedString != null) {
      if (highestSortingString == null
          || highestSortingReferencedString.slowCompareTo(highestSortingString) > 0) {
        highestSortingString = highestSortingReferencedString;
      }
    }
  }

  private RegisterAllocator performRegisterAllocation(
      IRCode code, DexEncodedMethod method, Timing timing) {
    // Always perform dead code elimination before register allocation. The register allocator
    // does not allow dead code (to make sure that we do not waste registers for unneeded values).
    assert deadCodeRemover.verifyNoDeadCode(code);
    materializeInstructionBeforeLongOperationsWorkaround(code);
    workaroundForwardingInitializerBug(code);
    timing.begin("Allocate registers");
    LinearScanRegisterAllocator registerAllocator = new LinearScanRegisterAllocator(appView, code);
    registerAllocator.allocateRegisters();
    timing.end();
    if (options.canHaveExceptionTargetingLoopHeaderBug()) {
      codeRewriter.workaroundExceptionTargetingLoopHeaderBug(code);
    }
    printMethod(code, "After register allocation (non-SSA)", null);
    timing.begin("Peephole optimize");
    for (int i = 0; i < PEEPHOLE_OPTIMIZATION_PASSES; i++) {
      CodeRewriter.collapseTrivialGotos(code);
      PeepholeOptimizer.optimize(code, registerAllocator);
    }
    timing.end();
    timing.begin("Clean up");
    CodeRewriter.removeUnneededMovesOnExitingPaths(code, registerAllocator);
    CodeRewriter.collapseTrivialGotos(code);
    timing.end();
    if (Log.ENABLED) {
      Log.debug(getClass(), "Final (non-SSA) flow graph for %s:\n%s",
          method.toSourceString(), code);
    }
    return registerAllocator;
  }

  private void workaroundForwardingInitializerBug(IRCode code) {
    if (!options.canHaveForwardingInitInliningBug()) {
      return;
    }
    // Only constructors.
    if (!code.method().isInstanceInitializer()) {
      return;
    }
    // Only constructors with certain signatures.
    DexTypeList paramTypes = code.method().method.proto.parameters;
    if (paramTypes.size() != 3 ||
        paramTypes.values[0] != options.itemFactory.doubleType ||
        paramTypes.values[1] != options.itemFactory.doubleType ||
        !paramTypes.values[2].isClassType()) {
      return;
    }
    // Only if the constructor contains a super constructor call taking only parameters as
    // inputs.
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      Instruction superConstructorCall =
          it.nextUntil(
              (i) ->
                  i.isInvokeDirect()
                      && i.asInvokeDirect().getInvokedMethod().name
                          == options.itemFactory.constructorMethodName
                      && i.asInvokeDirect().arguments().size() == 4
                      && i.asInvokeDirect().arguments().stream().allMatch(Value::isArgument));
      if (superConstructorCall != null) {
        // We force a materializing const instruction in front of the super call to make
        // sure that there is at least one temporary register in the method. That disables
        // the inlining that is crashing on these devices.
        ensureInstructionBefore(code, superConstructorCall, it);
        break;
      }
    }
  }

  /**
   * For each block, we look to see if the header matches:
   *
   * <pre>
   *   pseudo-instructions*
   *   v2 <- long-{mul,div} v0 v1
   *   pseudo-instructions*
   *   v5 <- long-{add,sub} v3 v4
   * </pre>
   *
   * where v2 ~=~ v3 or v2 ~=~ v4 (with ~=~ being equal or an alias of) and the block is not a
   * fallthrough target.
   */
  private void materializeInstructionBeforeLongOperationsWorkaround(IRCode code) {
    if (!options.canHaveDex2OatLinkedListBug()) {
      return;
    }
    DexItemFactory factory = options.itemFactory;
    final Supplier<DexMethod> javaLangLangSignum =
        Suppliers.memoize(
            () ->
                factory.createMethod(
                    factory.createString("Ljava/lang/Long;"),
                    factory.createString("signum"),
                    factory.intDescriptor,
                    new DexString[] {factory.longDescriptor}));
    for (BasicBlock block : code.blocks) {
      InstructionListIterator it = block.listIterator(code);
      Instruction firstMaterializing = it.nextUntil(IRConverter::isNotPseudoInstruction);
      if (!isLongMul(firstMaterializing)) {
        continue;
      }
      Instruction secondMaterializing = it.nextUntil(IRConverter::isNotPseudoInstruction);
      if (!isLongAddOrSub(secondMaterializing)) {
        continue;
      }
      if (isFallthoughTarget(block)) {
        continue;
      }
      Value outOfMul = firstMaterializing.outValue();
      for (Value inOfAddOrSub : secondMaterializing.inValues()) {
        if (isAliasOf(inOfAddOrSub, outOfMul)) {
          it = block.listIterator(code);
          it.nextUntil(i -> i == firstMaterializing);
          Value longValue = firstMaterializing.inValues().get(0);
          InvokeStatic invokeLongSignum =
              new InvokeStatic(
                  javaLangLangSignum.get(), null, Collections.singletonList(longValue));
          ensureThrowingInstructionBefore(code, firstMaterializing, it, invokeLongSignum);
          return;
        }
      }
    }
  }

  private static boolean isAliasOf(Value usedValue, Value definingValue) {
    while (true) {
      if (usedValue == definingValue) {
        return true;
      }
      Instruction definition = usedValue.definition;
      if (definition == null || !definition.isMove()) {
        return false;
      }
      usedValue = definition.asMove().src();
    }
  }

  private static boolean isNotPseudoInstruction(Instruction instruction) {
    return !(instruction.isDebugInstruction() || instruction.isMove());
  }

  private static boolean isLongMul(Instruction instruction) {
    return instruction != null
        && instruction.isMul()
        && instruction.asBinop().getNumericType() == NumericType.LONG
        && instruction.outValue() != null;
  }

  private static boolean isLongAddOrSub(Instruction instruction) {
    return instruction != null
        && (instruction.isAdd() || instruction.isSub())
        && instruction.asBinop().getNumericType() == NumericType.LONG;
  }

  private static boolean isFallthoughTarget(BasicBlock block) {
    for (BasicBlock pred : block.getPredecessors()) {
      if (pred.exit().fallthroughBlock() == block) {
        return true;
      }
    }
    return false;
  }

  private void ensureThrowingInstructionBefore(
      IRCode code, Instruction addBefore, InstructionListIterator it, Instruction instruction) {
    Instruction check = it.previous();
    assert addBefore == check;
    BasicBlock block = check.getBlock();
    if (block.hasCatchHandlers()) {
      // Split so the existing instructions retain their handlers and the new instruction has none.
      BasicBlock split = it.split(code);
      assert split.hasCatchHandlers();
      assert !block.hasCatchHandlers();
      it = block.listIterator(code, block.getInstructions().size() - 1);
    }
    instruction.setPosition(addBefore.getPosition());
    it.add(instruction);
  }

  private static void ensureInstructionBefore(
      IRCode code, Instruction addBefore, InstructionListIterator it) {
    // Force materialize a constant-zero before the long operation.
    Instruction check = it.previous();
    assert addBefore == check;
    // Forced definition of const-zero
    Value fixitValue = code.createValue(TypeElement.getInt());
    Instruction fixitDefinition = new AlwaysMaterializingDefinition(fixitValue);
    fixitDefinition.setBlock(addBefore.getBlock());
    fixitDefinition.setPosition(addBefore.getPosition());
    it.add(fixitDefinition);
    // Forced user of the forced definition to ensure it has a user and thus live range.
    Instruction fixitUser = new AlwaysMaterializingUser(fixitValue);
    fixitUser.setBlock(addBefore.getBlock());
    fixitUser.setPosition(addBefore.getPosition());
    it.add(fixitUser);
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

  private void printPhase(String phase) {
    if (!options.extensiveLoggingFilter.isEmpty()) {
      System.out.println("Entering phase: " + phase);
    }
  }

  private String printMethod(IRCode code, String title, String previous) {
    if (printer != null) {
      printer.resetUnusedValue();
      printer.begin("cfg");
      printer.print("name \"").append(title).append("\"\n");
      code.print(printer);
      printer.end("cfg");
    }
    if (options.extensiveLoggingFilter.size() > 0
        && options.extensiveLoggingFilter.contains(code.method().method.toSourceString())) {
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
}
