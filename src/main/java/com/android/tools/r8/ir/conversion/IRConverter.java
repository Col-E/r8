// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.Flavor.ExcludeDexResources;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.Flavor.IncludeAllResources;
import static com.android.tools.r8.ir.optimize.CodeRewriter.checksNullBeforeSideEffect;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.ResolutionResult;
import com.android.tools.r8.ir.analysis.DeterminismAnalysis;
import com.android.tools.r8.ir.analysis.InitializedClassesOnNormalExitAnalysis;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.constant.SparseConditionalConstantPropagation;
import com.android.tools.r8.ir.analysis.fieldaccess.FieldBitAccessAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis;
import com.android.tools.r8.ir.analysis.sideeffect.ClassInitializerSideEffectAnalysis.ClassInitializerSideEffect;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
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
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.LambdaRewriter;
import com.android.tools.r8.ir.desugar.StringConcatRewriter;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.ir.optimize.AliasIntroducer;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.ConstantCanonicalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.Devirtualizer;
import com.android.tools.r8.ir.optimize.DynamicTypeOptimization;
import com.android.tools.r8.ir.optimize.IdempotentFunctionCallCanonicalizer;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.MemberValuePropagation;
import com.android.tools.r8.ir.optimize.NonNullTracker;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.PeepholeOptimizer;
import com.android.tools.r8.ir.optimize.RedundantFieldLoadElimination;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriter;
import com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization;
import com.android.tools.r8.ir.optimize.classinliner.ClassInliner;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.lambda.LambdaMerger;
import com.android.tools.r8.ir.optimize.staticizer.ClassStaticizer;
import com.android.tools.r8.ir.optimize.string.StringBuilderOptimizer;
import com.android.tools.r8.ir.optimize.string.StringOptimizer;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.naming.IdentifierNameStringMarker;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryMethodOverrideAnalysis;
import com.android.tools.r8.shaking.MainDexClasses;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.AssertionProcessing;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Predicates;
import com.google.common.base.Suppliers;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class IRConverter {

  private static final int PEEPHOLE_OPTIMIZATION_PASSES = 2;

  public final AppView<?> appView;
  public final Set<DexType> mainDexClasses;

  private final Timing timing;
  private final Outliner outliner;
  private final ClassInitializerDefaultsOptimization classInitializerDefaultsOptimization;
  private final FieldBitAccessAnalysis fieldBitAccessAnalysis;
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
  private final UninstantiatedTypeOptimization uninstantiatedTypeOptimization;
  private final TypeChecker typeChecker;

  // Assumers that will insert Assume instructions.
  private final AliasIntroducer aliasIntroducer;
  private final DynamicTypeOptimization dynamicTypeOptimization;
  private final NonNullTracker nonNullTracker;

  final DeadCodeRemover deadCodeRemover;

  private final OptimizationFeedbackDelayed delayedOptimizationFeedback =
      new OptimizationFeedbackDelayed();
  private final OptimizationFeedback simpleOptimizationFeedback =
      OptimizationFeedbackSimple.getInstance();
  private DexString highestSortingString;

  private List<Action> onWaveDoneActions = null;

  private final List<DexString> neverMergePrefixes;
  boolean seenNotNeverMergePrefix = false;
  boolean seenNeverMergePrefix = false;

  /**
   * The argument `appView` is used to determine if whole program optimizations are allowed or not
   * (i.e., whether we are running R8). See {@link AppView#enableWholeProgramOptimizations()}.
   */
  public IRConverter(
      AppView<?> appView, Timing timing, CfgPrinter printer, MainDexClasses mainDexClasses) {
    assert appView.appInfo().hasLiveness() || appView.graphLense().isIdentityLense();
    assert appView.options() != null;
    assert appView.options().programConsumer != null;
    this.timing = timing != null ? timing : new Timing("internal");
    this.appView = appView;
    this.options = appView.options();
    this.printer = printer;
    this.mainDexClasses = mainDexClasses.getClasses();
    this.codeRewriter = new CodeRewriter(appView, this);
    this.constantCanonicalizer = new ConstantCanonicalizer();
    this.classInitializerDefaultsOptimization =
        options.debug ? null : new ClassInitializerDefaultsOptimization(appView, this);
    this.stringConcatRewriter = new StringConcatRewriter(appView);
    this.stringOptimizer = new StringOptimizer(appView);
    this.stringBuilderOptimizer = new StringBuilderOptimizer(appView);
    this.deadCodeRemover = new DeadCodeRemover(appView, codeRewriter);
    this.idempotentFunctionCallCanonicalizer = new IdempotentFunctionCallCanonicalizer(appView);
    this.neverMergePrefixes =
        options.neverMergePrefixes.stream()
            .map(prefix -> "L" + DescriptorUtils.getPackageBinaryNameFromJavaType(prefix))
            .map(options.itemFactory::createString)
            .collect(Collectors.toList());
    if (options.isDesugaredLibraryCompilation()) {
      // Specific L8 Settings.
      // BackportedMethodRewriter is needed for retarget core library members and backports.
      // InterfaceMethodRewriter is needed for emulated interfaces.
      // LambdaRewriter is needed because if it is missing there are invoke custom on
      // default/static interface methods, and this is not supported by the compiler.
      // The rest is nulled out.
      this.backportedMethodRewriter = new BackportedMethodRewriter(appView, this);
      this.interfaceMethodRewriter = new InterfaceMethodRewriter(appView, this);
      this.lambdaRewriter = new LambdaRewriter(appView, this);
      this.twrCloseResourceRewriter = null;
      this.lambdaMerger = null;
      this.covariantReturnTypeAnnotationTransformer = null;
      this.aliasIntroducer = null;
      this.nonNullTracker = null;
      this.dynamicTypeOptimization = null;
      this.classInliner = null;
      this.classStaticizer = null;
      this.fieldBitAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.outliner = null;
      this.memberValuePropagation = null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.uninstantiatedTypeOptimization = null;
      this.typeChecker = null;
      this.d8NestBasedAccessDesugaring = null;
      this.stringSwitchRemover = null;
      return;
    }
    this.lambdaRewriter = options.enableDesugaring ? new LambdaRewriter(appView, this) : null;
    this.interfaceMethodRewriter =
        options.isInterfaceMethodDesugaringEnabled()
            ? new InterfaceMethodRewriter(appView, this)
            : null;
    this.twrCloseResourceRewriter =
        (options.enableDesugaring && enableTwrCloseResourceDesugaring())
            ? new TwrCloseResourceRewriter(appView, this)
            : null;
    this.backportedMethodRewriter =
        options.enableDesugaring
            ? new BackportedMethodRewriter(appView, this)
            : null;
    this.lambdaMerger = options.enableLambdaMerging ? new LambdaMerger(appView) : null;
    this.covariantReturnTypeAnnotationTransformer =
        options.processCovariantReturnTypeAnnotations
            ? new CovariantReturnTypeAnnotationTransformer(this, appView.dexItemFactory())
            : null;
    this.aliasIntroducer =
        options.testing.forceAssumeNoneInsertion ? new AliasIntroducer(appView) : null;
    this.nonNullTracker = options.enableNonNullTracking ? new NonNullTracker(appView) : null;
    if (appView.enableWholeProgramOptimizations()) {
      assert appView.appInfo().hasLiveness();
      assert appView.rootSet() != null;
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      this.classInliner =
          options.enableClassInlining && options.enableInlining
              ? new ClassInliner(lambdaRewriter)
              : null;
      this.classStaticizer =
          options.enableClassStaticizer ? new ClassStaticizer(appViewWithLiveness, this) : null;
      this.dynamicTypeOptimization =
          options.enableDynamicTypeOptimization
              ? new DynamicTypeOptimization(appViewWithLiveness)
              : null;
      this.fieldBitAccessAnalysis =
          options.enableFieldBitAccessAnalysis
              ? new FieldBitAccessAnalysis(appViewWithLiveness)
              : null;
      this.libraryMethodOverrideAnalysis =
          options.enableTreeShakingOfLibraryMethodOverrides
              ? new LibraryMethodOverrideAnalysis(appViewWithLiveness)
              : null;
      this.lensCodeRewriter = new LensCodeRewriter(appViewWithLiveness, lambdaRewriter);
      this.inliner = new Inliner(appViewWithLiveness, mainDexClasses, lensCodeRewriter);
      this.outliner = new Outliner(appViewWithLiveness, this);
      this.memberValuePropagation =
          options.enableValuePropagation ? new MemberValuePropagation(appViewWithLiveness) : null;
      if (options.isMinifying()) {
        this.identifierNameStringMarker = new IdentifierNameStringMarker(appViewWithLiveness);
      } else {
        this.identifierNameStringMarker = null;
      }
      this.devirtualizer =
          options.enableDevirtualization ? new Devirtualizer(appViewWithLiveness) : null;
      this.uninstantiatedTypeOptimization =
          options.enableUninstantiatedTypeOptimization
              ? new UninstantiatedTypeOptimization(appViewWithLiveness)
              : null;
      this.typeChecker = new TypeChecker(appView.withLiveness());
      this.d8NestBasedAccessDesugaring = null;
    } else {
      this.classInliner = null;
      this.classStaticizer = null;
      this.dynamicTypeOptimization = null;
      this.fieldBitAccessAnalysis = null;
      this.libraryMethodOverrideAnalysis = null;
      this.inliner = null;
      this.outliner = null;
      this.memberValuePropagation = null;
      this.lensCodeRewriter = null;
      this.identifierNameStringMarker = null;
      this.devirtualizer = null;
      this.uninstantiatedTypeOptimization = null;
      this.typeChecker = null;
      this.d8NestBasedAccessDesugaring =
          options.shouldDesugarNests() ? new D8NestBasedAccessDesugaring(appView) : null;
    }
    this.stringSwitchRemover =
        options.isStringSwitchConversionEnabled()
            ? new StringSwitchRemover(appView, identifierNameStringMarker)
            : null;
  }

  public Set<DexCallSite> getDesugaredCallSites() {
    if (lambdaRewriter != null) {
      return lambdaRewriter.getDesugaredCallSites();
    } else {
      return Collections.emptySet();
    }
  }

  /** Create an IR converter for processing methods with full program optimization disabled. */
  public IRConverter(AppView<?> appView, Timing timing) {
    this(appView, timing, null, MainDexClasses.NONE);
  }

  /** Create an IR converter for processing methods with full program optimization disabled. */
  public IRConverter(AppView<?> appView, Timing timing, CfgPrinter printer) {
    this(appView, timing, printer, MainDexClasses.NONE);
  }

  public IRConverter(AppInfo appInfo, InternalOptions options, Timing timing, CfgPrinter printer) {
    this(AppView.createForD8(appInfo, options), timing, printer, MainDexClasses.NONE);
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

  private boolean removeLambdaDeserializationMethods() {
    if (lambdaRewriter != null) {
      return lambdaRewriter.removeLambdaDeserializationMethods(appView.appInfo().classes());
    }
    return false;
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
      lambdaRewriter.adjustAccessibility();
      lambdaRewriter.synthesizeLambdaClasses(builder, executorService);
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
      InterfaceMethodRewriter.Flavor includeAllResources,
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

  private void synthesizeJava8UtilityClass(
      Builder<?> builder, ExecutorService executorService) throws ExecutionException {
    if (backportedMethodRewriter != null) {
      backportedMethodRewriter.synthesizeUtilityClass(builder, executorService, options);
    }
  }

  private void processCovariantReturnTypeAnnotations(Builder<?> builder) {
    if (covariantReturnTypeAnnotationTransformer != null) {
      covariantReturnTypeAnnotationTransformer.process(builder);
    }
  }

  public DexApplication convert(DexApplication application, ExecutorService executor)
      throws ExecutionException {
    removeLambdaDeserializationMethods();

    timing.begin("IR conversion");
    convertClasses(application.classes(), executor);

    // Build a new application with jumbo string info,
    Builder<?> builder = application.builder();
    builder.setHighestSortingString(highestSortingString);

    desugarNestBasedAccess(builder, executor);
    synthesizeLambdaClasses(builder, executor);
    desugarInterfaceMethods(builder, ExcludeDexResources, executor);
    synthesizeTwrCloseResourceUtilityClass(builder, executor);
    synthesizeJava8UtilityClass(builder, executor);
    processCovariantReturnTypeAnnotations(builder);

    handleSynthesizedClassMapping(builder);
    timing.end();

    return builder.build();
  }

  private void handleSynthesizedClassMapping(Builder<?> builder) {
    if (options.intermediate) {
      updateSynthesizedClassMapping(builder);
    }

    updateMainDexListWithSynthesizedClassMap(builder);

    if (!options.intermediate) {
      clearSynthesizedClassMapping(builder);
    }
  }

  private void updateMainDexListWithSynthesizedClassMap(Builder<?> builder) {
    Set<DexType> inputMainDexList = builder.getMainDexList();
    if (!inputMainDexList.isEmpty()) {
      Map<DexType, DexProgramClass> programClasses = builder.getProgramClasses().stream()
          .collect(Collectors.toMap(
              programClass -> programClass.type,
              Function.identity()));
      Collection<DexType> synthesized = new ArrayList<>();
      for (DexType dexType : inputMainDexList) {
        DexProgramClass programClass = programClasses.get(dexType);
        if (programClass != null) {
          synthesized.addAll(DexAnnotation.readAnnotationSynthesizedClassMap(
              programClass, builder.dexItemFactory));
        }
      }
      builder.addToMainDexList(synthesized);
    }
  }

  private void clearSynthesizedClassMapping(Builder<?> builder) {
    for (DexProgramClass programClass : builder.getProgramClasses()) {
      programClass.annotations =
          programClass.annotations.getWithout(builder.dexItemFactory.annotationSynthesizedClassMap);
    }
  }

  private void updateSynthesizedClassMapping(Builder<?> builder) {
    ListMultimap<DexProgramClass, DexProgramClass> originalToSynthesized =
        ArrayListMultimap.create();
    for (DexProgramClass synthesized : builder.getSynthesizedClasses()) {
      for (DexProgramClass original : synthesized.getSynthesizedFrom()) {
        originalToSynthesized.put(original, synthesized);
      }
    }

    for (Map.Entry<DexProgramClass, Collection<DexProgramClass>> entry :
        originalToSynthesized.asMap().entrySet()) {
      DexProgramClass original = entry.getKey();
      // Use a tree set to make sure that we have an ordering on the types.
      // These types are put in an array in annotations in the output and we
      // need a consistent ordering on them.
      TreeSet<DexType> synthesized = new TreeSet<>(DexType::slowCompareTo);
      entry.getValue()
          .stream()
          .map(dexProgramClass -> dexProgramClass.type)
          .forEach(synthesized::add);
      synthesized.addAll(
          DexAnnotation.readAnnotationSynthesizedClassMap(original, builder.dexItemFactory));

      DexAnnotation updatedAnnotation =
          DexAnnotation.createAnnotationSynthesizedClassMap(synthesized, builder.dexItemFactory);

      original.annotations = original.annotations.getWithAddedOrReplaced(updatedAnnotation);
    }
  }

  private void convertClasses(Iterable<DexProgramClass> classes, ExecutorService executor)
      throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      futures.add(executor.submit(() -> convertMethods(clazz)));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void convertMethods(DexProgramClass clazz) {
    boolean isReachabilitySensitive = clazz.hasReachabilitySensitiveAnnotation(options.itemFactory);
    // When converting all methods on a class always convert <clinit> first.
    for (DexEncodedMethod method : clazz.directMethods()) {
      if (method.isClassInitializer()) {
        method.getMutableOptimizationInfo().setReachabilitySensitive(isReachabilitySensitive);
        convertMethod(method);
        break;
      }
    }
    clazz.forEachMethod(
        method -> {
          if (!method.isClassInitializer()) {
            method.getMutableOptimizationInfo().setReachabilitySensitive(isReachabilitySensitive);
            convertMethod(method);
          }
        });
  }

  private void convertMethod(DexEncodedMethod method) {
    if (method.getCode() != null) {
      boolean matchesMethodFilter = options.methodMatchesFilter(method);
      if (matchesMethodFilter) {
        if (appView.options().enableNeverMergePrefixes) {
          for (DexString neverMergePrefix : neverMergePrefixes) {
            // Synthetic classes will always be merged.
            if (method.method.holder.isD8R8SynthesizedClassType()) {
              continue;
            }
            if (method.method.holder.descriptor.startsWith(neverMergePrefix)) {
              seenNeverMergePrefix = true;
            } else {
              seenNotNeverMergePrefix = true;
            }
            // Don't mix.
            if (seenNeverMergePrefix && seenNotNeverMergePrefix) {
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
        if (options.isGeneratingClassFiles()
            || !(options.passthroughDexCode && method.getCode().isDexCode())) {
          // We do not process in call graph order, so anything could be a leaf.
          rewriteCode(method, simpleOptimizationFeedback, x -> true, CallSiteInformation.empty(),
              Outliner::noProcessing);
        } else {
          assert method.getCode().isDexCode();
        }
        if (!options.isGeneratingClassFiles()) {
          updateHighestSortingStrings(method);
        }
      }
    }
  }

  public DexApplication optimize() throws ExecutionException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      return optimize(executor);
    } finally {
      executor.shutdown();
    }
  }

  public DexApplication optimize(ExecutorService executorService) throws ExecutionException {

    if (options.isShrinking()) {
      assert !removeLambdaDeserializationMethods();
    } else {
      removeLambdaDeserializationMethods();
    }

    DexApplication application = appView.appInfo().app();

    computeReachabilitySensitivity(application);
    collectLambdaMergingCandidates(application);
    collectStaticizerCandidates(application);

    // The process is in two phases in general.
    // 1) Subject all DexEncodedMethods to optimization, except some optimizations that require
    //    reprocessing IR code of methods, e.g., outlining, double-inlining, class staticizer, etc.
    //    - a side effect is candidates for those optimizations are identified.
    // 2) Revisit DexEncodedMethods for the collected candidates.
    // TODO(b/127694949): unified framework to reprocess methods only once.

    printPhase("Primary optimization pass");

    // Process the application identifying outlining candidates.
    GraphLense graphLenseForIR = appView.graphLense();
    OptimizationFeedbackDelayed feedback = delayedOptimizationFeedback;
    {
      timing.begin("Build call graph");
      MethodProcessor methodProcessor =
          CallGraph.createMethodProcessor(appView.withLiveness(), executorService, timing);
      timing.end();
      timing.begin("IR conversion phase 1");
      BiConsumer<IRCode, DexEncodedMethod> outlineHandler =
          outliner == null ? Outliner::noProcessing : outliner.identifyCandidateMethods();
      methodProcessor.forEachMethod(
          (method, isProcessedConcurrently) ->
              processMethod(
                  method,
                  feedback,
                  isProcessedConcurrently,
                  methodProcessor.getCallSiteInformation(),
                  outlineHandler),
          this::waveStart,
          this::waveDone,
          executorService);
      timing.end();
      assert graphLenseForIR == appView.graphLense();
    }

    appView.setAllCodeProcessed();

    if (libraryMethodOverrideAnalysis != null) {
      libraryMethodOverrideAnalysis.finish();
    }

    // Second pass for methods whose collected call site information become more precise.
    if (appView.callSiteOptimizationInfoPropagator() != null) {
      printPhase("2nd round of method processing after inter-procedural analysis.");
      timing.begin("IR conversion phase 2");
      appView.callSiteOptimizationInfoPropagator().revisitMethods(
          (method, isProcessedConcurrently) ->
              processMethod(
                  method,
                  feedback,
                  isProcessedConcurrently,
                  CallSiteInformation.empty(),
                  Outliner::noProcessing),
          executorService);
      timing.end();
    }

    // Second inlining pass for dealing with double inline callers.
    if (inliner != null) {
      printPhase("Double caller inlining");
      assert graphLenseForIR == appView.graphLense();
      inliner.processDoubleInlineCallers(this, executorService, feedback);
      feedback.updateVisibleOptimizationInfo();
      assert graphLenseForIR == appView.graphLense();
    }

    // TODO(b/112831361): Implement support for staticizeClasses in CF backend.
    if (!options.isGeneratingClassFiles()) {
      printPhase("Class staticizer post processing");
      staticizeClasses(feedback, executorService);
    }

    // Build a new application with jumbo string info.
    Builder<?> builder = application.builder();
    builder.setHighestSortingString(highestSortingString);

    printPhase("Lambda class synthesis");
    synthesizeLambdaClasses(builder, executorService);

    printPhase("Interface method desugaring");
    desugarInterfaceMethods(builder, IncludeAllResources, executorService);

    printPhase("Twr close resource utility class synthesis");
    synthesizeTwrCloseResourceUtilityClass(builder, executorService);
    synthesizeJava8UtilityClass(builder, executorService);
    handleSynthesizedClassMapping(builder);

    printPhase("Lambda merging finalization");
    finalizeLambdaMerging(application, feedback, builder, executorService);

    if (outliner != null) {
      printPhase("Outlining");
      timing.begin("IR conversion phase 3");
      if (outliner.selectMethodsForOutlining()) {
        forEachSelectedOutliningMethod(
            executorService,
            (code, method) -> {
              printMethod(code, "IR before outlining (SSA)", null);
              outliner.identifyOutlineSites(code, method);
            });
        DexProgramClass outlineClass = outliner.buildOutlinerClass(computeOutlineClassType());
        appView.appInfo().addSynthesizedClass(outlineClass);
        optimizeSynthesizedClass(outlineClass, executorService);
        forEachSelectedOutliningMethod(
            executorService,
            (code, method) -> {
              outliner.applyOutliningCandidate(code, method);
              printMethod(code, "IR after outlining (SSA)", null);
              finalizeIR(method, code, OptimizationFeedbackIgnore.getInstance());
            });
        assert outliner.checkAllOutlineSitesFoundAgain();
        builder.addSynthesizedClass(outlineClass, true);
        clearDexMethodCompilationState(outlineClass);
      }
      timing.end();
    }
    clearDexMethodCompilationState();

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInFields();
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
      if (uninstantiatedTypeOptimization != null) {
        uninstantiatedTypeOptimization.logResults();
      }
      if (stringOptimizer != null) {
        stringOptimizer.logResult();
      }
      if (stringBuilderOptimizer != null) {
        stringBuilderOptimizer.logResults();
      }
    }

    // Check if what we've added to the application builder as synthesized classes are same as
    // what we've added and used through AppInfo.
    assert appView
            .appInfo()
            .getSynthesizedClassesForSanityCheck()
            .containsAll(builder.getSynthesizedClasses())
        && builder
            .getSynthesizedClasses()
            .containsAll(appView.appInfo().getSynthesizedClassesForSanityCheck());
    return builder.build();
  }

  private void waveStart() {
    onWaveDoneActions = Collections.synchronizedList(new ArrayList<>());
  }

  private void waveDone() {
    delayedOptimizationFeedback.updateVisibleOptimizationInfo();
    onWaveDoneActions.forEach(Action::execute);
    onWaveDoneActions = null;
  }

  public void addWaveDoneAction(Action action) {
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
      ExecutorService executorService, BiConsumer<IRCode, DexEncodedMethod> consumer)
      throws ExecutionException {
    assert !options.skipIR;
    Set<DexEncodedMethod> methods = outliner.getMethodsSelectedForOutlining();
    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      futures.add(
          executorService.submit(
              () -> {
                IRCode code =
                    method.buildIR(appView, appView.appInfo().originFor(method.method.holder));
                assert code != null;
                assert !method.getCode().isOutlineCode();
                // Instead of repeating all the optimizations of rewriteCode(), only run the
                // optimizations needed for outlining: rewriteMoveResult() to remove out-values on
                // StringBuilder/StringBuffer method invocations, and removeDeadCode() to remove
                // unused out-values.
                codeRewriter.rewriteMoveResult(code);
                deadCodeRemover.run(code);
                consumer.accept(code, method);
                return null;
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void collectLambdaMergingCandidates(DexApplication application) {
    if (lambdaMerger != null) {
      lambdaMerger.collectGroupCandidates(application, appView.withLiveness());
    }
  }

  private void finalizeLambdaMerging(
      DexApplication application,
      OptimizationFeedback feedback,
      Builder<?> builder,
      ExecutorService executorService)
      throws ExecutionException {
    if (lambdaMerger != null) {
      lambdaMerger.applyLambdaClassMapping(
          application, this, feedback, builder, executorService);
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
    code.traceBlocks();
    RegisterAllocator registerAllocator = performRegisterAllocation(code, method);
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
    do {
      String name = OutlineOptions.CLASS_NAME + (count == 0 ? "" : Integer.toString(count));
      count++;
      result = appView.dexItemFactory().createType(DescriptorUtils.javaTypeToDescriptor(name));
    } while (appView.definitionFor(result) != null);
    // Register the newly generated type in the subtyping hierarchy, if we have one.
    appView.appInfo().registerNewType(result, appView.dexItemFactory().objectType);
    return result;
  }

  public void optimizeSynthesizedClass(
      DexProgramClass clazz, ExecutorService executorService)
      throws ExecutionException {
    Set<DexEncodedMethod> methods = Sets.newIdentityHashSet();
    clazz.forEachMethod(methods::add);
    // Process the generated class, but don't apply any outlining.
    optimizeSynthesizedMethodsConcurrently(methods, executorService);
  }

  public void optimizeSynthesizedClasses(
      Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    Set<DexEncodedMethod> methods = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : classes) {
      clazz.forEachMethod(methods::add);
    }
    // Process the generated class, but don't apply any outlining.
    optimizeSynthesizedMethodsConcurrently(methods, executorService);
  }

  public void optimizeSynthesizedMethod(DexEncodedMethod method) {
    if (!method.isProcessed()) {
      // Process the generated method, but don't apply any outlining.
      processMethod(
          method,
          delayedOptimizationFeedback,
          Predicates.alwaysFalse(),
          CallSiteInformation.empty(),
          Outliner::noProcessing);
    }
  }

  public void optimizeSynthesizedMethodsConcurrently(
      Collection<DexEncodedMethod> methods, ExecutorService executorService)
      throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>(methods.size());
    optimizeSynthesizedMethodsConcurrently(methods, executorService, futures);
    ThreadUtils.awaitFutures(futures);
  }

  public void optimizeSynthesizedMethodsConcurrently(
      Collection<DexEncodedMethod> methods,
      ExecutorService executorService,
      List<Future<?>> futures) {
    for (DexEncodedMethod method : methods) {
      futures.add(
          executorService.submit(
              () -> {
                processMethod(
                    method,
                    delayedOptimizationFeedback,
                    methods::contains,
                    CallSiteInformation.empty(),
                    Outliner::noProcessing);
                return null; // we want a Callable not a Runnable to be able to throw
              }));
    }
  }

  private String logCode(InternalOptions options, DexEncodedMethod method) {
    return options.useSmaliSyntax ? method.toSmaliString(null) : method.codeToString();
  }

  public void processMethod(
      DexEncodedMethod method,
      OptimizationFeedback feedback,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation,
      BiConsumer<IRCode, DexEncodedMethod> outlineHandler) {
    Code code = method.getCode();
    boolean matchesMethodFilter = options.methodMatchesFilter(method);
    if (code != null && matchesMethodFilter) {
      rewriteCode(method, feedback, isProcessedConcurrently, callSiteInformation, outlineHandler);
    } else {
      // Mark abstract methods as processed as well.
      method.markProcessed(ConstraintWithTarget.NEVER);
    }
  }

  private static void invertConditionalsForTesting(IRCode code) {
    for (BasicBlock block : code.blocks) {
      if (block.exit().isIf()) {
        block.exit().asIf().invert();
      }
    }
  }

  private void rewriteCode(
      DexEncodedMethod method,
      OptimizationFeedback feedback,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation,
      BiConsumer<IRCode, DexEncodedMethod> outlineHandler) {
    Origin origin = appView.appInfo().originFor(method.method.holder);
    try {
      rewriteCodeInternal(
          method, feedback, isProcessedConcurrently, callSiteInformation, outlineHandler, origin);
    } catch (CompilationError e) {
      // If rewriting throws a compilation error, attach the origin and method if missing.
      throw e.withAdditionalOriginAndPositionInfo(origin, new MethodPosition(method.method));
    } catch (NullPointerException e) {
      throw new CompilationError(
          "NullPointerException during IR Conversion",
          e,
          origin,
          new MethodPosition(method.method));
    }
  }

  private void rewriteCodeInternal(
      DexEncodedMethod method,
      OptimizationFeedback feedback,
      Predicate<DexEncodedMethod> isProcessedConcurrently,
      CallSiteInformation callSiteInformation,
      BiConsumer<IRCode, DexEncodedMethod> outlineHandler,
      Origin origin) {

    if (options.verbose) {
      options.reporter.info(
          new StringDiagnostic("Processing: " + method.toSourceString()));
    }
    if (Log.ENABLED) {
      Log.debug(getClass(), "Original code for %s:\n%s",
          method.toSourceString(), logCode(options, method));
    }
    if (options.skipIR) {
      feedback.markProcessed(method, ConstraintWithTarget.NEVER);
      return;
    }
    IRCode code = method.buildIR(appView, origin);
    if (code == null) {
      feedback.markProcessed(method, ConstraintWithTarget.NEVER);
      return;
    }
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
      CodeRewriter.ensureDirectStringNewToInit(code, appView.dexItemFactory());
    }

    boolean isDebugMode = options.debug || method.getOptimizationInfo().isReachabilitySensitive();

    if (isDebugMode) {
      codeRewriter.simplifyDebugLocals(code);
    }

    if (!method.isProcessed()) {
      if (lensCodeRewriter != null) {
        lensCodeRewriter.rewrite(code, method);
      } else {
        assert appView.graphLense().isIdentityLense();
        if (lambdaRewriter != null && options.testing.desugarLambdasThroughLensCodeRewriter()) {
          lambdaRewriter.desugarLambdas(method, code);
          assert code.isConsistentSSA();
        }
      }
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
      return;
    }

    // This is the first point in time where we can assert that the types are sound. If this
    // assert fails, then the types that we have inferred are unsound, or the method does not type
    // check. In the latter case, the type checker should be extended to detect the issue such that
    // we will return with finalizeEmptyThrowingCode() above.
    assert code.verifyTypes(appView);

    if (appView.enableWholeProgramOptimizations() && options.enableServiceLoaderRewriting) {
      assert appView.appInfo().hasLiveness();
      ServiceLoaderRewriter.rewrite(code, appView.withLiveness());
    }

    if (classStaticizer != null) {
      classStaticizer.fixupMethodCode(method, code);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after class staticizer (SSA)", previous);

    if (identifierNameStringMarker != null) {
      identifierNameStringMarker.decoupleIdentifierNameStringsInMethod(method, code);
      assert code.isConsistentSSA();
    }

    if (memberValuePropagation != null) {
      memberValuePropagation.rewriteWithConstantValues(
          code, method.method.holder, isProcessedConcurrently);
    }
    if (options.enableEnumValueOptimization) {
      assert appView.enableWholeProgramOptimizations();
      codeRewriter.removeSwitchMaps(code);
    }
    if (options.assertionProcessing != AssertionProcessing.LEAVE) {
      codeRewriter.processAssertions(appView, method, code, feedback);
    }

    previous = printMethod(code, "IR after disable assertions (SSA)", previous);

    if (aliasIntroducer != null) {
      aliasIntroducer.insertAssumeInstructions(code);
      assert code.isConsistentSSA();
    }

    if (nonNullTracker != null) {
      nonNullTracker.insertAssumeInstructions(code);
      assert code.isConsistentSSA();
    }

    if (dynamicTypeOptimization != null) {
      assert appView.enableWholeProgramOptimizations();
      dynamicTypeOptimization.insertAssumeInstructions(code);
      assert code.isConsistentSSA();
    }

    appView.withGeneratedMessageLiteShrinker(shrinker -> shrinker.run(method, code));

    previous = printMethod(code, "IR after null tracking (SSA)", previous);

    if (!isDebugMode && options.enableInlining && inliner != null) {
      inliner.performInlining(method, code, feedback, isProcessedConcurrently, callSiteInformation);
      assert code.verifyTypes(appView);
    }

    previous = printMethod(code, "IR after inlining (SSA)", previous);

    if (appView.appInfo().hasLiveness()) {
      // Reflection optimization 1. getClass() / forName() -> const-class
      ReflectionOptimizer.rewriteGetClassOrForNameToConstClass(appView.withLiveness(), code);
    }

    if (!isDebugMode) {
      // Reflection optimization 2. get*Name() with const-class -> const-string
      if (options.enableNameReflectionOptimization
          || options.testing.forceNameReflectionOptimization) {
        stringOptimizer.rewriteClassGetName(appView, code);
      }
      // Reflection/string optimization 3. trivial conversion/computation on const-string
      stringOptimizer.computeTrivialOperationsOnConstString(code);
      stringOptimizer.removeTrivialConversions(code);
      assert code.isConsistentSSA();
    }

    if (devirtualizer != null) {
      assert code.verifyTypes(appView);
      devirtualizer.devirtualizeInvokeInterface(code, method.method.holder);
    }
    if (uninstantiatedTypeOptimization != null) {
      uninstantiatedTypeOptimization.rewrite(code);
    }

    assert code.verifyTypes(appView);
    codeRewriter.removeTrivialCheckCastAndInstanceOfInstructions(code);

    if (options.enableEnumValueOptimization) {
      assert appView.enableWholeProgramOptimizations();
      codeRewriter.rewriteConstantEnumMethodCalls(code);
    }

    codeRewriter.rewriteKnownArrayLengthCalls(code);
    codeRewriter.rewriteAssertionErrorTwoArgumentConstructor(code, options);
    codeRewriter.commonSubexpressionElimination(code);
    codeRewriter.simplifyArrayConstruction(code);
    codeRewriter.rewriteMoveResult(code);
    // TODO(b/114002137): for now, string concatenation depends on rewriteMoveResult.
    if (options.enableStringConcatenationOptimization
        && !isDebugMode
        && options.isGeneratingDex()) {
      stringBuilderOptimizer.computeTrivialStringConcatenation(code);
    }

    codeRewriter.splitRangeInvokeConstants(code);
    new SparseConditionalConstantPropagation(code).run();
    if (stringSwitchRemover != null) {
      stringSwitchRemover.run(method, code);
    }
    codeRewriter.rewriteSwitch(code);
    codeRewriter.processMethodsNeverReturningNormally(code);
    codeRewriter.simplifyIf(code);
    if (options.enableRedundantConstNumberOptimization) {
      codeRewriter.redundantConstNumberRemoval(code);
    }
    if (RedundantFieldLoadElimination.shouldRun(appView, code)) {
      new RedundantFieldLoadElimination(appView, code).run();
    }

    if (options.testing.invertConditionals) {
      invertConditionalsForTesting(code);
    }

    assert code.verifyTypes(appView);

    if (nonNullTracker != null) {
      // TODO(b/139246447): Once we extend this optimization to, e.g., constants of primitive args,
      //   this may not be the right place to collect call site optimization info.
      // Collecting call-site optimization info depends on the existence of non-null IRs.
      // Arguments can be changed during the debug mode.
      if (!isDebugMode && appView.callSiteOptimizationInfoPropagator() != null) {
        appView.callSiteOptimizationInfoPropagator().collectCallSiteOptimizationInfo(code);
      }
      // Computation of non-null parameters on normal exits rely on the existence of non-null IRs.
      nonNullTracker.computeNonNullParamOnNormalExits(feedback, code);
    }
    if (aliasIntroducer != null || nonNullTracker != null || dynamicTypeOptimization != null) {
      codeRewriter.removeAssumeInstructions(code);
      assert code.isConsistentSSA();
      assert code.verifyTypes(appView);
    }

    codeRewriter.rewriteThrowNullPointerException(code);

    if (classInitializerDefaultsOptimization != null && !isDebugMode) {
      classInitializerDefaultsOptimization.optimize(method, code, feedback);
    }
    if (Log.ENABLED) {
      Log.debug(getClass(), "Intermediate (SSA) flow graph for %s:\n%s",
          method.toSourceString(), code);
    }
    // Dead code removal. Performed after simplifications to remove code that becomes dead
    // as a result of those simplifications. The following optimizations could reveal more
    // dead code which is removed right before register allocation in performRegisterAllocation.
    deadCodeRemover.run(code);
    assert code.isConsistentSSA();
    // Assert that we do not have unremoved dead code in the output.
    assert code.verifyNoNullabilityBottomTypes();

    if (options.enableDesugaring && enableTryWithResourcesDesugaring()) {
      codeRewriter.rewriteThrowableAddAndGetSuppressed(code);
    }
    if (backportedMethodRewriter != null) {
      backportedMethodRewriter.desugar(code);
    }

    stringConcatRewriter.desugarStringConcats(method.method, code);

    if (options.testing.desugarLambdasThroughLensCodeRewriter()) {
      assert !options.enableDesugaring || lambdaRewriter.verifyNoLambdasToDesugar(code);
    } else if (lambdaRewriter != null) {
      lambdaRewriter.desugarLambdas(method, code);
      assert code.isConsistentSSA();
    }
    previous = printMethod(code, "IR after lambda desugaring (SSA)", previous);

    assert code.verifyTypes(appView);

    previous = printMethod(code, "IR before class inlining (SSA)", previous);

    if (classInliner != null) {
      // Class inliner should work before lambda merger, so if it inlines the
      // lambda, it does not get collected by merger.
      assert options.enableInlining && inliner != null;
      classInliner.processMethodCode(
          appView.withLiveness(),
          codeRewriter,
          stringOptimizer,
          method,
          code,
          isProcessedConcurrently,
          inliner,
          Suppliers.memoize(
              () ->
                  inliner.createDefaultOracle(
                      method,
                      code,
                      isProcessedConcurrently,
                      callSiteInformation,
                      Integer.MAX_VALUE / 2,
                      Integer.MAX_VALUE / 2)));
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after class inlining (SSA)", previous);

    if (d8NestBasedAccessDesugaring != null) {
      d8NestBasedAccessDesugaring.rewriteNestBasedAccesses(method, code, appView);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after nest based access desugaring (SSA)", previous);

    if (interfaceMethodRewriter != null) {
      interfaceMethodRewriter.rewriteMethodReferences(method, code);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after interface method rewriting (SSA)", previous);

    if (twrCloseResourceRewriter != null) {
      twrCloseResourceRewriter.rewriteMethodCode(code);
    }

    previous = printMethod(code, "IR after twr close resource rewriter (SSA)", previous);

    if (lambdaMerger != null) {
      lambdaMerger.processMethodCode(method, code);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after lambda merger (SSA)", previous);

    if (options.outline.enabled) {
      outlineHandler.accept(code, method);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after outline handler (SSA)", previous);

    // TODO(mkroghj) Test if shorten live ranges is worth it.
    if (!options.isGeneratingClassFiles()) {
      constantCanonicalizer.canonicalize(appView, code);
      codeRewriter.useDedicatedConstantForLitInstruction(code);
      codeRewriter.shortenLiveRanges(code);
    }
    idempotentFunctionCallCanonicalizer.canonicalize(code);

    previous =
        printMethod(code, "IR after idempotent function call canonicalization (SSA)", previous);

    // Insert code to log arguments if requested.
    if (options.methodMatchesLogArgumentsFilter(method)) {
      codeRewriter.logArgumentTypes(method, code);
      assert code.isConsistentSSA();
    }

    previous = printMethod(code, "IR after argument type logging (SSA)", previous);

    if (classStaticizer != null) {
      classStaticizer.examineMethodCode(method, code);
    }

    if (appView.enableWholeProgramOptimizations()) {
      if (libraryMethodOverrideAnalysis != null) {
        libraryMethodOverrideAnalysis.analyze(code);
      }

      if (fieldBitAccessAnalysis != null) {
        fieldBitAccessAnalysis.recordFieldAccesses(code, feedback);
      }

      // Compute optimization info summary for the current method unless it is pinned
      // (in that case we should not be making any assumptions about the behavior of the method).
      if (!appView.appInfo().withLiveness().isPinned(method.method)) {
        codeRewriter.identifyClassInlinerEligibility(method, code, feedback);
        codeRewriter.identifyParameterUsages(method, code, feedback);
        codeRewriter.identifyReturnsArgument(method, code, feedback);
        codeRewriter.identifyTrivialInitializer(method, code, feedback);

        if (options.enableInlining && inliner != null) {
          codeRewriter.identifyInvokeSemanticsForInlining(method, code, appView, feedback);
        }

        computeDynamicReturnType(feedback, method, code);
        computeInitializedClassesOnNormalExit(feedback, method, code);
        computeMayHaveSideEffects(feedback, method, code);
        computeReturnValueOnlyDependsOnArguments(feedback, method, code);
        computeNonNullParamOrThrow(feedback, method, code);
      }
    }

    previous =
        printMethod(code, "IR after computation of optimization info summary (SSA)", previous);

    if (options.canHaveNumberConversionRegisterAllocationBug()) {
      codeRewriter.workaroundNumberConversionRegisterAllocationBug(code);
    }

    // Either marked by IdentifierNameStringMarker or name reflection, or propagated from inlinee,
    // Then, make it visible to IdentifierMinifier.
    // Note that we place this at the end of IR processing because inlinee can be inlined by
    // Inliner, ClassInliner, or future optimizations that use the inlining machinery.
    if (method.getOptimizationInfo().useIdentifierNameString()) {
      // If it is optimized, e.g., moved to default values of static fields or even removed by dead
      // code remover, we can save future computation in IdentifierMinifier.
      if (Streams.stream(code.instructionIterator())
          .anyMatch(Instruction::isDexItemBasedConstString)) {
        feedback.markUseIdentifierNameString(method);
      }
    } else {
      assert Streams.stream(code.instructionIterator())
          .noneMatch(Instruction::isDexItemBasedConstString);
    }

    printMethod(code, "Optimized IR (SSA)", previous);
    finalizeIR(method, code, feedback);
  }

  // Track usage of parameters and compute their nullability and possibility of NPE.
  private void computeNonNullParamOrThrow(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (method.getOptimizationInfo().getNonNullParamOrThrow() != null) {
      return;
    }

    List<Value> arguments = code.collectArguments();
    BitSet paramsCheckedForNull = new BitSet();
    for (int index = 0; index < arguments.size(); index++) {
      Value argument = arguments.get(index);
      // This handles cases where the parameter is checked via Kotlin Intrinsics:
      //
      //   kotlin.jvm.internal.Intrinsics.checkParameterIsNotNull(param, message)
      //
      // or its inlined version:
      //
      //   if (param != null) return;
      //   invoke-static throwParameterIsNullException(msg)
      //
      // or some other variants, e.g., throw null or NPE after the direct null check.
      if (argument.isUsed() && checksNullBeforeSideEffect(code, argument, appView)) {
        paramsCheckedForNull.set(index);
      }
    }
    if (paramsCheckedForNull.length() > 0) {
      feedback.setNonNullParamOrThrow(method, paramsCheckedForNull);
    }
  }

  private void computeDynamicReturnType(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (dynamicTypeOptimization != null) {
      DexType staticReturnTypeRaw = method.method.proto.returnType;
      if (!staticReturnTypeRaw.isReferenceType()) {
        return;
      }

      TypeLatticeElement dynamicReturnType =
          dynamicTypeOptimization.computeDynamicReturnType(method, code);
      if (dynamicReturnType != null) {
        TypeLatticeElement staticReturnType =
            TypeLatticeElement.fromDexType(staticReturnTypeRaw, Nullability.maybeNull(), appView);

        // If the dynamic return type is not more precise than the static return type there is no
        // need to record it.
        if (dynamicReturnType.strictlyLessThan(staticReturnType, appView)) {
          feedback.methodReturnsObjectOfType(method, appView, dynamicReturnType);
        }
      }

      ClassTypeLatticeElement exactReturnType =
          dynamicTypeOptimization.computeDynamicLowerBoundType(method, code);
      if (exactReturnType != null) {
        feedback.methodReturnsObjectWithLowerBoundType(method, exactReturnType);
      }
    }
  }

  private void computeInitializedClassesOnNormalExit(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (options.enableInitializedClassesAnalysis && appView.appInfo().hasLiveness()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      Set<DexType> initializedClasses =
          InitializedClassesOnNormalExitAnalysis.computeInitializedClassesOnNormalExit(
              appViewWithLiveness, code);
      if (initializedClasses != null && !initializedClasses.isEmpty()) {
        feedback.methodInitializesClassesOnNormalExit(method, initializedClasses);
      }
    }
  }

  private void computeMayHaveSideEffects(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    // If the method is native, we don't know what could happen.
    assert !method.accessFlags.isNative();

    if (!options.enableSideEffectAnalysis) {
      return;
    }

    if (appView.appInfo().withLiveness().mayHaveSideEffects.containsKey(method.method)) {
      return;
    }

    DexType context = method.method.holder;

    if (method.isClassInitializer()) {
      // For class initializers, we also wish to compute if the class initializer has observable
      // side effects.
      ClassInitializerSideEffect classInitializerSideEffect =
          ClassInitializerSideEffectAnalysis.classInitializerCanBePostponed(appView, code);
      if (classInitializerSideEffect.isNone()) {
        feedback.methodMayNotHaveSideEffects(method);
        feedback.classInitializerMayBePostponed(method);
      } else if (classInitializerSideEffect.canBePostponed()) {
        feedback.classInitializerMayBePostponed(method);
      }
      return;
    }

    boolean mayHaveSideEffects;
    if (method.accessFlags.isSynchronized()) {
      // If the method is synchronized then it acquires a lock.
      mayHaveSideEffects = true;
    } else if (method.isInstanceInitializer() && hasNonTrivialFinalizeMethod(context)) {
      // If a class T overrides java.lang.Object.finalize(), then treat the constructor as having
      // side effects. This ensures that we won't remove instructions on the form `new-instance
      // {v0}, T`.
      mayHaveSideEffects = true;
    } else {
      // Otherwise, check if there is an instruction that has side effects.
      mayHaveSideEffects =
          Streams.stream(code.instructions())
              .anyMatch(instruction -> instruction.instructionMayHaveSideEffects(appView, context));
    }

    if (!mayHaveSideEffects) {
      feedback.methodMayNotHaveSideEffects(method);
    }
  }

  private void computeReturnValueOnlyDependsOnArguments(
      OptimizationFeedback feedback, DexEncodedMethod method, IRCode code) {
    if (!options.enableDeterminismAnalysis) {
      return;
    }
    boolean returnValueOnlyDependsOnArguments =
        DeterminismAnalysis.returnValueOnlyDependsOnArguments(appView.withLiveness(), code);
    if (returnValueOnlyDependsOnArguments) {
      feedback.methodReturnValueOnlyDependsOnArguments(method);
    }
  }

  // Returns true if `method` is an initializer and the enclosing class overrides the method
  // `void java.lang.Object.finalize()`.
  private boolean hasNonTrivialFinalizeMethod(DexType type) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz != null) {
      if (clazz.isProgramClass() && !clazz.isInterface()) {
        ResolutionResult resolutionResult =
            appView
                .appInfo()
                .resolveMethodOnClass(clazz, appView.dexItemFactory().objectMethods.finalize);
        for (DexEncodedMethod target : resolutionResult.asListOfTargets()) {
          if (target.method != appView.dexItemFactory().objectMethods.finalize) {
            return true;
          }
        }
        return false;
      } else {
        // Conservatively report that the library class could implement finalize().
        return true;
      }
    }
    return false;
  }

  private void finalizeIR(DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    code.traceBlocks();
    if (options.isGeneratingClassFiles()) {
      finalizeToCf(method, code, feedback);
    } else {
      assert options.isGeneratingDex();
      finalizeToDex(method, code, feedback);
    }
  }

  private void finalizeEmptyThrowingCode(DexEncodedMethod method, OptimizationFeedback feedback) {
    assert options.isGeneratingClassFiles() || options.isGeneratingDex();
    Code emptyThrowingCode =
        options.isGeneratingClassFiles()
            ? method.buildEmptyThrowingCfCode()
            : method.buildEmptyThrowingDexCode();
    method.setCode(emptyThrowingCode, appView);
    feedback.markProcessed(method, ConstraintWithTarget.ALWAYS);
  }

  private void finalizeToCf(DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    assert !method.getCode().isDexCode();
    CfBuilder builder = new CfBuilder(appView, method, code);
    CfCode result = builder.build(codeRewriter);
    method.setCode(result, appView);
    markProcessed(method, code, feedback);
  }

  private void finalizeToDex(DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    // Workaround massive dex2oat memory use for self-recursive methods.
    CodeRewriter.disableDex2OatInliningForSelfRecursiveMethods(appView, code);
    // Perform register allocation.
    RegisterAllocator registerAllocator = performRegisterAllocation(code, method);
    method.setCode(code, registerAllocator, appView);
    updateHighestSortingStrings(method);
    if (Log.ENABLED) {
      Log.debug(getClass(), "Resulting dex code for %s:\n%s",
          method.toSourceString(), logCode(options, method));
    }
    printMethod(code, "Final IR (non-SSA)", null);
    markProcessed(method, code, feedback);
  }

  private void markProcessed(DexEncodedMethod method, IRCode code, OptimizationFeedback feedback) {
    // After all the optimizations have take place, we compute whether method should be inlined.
    ConstraintWithTarget state;
    if (!options.enableInlining
        || inliner == null
        || method.getOptimizationInfo().isReachabilitySensitive()) {
      state = ConstraintWithTarget.NEVER;
    } else {
      state = inliner.computeInliningConstraint(code, method);
    }
    feedback.markProcessed(method, state);
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

  private RegisterAllocator performRegisterAllocation(IRCode code, DexEncodedMethod method) {
    // Always perform dead code elimination before register allocation. The register allocator
    // does not allow dead code (to make sure that we do not waste registers for unneeded values).
    deadCodeRemover.run(code);
    materializeInstructionBeforeLongOperationsWorkaround(code);
    workaroundForwardingInitializerBug(code);
    LinearScanRegisterAllocator registerAllocator = new LinearScanRegisterAllocator(appView, code);
    registerAllocator.allocateRegisters();
    if (options.canHaveExceptionTargetingLoopHeaderBug()) {
      codeRewriter.workaroundExceptionTargetingLoopHeaderBug(code);
    }
    printMethod(code, "After register allocation (non-SSA)", null);
    for (int i = 0; i < PEEPHOLE_OPTIMIZATION_PASSES; i++) {
      CodeRewriter.collapseTrivialGotos(code);
      PeepholeOptimizer.optimize(code, registerAllocator);
    }
    CodeRewriter.removeUnneededMovesOnExitingPaths(code, registerAllocator);
    CodeRewriter.collapseTrivialGotos(code);
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
    if (!code.method.isInstanceInitializer()) {
      return;
    }
    // Only constructors with certain signatures.
    DexTypeList paramTypes = code.method.method.proto.parameters;
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
      Instruction superConstructorCall = it.nextUntil((i) ->
          i.isInvokeDirect() &&
          i.asInvokeDirect().getInvokedMethod().name == options.itemFactory.constructorMethodName &&
          i.asInvokeDirect().arguments().size() == 4 &&
          i.asInvokeDirect().arguments().stream().allMatch(Value::isArgument));
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
    Value fixitValue = code.createValue(TypeLatticeElement.INT);
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
        && options.extensiveLoggingFilter.contains(code.method.method.toSourceString())) {
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
