// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.fieldaccess.FieldAccessAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.InstanceFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValueAnalysis;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.ArrayConstructionSimplifier;
import com.android.tools.r8.ir.conversion.passes.BinopRewriter;
import com.android.tools.r8.ir.conversion.passes.BranchSimplifier;
import com.android.tools.r8.ir.conversion.passes.CommonSubexpressionElimination;
import com.android.tools.r8.ir.conversion.passes.DexConstantOptimizer;
import com.android.tools.r8.ir.conversion.passes.NaturalIntLoopRemover;
import com.android.tools.r8.ir.conversion.passes.ParentConstructorHoistingCodeRewriter;
import com.android.tools.r8.ir.conversion.passes.SplitBranch;
import com.android.tools.r8.ir.conversion.passes.ThrowCatchOptimizer;
import com.android.tools.r8.ir.conversion.passes.TrivialCheckCastAndInstanceOfRemover;
import com.android.tools.r8.ir.conversion.passes.TrivialPhiSimplifier;
import com.android.tools.r8.ir.desugar.CfInstructionDesugaringCollection;
import com.android.tools.r8.ir.desugar.CovariantReturnTypeAnnotationTransformer;
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
import com.android.tools.r8.ir.optimize.RedundantFieldLoadAndStoreElimination;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer;
import com.android.tools.r8.ir.optimize.RemoveVerificationErrorForUnknownReturnedValues;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriter;
import com.android.tools.r8.ir.optimize.api.InstanceInitializerOutliner;
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
import com.android.tools.r8.lightir.IR2LirConverter;
import com.android.tools.r8.lightir.Lir2IRConverter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.lightir.LirStrategy.ExternalPhisStrategy;
import com.android.tools.r8.lightir.PhiInInstructionsStrategy;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorIROptimizer;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.shaking.LibraryMethodOverrideAnalysis;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.NeverMergeGroup;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class IRConverter {

  public final AppView<?> appView;

  public final Outliner outliner;
  private final ClassInitializerDefaultsOptimization classInitializerDefaultsOptimization;
  protected final CfInstructionDesugaringCollection instructionDesugaring;
  protected final FieldAccessAnalysis fieldAccessAnalysis;
  protected final LibraryMethodOverrideAnalysis libraryMethodOverrideAnalysis;
  protected final StringOptimizer stringOptimizer;
  protected final IdempotentFunctionCallCanonicalizer idempotentFunctionCallCanonicalizer;
  private final ClassInliner classInliner;
  protected final InternalOptions options;
  public final CodeRewriter codeRewriter;
  public final CommonSubexpressionElimination commonSubexpressionElimination;
  private final SplitBranch splitBranch;
  public AssertionErrorTwoArgsConstructorRewriter assertionErrorTwoArgsConstructorRewriter;
  public final MemberValuePropagation<?> memberValuePropagation;
  private final LensCodeRewriter lensCodeRewriter;
  protected final Inliner inliner;
  protected final IdentifierNameStringMarker identifierNameStringMarker;
  private final Devirtualizer devirtualizer;
  protected final CovariantReturnTypeAnnotationTransformer covariantReturnTypeAnnotationTransformer;
  private final StringSwitchRemover stringSwitchRemover;
  private final TypeChecker typeChecker;
  protected ServiceLoaderRewriter serviceLoaderRewriter;
  private final EnumValueOptimizer enumValueOptimizer;
  private final BinopRewriter binopRewriter;
  protected final EnumUnboxer enumUnboxer;
  protected InstanceInitializerOutliner instanceInitializerOutliner;
  protected final RemoveVerificationErrorForUnknownReturnedValues
      removeVerificationErrorForUnknownReturnedValues;

  public final AssumeInserter assumeInserter;
  private final DynamicTypeOptimization dynamicTypeOptimization;

  final AssertionsRewriter assertionsRewriter;
  public final DeadCodeRemover deadCodeRemover;

  private final MethodOptimizationInfoCollector methodOptimizationInfoCollector;

  protected final OptimizationFeedbackDelayed delayedOptimizationFeedback =
      new OptimizationFeedbackDelayed();
  protected final OptimizationFeedback simpleOptimizationFeedback =
      OptimizationFeedbackSimple.getInstance();
  protected DexString highestSortingString;

  protected List<Action> onWaveDoneActions = null;
  protected final Set<DexMethod> prunedMethodsInWave = Sets.newIdentityHashSet();

  protected final NeverMergeGroup<DexString> neverMerge;
  // Use AtomicBoolean to satisfy TSAN checking (see b/153714743).
  AtomicBoolean seenNotNeverMergePrefix = new AtomicBoolean();
  AtomicBoolean seenNeverMergePrefix = new AtomicBoolean();
  String conflictingPrefixesErrorMessage = null;

  /**
   * The argument `appView` is used to determine if whole program optimizations are allowed or not
   * (i.e., whether we are running R8). See {@link AppView#enableWholeProgramOptimizations()}.
   */
  public IRConverter(AppView<?> appView) {
    assert appView.options() != null;
    assert appView.options().programConsumer != null;
    this.appView = appView;
    this.options = appView.options();
    this.codeRewriter = new CodeRewriter(appView);
    this.commonSubexpressionElimination = new CommonSubexpressionElimination(appView);
    this.splitBranch = new SplitBranch(appView);
    this.assertionErrorTwoArgsConstructorRewriter =
        appView.options().desugarState.isOn()
            ? new AssertionErrorTwoArgsConstructorRewriter(appView)
            : null;
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
      this.binopRewriter = null;
      this.enumUnboxer = EnumUnboxer.empty();
      this.assumeInserter = null;
      this.instanceInitializerOutliner = null;
      this.removeVerificationErrorForUnknownReturnedValues = null;
      return;
    }
    this.instructionDesugaring =
        appView.enableWholeProgramOptimizations()
            ? CfInstructionDesugaringCollection.empty()
            : CfInstructionDesugaringCollection.create(appView, appView.apiLevelCompute());
    this.covariantReturnTypeAnnotationTransformer =
        options.processCovariantReturnTypeAnnotations
            ? new CovariantReturnTypeAnnotationTransformer(appView, this)
            : null;
    if (appView.options().desugarState.isOn()
        && appView.options().apiModelingOptions().enableOutliningOfMethods
        && appView.options().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
      this.instanceInitializerOutliner = new InstanceInitializerOutliner(appView);
    } else {
      this.instanceInitializerOutliner = null;
    }
    removeVerificationErrorForUnknownReturnedValues =
        (appView.options().apiModelingOptions().enableLibraryApiModeling
                && appView.options().canHaveVerifyErrorForUnknownUnusedReturnValue())
            ? new RemoveVerificationErrorForUnknownReturnedValues(appView)
            : null;
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
      this.binopRewriter =
          options.testing.enableBinopOptimization && !options.debug
              ? new BinopRewriter(appView)
              : null;
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
      this.binopRewriter = null;
      this.enumUnboxer = EnumUnboxer.empty();
    }
    this.stringSwitchRemover =
        options.isStringSwitchConversionEnabled()
            ? new StringSwitchRemover(appView, identifierNameStringMarker)
            : null;
  }

  public IRConverter(AppInfo appInfo) {
    this(AppView.createForD8(appInfo));
  }

  public Inliner getInliner() {
    return inliner;
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

  protected void workaroundAbstractMethodOnNonAbstractClassVerificationBug(
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

  public void processSimpleSynthesizeMethods(
      List<ProgramMethod> methods, ExecutorService executorService) throws ExecutionException {
    ThreadUtils.processItems(
        methods, this::processAndFinalizeSimpleSynthesizedMethod, executorService);
  }

  private void processAndFinalizeSimpleSynthesizedMethod(ProgramMethod method) {
    IRCode code = method.buildIR(appView);
    assert code != null;
    codeRewriter.rewriteMoveResult(code);
    removeDeadCodeAndFinalizeIR(code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
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
  }

  public void optimizeSynthesizedMethods(
      List<ProgramMethod> programMethods,
      MethodProcessorEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    // Process the generated class, but don't apply any outlining.
    ProgramMethodSet methods = ProgramMethodSet.create(programMethods::forEach);
    processMethodsConcurrently(methods, eventConsumer, executorService);
  }

  public void optimizeSynthesizedMethod(
      ProgramMethod synthesizedMethod, MethodProcessorEventConsumer eventConsumer) {
    if (!synthesizedMethod.getDefinition().isProcessed()) {
      // Process the generated method, but don't apply any outlining.
      OneTimeMethodProcessor methodProcessor =
          OneTimeMethodProcessor.create(synthesizedMethod, eventConsumer, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingContext));
    }
  }

  public void processClassesConcurrently(
      Collection<DexProgramClass> classes,
      MethodProcessorEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    ProgramMethodSet wave = ProgramMethodSet.create();
    for (DexProgramClass clazz : classes) {
      clazz.forEachProgramMethod(wave::add);
    }
    processMethodsConcurrently(wave, eventConsumer, executorService);
  }

  public void processMethodsConcurrently(
      ProgramMethodSet wave,
      MethodProcessorEventConsumer eventConsumer,
      ExecutorService executorService)
      throws ExecutionException {
    if (!wave.isEmpty()) {
      OneTimeMethodProcessor methodProcessor =
          OneTimeMethodProcessor.create(wave, eventConsumer, appView);
      methodProcessor.forEachWaveWithExtension(
          (method, methodProcessingContext) ->
              processDesugaredMethod(
                  method, delayedOptimizationFeedback, methodProcessor, methodProcessingContext),
          executorService);
    }
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

  protected Timing rewriteDesugaredCodeInternal(
      ProgramMethod method,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    if (options.verbose) {
      options.reporter.info(
          new StringDiagnostic("Processing: " + method.toSourceString()));
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

  // TODO(b/140766440): Convert all sub steps an implementer of CodeOptimization
  Timing optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    ProgramMethod context = code.context();
    DexEncodedMethod method = context.getDefinition();
    DexProgramClass holder = context.getHolder();
    assert holder != null;

    Timing timing = Timing.create(context.toSourceString(), options);

    String previous = printMethod(code, "Initial IR (SSA)", null);

    if (options.testing.irModifier != null) {
      options.testing.irModifier.accept(code, appView);
    }

    if (options.canHaveArtStringNewInitBug()) {
      timing.begin("Check for new-init issue");
      TrivialPhiSimplifier.ensureDirectStringNewToInit(code, appView.dexItemFactory());
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
      serviceLoaderRewriter.rewrite(code, methodProcessor, methodProcessingContext);
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
          code, context, methodProcessor, methodProcessingContext);
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
      code.removeRedundantBlocks();
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

    new TrivialCheckCastAndInstanceOfRemover(appView)
        .run(code, methodProcessor, methodProcessingContext, timing);

    if (enumValueOptimizer != null) {
      assert appView.enableWholeProgramOptimizations();
      enumValueOptimizer.run(code, timing);
    }

    timing.begin("Rewrite array length");
    codeRewriter.rewriteKnownArrayLengthCalls(code);
    timing.end();
    new NaturalIntLoopRemover(appView).run(code, timing);
    if (assertionErrorTwoArgsConstructorRewriter != null) {
      timing.begin("Rewrite AssertionError");
      assertionErrorTwoArgsConstructorRewriter.rewrite(
          code, methodProcessor, methodProcessingContext);
      timing.end();
    }
    commonSubexpressionElimination.run(code, timing);
    new ArrayConstructionSimplifier(appView).run(code, timing);
    timing.begin("Rewrite move result");
    codeRewriter.rewriteMoveResult(code);
    timing.end();
    if (options.enableStringConcatenationOptimization && !isDebugMode) {
      timing.begin("Rewrite string concat");
      StringBuilderAppendOptimizer.run(appView, code);
      timing.end();
    }
    timing.begin("Propagate sparse conditionals");
    new SparseConditionalConstantPropagation(appView, code).run();
    timing.end();
    timing.begin("Rewrite always throwing instructions");
    new ThrowCatchOptimizer(appView).optimizeAlwaysThrowingInstructions(code);
    timing.end();
    timing.begin("Simplify control flow");
    if (new BranchSimplifier(appView).simplifyBranches(code)) {
      new TrivialCheckCastAndInstanceOfRemover(appView)
          .run(code, methodProcessor, methodProcessingContext, timing);
    }
    timing.end();
    splitBranch.run(code, timing);
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
    if (binopRewriter != null) {
      binopRewriter.run(code, timing);
    }

    if (options.testing.invertConditionals) {
      invertConditionalsForTesting(code);
    }

    if (!isDebugMode) {
      timing.begin("Rewrite throw NPE");
      new ThrowCatchOptimizer(appView).rewriteThrowNullPointerException(code);
      timing.end();
      previous = printMethod(code, "IR after rewrite throw null (SSA)", previous);
    }

    timing.begin("Optimize class initializers");
    ClassInitializerDefaultsResult classInitializerDefaultsResult =
        classInitializerDefaultsOptimization.optimize(code, feedback);
    timing.end();
    previous = printMethod(code, "IR after class initializer optimisation (SSA)", previous);

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
      code.removeRedundantBlocks();
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
          new ConstantCanonicalizer(appView, context, code);
      constantCanonicalizer.canonicalize();
      timing.end();
      previous = printMethod(code, "IR after constant canonicalization (SSA)", previous);
      new DexConstantOptimizer(appView, constantCanonicalizer).run(code, timing);
      previous = printMethod(code, "IR after dex constant optimization (SSA)", previous);
    }

    if (removeVerificationErrorForUnknownReturnedValues != null) {
      removeVerificationErrorForUnknownReturnedValues.run(context, code, timing);
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

    new ParentConstructorHoistingCodeRewriter(appView).run(code, timing);

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

    timing.begin("Redundant catch/rethrow elimination");
    new ThrowCatchOptimizer(appView).optimizeRedundantCatchRethrowInstructions(code);
    timing.end();
    previous = printMethod(code, "IR after redundant catch/rethrow elimination (SSA)", previous);

    if (assumeInserter != null) {
      timing.begin("Remove assume instructions");
      CodeRewriter.removeAssumeInstructions(appView, code);
      code.removeRedundantBlocks();
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
    if (code.isDefaultInstanceInitializerCode()) {
      // Passthrough unless the parent constructor may be inlineable.
      if (options.canInitNewInstanceUsingSuperclassConstructor()) {
        DexMethod parentConstructorReference =
            DefaultInstanceInitializerCode.getParentConstructor(method, appView.dexItemFactory());
        DexClassAndMethod parentConstructor = appView.definitionFor(parentConstructorReference);
        if (parentConstructor != null && parentConstructor.isProgramMethod()) {
          return false;
        }
      }
      return true;
    }
    return false;
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
    IRCode oldCode = code;
    if (options.testing.roundtripThroughLir) {
      code = roundtripThroughLir(code, feedback, bytecodeMetadataProvider, timing);
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
    printMethod(code.context(), "After finalization");
  }

  private IRCode roundtripThroughLir(
      IRCode code,
      OptimizationFeedback feedback,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing) {
    IRCode round1 =
        doRoundtripWithStrategy(code, new ExternalPhisStrategy(), "indirect phis", timing);
    IRCode round2 =
        doRoundtripWithStrategy(round1, new PhiInInstructionsStrategy(), "inline phis", timing);
    remapBytecodeMetadataProvider(code, round2, bytecodeMetadataProvider);
    return round2;
  }

  private static void remapBytecodeMetadataProvider(
      IRCode oldCode, IRCode newCode, BytecodeMetadataProvider bytecodeMetadataProvider) {
    InstructionIterator it1 = oldCode.instructionIterator();
    InstructionIterator it2 = newCode.instructionIterator();
    while (it1.hasNext() && it2.hasNext()) {
      bytecodeMetadataProvider.remap(it1.next(), it2.next());
    }
    assert !it1.hasNext() && !it2.hasNext();
  }

  private <EV, S extends LirStrategy<Value, EV>> IRCode doRoundtripWithStrategy(
      IRCode code, S strategy, String name, Timing timing) {
    timing.begin("IR->LIR (" + name + ")");
    LirCode<EV> lirCode =
        IR2LirConverter.translate(code, strategy.getEncodingStrategy(), appView.dexItemFactory());
    timing.end();
    // Check that printing does not fail.
    String lirString = lirCode.toString();
    assert !lirString.isEmpty();
    timing.begin("LIR->IR (" + name + ")");
    IRCode irCode =
        Lir2IRConverter.translate(
            code.context(), lirCode, strategy.getDecodingStrategy(lirCode), appView);
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

  protected synchronized void updateHighestSortingStrings(DexEncodedMethod method) {
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

  public void printPhase(String phase) {
    if (!options.extensiveLoggingFilter.isEmpty()) {
      System.out.println("Entering phase: " + phase);
    }
  }

  public String printMethod(IRCode code, String title, String previous) {
    if (options.extensiveLoggingFilter.isEmpty()) {
      return previous;
    }
    String methodString = code.method().getReference().toSourceString();
    if (options.extensiveLoggingFilter.contains(methodString)) {
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

  public void printMethod(ProgramMethod method, String title) {
    if (options.extensiveLoggingFilter.size() > 0
        && options.extensiveLoggingFilter.contains(method.getReference().toSourceString())) {
      String current = method.getDefinition().codeToString();
      System.out.println();
      System.out.println("-----------------------------------------------------------------------");
      System.out.println(title);
      System.out.println("-----------------------------------------------------------------------");
      System.out.println(current);
      System.out.println("-----------------------------------------------------------------------");
    }
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
