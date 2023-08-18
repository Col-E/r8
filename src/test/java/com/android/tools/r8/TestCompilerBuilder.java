// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.google.common.base.Predicates.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorEventConsumer;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ForwardingOutputStream;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingOutputStream;
import com.android.tools.r8.utils.codeinspector.ArgumentPropagatorCodeScannerResultInspector;
import com.android.tools.r8.utils.codeinspector.EnumUnboxingInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class TestCompilerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestCompilerBuilder<C, B, CR, RR, T>>
    extends TestBaseBuilder<C, B, CR, RR, T> {

  public static final Consumer<InternalOptions> DEFAULT_OPTIONS =
      options -> {
        options.testing.enableTestAssertions = true;
        options.testing.allowUnusedDontWarnRules = false;
        options.testing.allowUnnecessaryDontWarnWildcards = false;
        options.horizontalClassMergerOptions().enable();
        options.horizontalClassMergerOptions().setEnableInterfaceMerging();
        options
            .getCfCodeAnalysisOptions()
            .setAllowUnreachableCfBlocks(false)
            .setEnableUnverifiableCodeReporting(true);
        options.getOpenClosedInterfacesOptions().disallowOpenInterfaces();
      };

  final Backend backend;

  // Default initialized setup. Can be overwritten if needed.
  private boolean allowStdoutMessages = false;
  private boolean allowStderrMessages = false;
  private boolean useDefaultRuntimeLibrary = true;
  private final List<Path> additionalRunClassPath = new ArrayList<>();
  private ProgramConsumer programConsumer;
  private MainDexClassesCollector mainDexClassesCollector;
  private StringConsumer mainDexListConsumer;
  // TODO(b/186010707): This could become implicit once min always be set when fixed.
  private boolean noMinApiLevel = false;
  private int minApiLevel = -1;
  private boolean optimizeMultidexForLinearAlloc = false;
  private Consumer<InternalOptions> optionsConsumer = DEFAULT_OPTIONS;
  private ByteArrayOutputStream stdout = null;
  private boolean stdOutForwarding = true;
  private PrintStream oldStdout = null;
  private ByteArrayOutputStream stderr = null;
  private PrintStream oldStderr = null;
  protected OutputMode outputMode = OutputMode.DexIndexed;
  private boolean isBenchmarkRunner = false;

  private Optional<Integer> isAndroidBuildVersionAdded = null;

  private static final Map<Integer, Set<String>> allowedGlobalSynthetics =
      new ConcurrentHashMap<>();

  LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration =
      LibraryDesugaringTestConfiguration.DISABLED;

  public boolean isD8TestBuilder() {
    return false;
  }

  public D8TestBuilder asD8TestBuilder() {
    return null;
  }

  public boolean isR8TestBuilder() {
    return false;
  }

  public R8TestBuilder<?> asR8TestBuilder() {
    return null;
  }

  public boolean isTestShrinkerBuilder() {
    return false;
  }

  public T addAndroidBuildVersion() {
    return addAndroidBuildVersion(null);
  }

  public T addAndroidBuildVersion(AndroidApiLevel specifiedApiLevel) {
    addProgramClasses(AndroidBuildVersion.class);
    isAndroidBuildVersionAdded =
        Optional.ofNullable(specifiedApiLevel == null ? null : specifiedApiLevel.getLevel());
    return self();
  }

  TestCompilerBuilder(TestState state, B builder, Backend backend) {
    super(state, builder);
    this.backend = backend;
    if (backend == Backend.DEX) {
      setOutputMode(OutputMode.DexIndexed);
    } else {
      assert backend == Backend.CF;
      setOutputMode(OutputMode.ClassFile);
    }
  }

  protected int getMinApiLevel() {
    // TODO(b/186010707): Enable assert minApiLevel != -1;
    return minApiLevel;
  }

  abstract CR internalCompile(
      B builder,
      Consumer<InternalOptions> optionsConsumer,
      Supplier<AndroidApp> app,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException;

  public T addArgumentPropagatorCodeScannerResultInspector(
      ThrowableConsumer<ArgumentPropagatorCodeScannerResultInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.argumentPropagatorEventConsumer =
                options.testing.argumentPropagatorEventConsumer.andThen(
                    new ArgumentPropagatorEventConsumer() {
                      @Override
                      public void acceptCodeScannerResult(
                          MethodStateCollectionByReference methodStates) {
                        inspector.acceptWithRuntimeException(
                            new ArgumentPropagatorCodeScannerResultInspector(
                                options.dexItemFactory(), methodStates));
                      }
                    }));
  }

  public T addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    if (optionsConsumer != null) {
      this.optionsConsumer = this.optionsConsumer.andThen(optionsConsumer);
    }
    return self();
  }

  public T allowCheckDiscardedErrors() {
    return addOptionsModification(
        options -> options.testing.dontReportFailingCheckDiscarded = true);
  }

  public T addEnumUnboxingInspector(Consumer<EnumUnboxingInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.unboxedEnumsConsumer =
                ((dexItemFactory, unboxedEnums) ->
                    inspector.accept(new EnumUnboxingInspector(dexItemFactory, unboxedEnums))));
  }

  public T addHorizontallyMergedClassesInspector(
      ThrowableConsumer<HorizontallyMergedClassesInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.horizontallyMergedClassesConsumer =
                ((dexItemFactory, horizontallyMergedClasses) ->
                    inspector.acceptWithRuntimeException(
                        new HorizontallyMergedClassesInspector(
                            dexItemFactory, horizontallyMergedClasses))));
  }

  public T addHorizontallyMergedClassesInspectorIf(
      boolean condition, ThrowableConsumer<HorizontallyMergedClassesInspector> inspector) {
    if (condition) {
      return addHorizontallyMergedClassesInspector(inspector);
    }
    return self();
  }

  public T addVerticallyMergedClassesInspector(
      Consumer<VerticallyMergedClassesInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.verticallyMergedClassesConsumer =
                ((dexItemFactory, verticallyMergedClasses) ->
                    inspector.accept(
                        new VerticallyMergedClassesInspector(
                            dexItemFactory, verticallyMergedClasses))));
  }

  public CR benchmarkCompile(BenchmarkResults results) throws CompilationFailedException {
    if (System.getProperty("com.android.tools.r8.printtimes") != null) {
      allowStdoutMessages();
    }
    isBenchmarkRunner = true;
    return internalCompileAndBenchmark(results);
  }

  public CR compile() throws CompilationFailedException {
    return internalCompileAndBenchmark(null);
  }

  private Collection<Path> getDefaultLibraryFiles() {
    if (backend == Backend.DEX) {
      assert builder.isMinApiLevelSet();
      return Collections.singletonList(
          ToolHelper.getFirstSupportedAndroidJar(
              AndroidApiLevel.getAndroidApiLevel(builder.getMinApiLevel())));
    } else {
      assert backend == Backend.CF;
      return Collections.singletonList(ToolHelper.getJava8RuntimeJar());
    }
  }

  private CR internalCompileAndBenchmark(BenchmarkResults benchmark)
      throws CompilationFailedException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    builder.setProgramConsumer(sink.wrapProgramConsumer(programConsumer));
    if (mainDexClassesCollector != null || mainDexListConsumer != null) {
      builder.setMainDexListConsumer(
          ChainedStringConsumer.builder()
              .addIfNotNull(mainDexClassesCollector)
              .addIfNotNull(mainDexListConsumer)
              .build());
    }
    if (!noMinApiLevel && (backend.isDex() || !isTestShrinkerBuilder())) {
      assert !builder.isMinApiLevelSet()
          : "Don't set the API level directly through BaseCompilerCommand.Builder in tests";
      // TODO(b/186010707): This will always be set when fixed.
      int minApi =
          getMinApiLevel() == -1
              ? ToolHelper.getMinApiLevelForDexVm().getLevel()
              : getMinApiLevel();
      builder.setMinApiLevel(minApi);
    }
    if (!noMinApiLevel
        && backend.isDex()
        && (isD8TestBuilder() || isR8TestBuilder())
        && !isBenchmarkRunner) {
      int minApiLevel = builder.getMinApiLevel();
      allowedGlobalSynthetics.computeIfAbsent(
          minApiLevel, TestCompilerBuilder::computeAllGlobalSynthetics);
      Consumer<InternalOptions> previousConsumer = optionsConsumer;
      optionsConsumer =
          options -> {
            options.testing.globalSyntheticCreatedCallback =
                programClass -> {
                  assertTrue(
                      allowedGlobalSynthetics
                          .get(minApiLevel)
                          .contains(programClass.getType().toDescriptorString()));
                };
            if (previousConsumer != null) {
              previousConsumer.accept(options);
            }
          };
    }

    if ((isD8TestBuilder() || isR8TestBuilder()) && !isBenchmarkRunner) {
      addOptionsModification(
          o -> o.getArtProfileOptions().setEnableCompletenessCheckForTesting(true));
    }

    builder.setOptimizeMultidexForLinearAlloc(optimizeMultidexForLinearAlloc);
    if (useDefaultRuntimeLibrary) {
      builder.addLibraryFiles(getDefaultLibraryFiles());
    }
    assertNull(oldStdout);
    oldStdout = System.out;
    assertNull(oldStderr);
    oldStderr = System.err;
    CR cr;
    try {
      if (stdout != null) {
        assertTrue(allowStdoutMessages);
        if (stdOutForwarding) {
          System.setOut(new PrintStream(new ForwardingOutputStream(stdout, System.out)));
        } else {
          System.setOut(new PrintStream(stdout));
        }
      } else if (!allowStdoutMessages) {
        System.setOut(
            new PrintStream(
                new ThrowingOutputStream<>(
                    () -> new AssertionError("Unexpected print to stdout"))));
      }
      if (stderr != null) {
        assertTrue(allowStderrMessages);
        System.setErr(new PrintStream(new ForwardingOutputStream(stderr, System.err)));
      } else if (!allowStderrMessages) {
        System.setErr(
            new PrintStream(
                new ThrowingOutputStream<>(
                    () -> new AssertionError("Unexpected print to stderr"))));
      }
      cr =
          internalCompile(builder, optionsConsumer, Suppliers.memoize(sink::build), benchmark)
              .addRunClasspathFiles(additionalRunClassPath);
      if (isAndroidBuildVersionAdded != null) {
        cr.setSystemProperty(
            AndroidBuildVersion.PROPERTY,
            "" + isAndroidBuildVersionAdded.orElse(builder.getMinApiLevel()));
      }
      return cr;
    } finally {
      if (mainDexClassesCollector != null) {
        getState().setMainDexClasses(mainDexClassesCollector.getMainDexClasses());
      }
      if (stdout != null) {
        getState().setStdout(stdout.toString());
      }
      System.setOut(oldStdout);
      if (stderr != null) {
        getState().setStderr(stderr.toString());
      }
      System.setErr(oldStderr);
    }
  }

  public T enableExperimentalMapFileVersion() {
    addOptionsModification(o -> o.testing.enableExperimentalMapFileVersion = true);
    return self();
  }

  @FunctionalInterface
  public interface DiagnosticsConsumer {
    void accept(TestDiagnosticMessages diagnostics);
  }

  public CR compileWithExpectedDiagnostics(DiagnosticsConsumer diagnosticsConsumer)
      throws CompilationFailedException {
    TestDiagnosticMessages diagnosticsHandler = getState().getDiagnosticsMessages();
    try {
      CR result = compile();
      diagnosticsConsumer.accept(diagnosticsHandler);
      return result;
    } catch (CompilationFailedException e) {
      diagnosticsConsumer.accept(diagnosticsHandler);
      throw e;
    }
  }

  @Override
  @Deprecated
  public RR run(String mainClass)
      throws CompilationFailedException, ExecutionException, IOException {
    return compile().run(mainClass);
  }

  @Override
  public RR run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    return compile().run(runtime, mainClass, args);
  }

  public T setMode(CompilationMode mode) {
    builder.setMode(mode);
    return self();
  }

  public T debug() {
    return setMode(CompilationMode.DEBUG);
  }

  public T release() {
    return setMode(CompilationMode.RELEASE);
  }

  public T setMinApiThreshold(AndroidApiLevel minApiThreshold) {
    assert backend == Backend.DEX;
    AndroidApiLevel minApi = ToolHelper.getMinApiLevelForDexVmNoHigherThan(minApiThreshold);
    return setMinApi(minApi);
  }

  public T setMinApi(AndroidApiLevel minApiLevel) {
    return setMinApi(minApiLevel.getLevel());
  }

  public T setMinApi(TestParameters parameters) {
    parameters.configureApiLevel(this);
    return self();
  }

  public T setMinApi(int minApiLevel) {
    assert minApiLevel != -1;
    this.minApiLevel = minApiLevel;
    return self();
  }

  public T setNoMinApi() {
    this.minApiLevel = -1;
    this.noMinApiLevel = true;
    return self();
  }

  public T setOptimizeMultidexForLinearAlloc() {
    this.optimizeMultidexForLinearAlloc = true;
    return self();
  }

  public T disableDesugaring() {
    builder.setDisableDesugaring(true);
    return self();
  }

  public OutputMode getOutputMode() {
    if (programConsumer instanceof DexIndexedConsumer) {
      return OutputMode.DexIndexed;
    }
    if (programConsumer instanceof DexFilePerClassFileConsumer) {
      return ((DexFilePerClassFileConsumer) programConsumer)
              .combineSyntheticClassesWithPrimaryClass()
          ? OutputMode.DexFilePerClassFile
          : OutputMode.DexFilePerClass;
    }
    assert programConsumer instanceof ClassFileConsumer;
    return OutputMode.ClassFile;
  }

  public T setOutputMode(OutputMode outputMode) {
    assert ToolHelper.verifyValidOutputMode(backend, outputMode);
    switch (outputMode) {
      case DexIndexed:
        programConsumer = DexIndexedConsumer.emptyConsumer();
        break;
      case DexFilePerClassFile:
        programConsumer = DexFilePerClassFileConsumer.emptyConsumer();
        break;
      case DexFilePerClass:
        programConsumer =
            new DexFilePerClassFileConsumer.ForwardingConsumer(null) {
              @Override
              public boolean combineSyntheticClassesWithPrimaryClass() {
                return false;
              }
            };
        break;
      case ClassFile:
        programConsumer = ClassFileConsumer.emptyConsumer();
        break;
    }
    return self();
  }

  public T setProgramConsumer(ProgramConsumer programConsumer) {
    assert programConsumer != null;
    assert backend == Backend.fromConsumer(programConsumer);
    this.programConsumer = programConsumer;
    return self();
  }

  public T collectMainDexClasses() {
    assert mainDexClassesCollector == null;
    mainDexClassesCollector = new MainDexClassesCollector();
    return self();
  }

  public T setMainDexListConsumer(StringConsumer consumer) {
    assert consumer != null;
    this.mainDexListConsumer = consumer;
    return self();
  }

  public T setIncludeClassesChecksum(boolean include) {
    builder.setIncludeClassesChecksum(include);
    return self();
  }

  @Override
  public T addLibraryFiles(Collection<Path> files) {
    useDefaultRuntimeLibrary = false;
    return super.addLibraryFiles(files);
  }

  @Override
  public T addLibraryClasses(Collection<Class<?>> classes) {
    useDefaultRuntimeLibrary = false;
    return super.addLibraryClasses(classes);
  }

  @Override
  public T addLibraryProvider(ClassFileResourceProvider provider) {
    useDefaultRuntimeLibrary = false;
    return super.addLibraryProvider(provider);
  }

  public T setUseDefaultRuntimeLibrary(boolean useDefaultRuntimeLibrary) {
    this.useDefaultRuntimeLibrary = useDefaultRuntimeLibrary;
    return self();
  }

  @Override
  public T allowStdoutMessages() {
    allowStdoutMessages = true;
    return self();
  }

  public T collectStdout() {
    assert stdout == null;
    stdout = new ByteArrayOutputStream();
    return allowStdoutMessages();
  }

  public T collectStdoutWithoutForwarding() {
    assert stdout == null;
    stdout = new ByteArrayOutputStream();
    stdOutForwarding = false;
    return allowStdoutMessages();
  }

  /**
   * If {@link #allowStdoutMessages} is false, then {@link System#out} will be replaced temporarily
   * by a {@link ThrowingOutputStream}. To allow the testing infrastructure to print messages to the
   * terminal, this method provides a reference to the original {@link System#out}.
   */
  public PrintStream getStdoutForTesting() {
    assertNotNull(oldStdout);
    return oldStdout;
  }

  public T allowStderrMessages() {
    allowStderrMessages = true;
    return self();
  }

  public T collectStderr() {
    assert stderr == null;
    stderr = new ByteArrayOutputStream();
    return allowStderrMessages();
  }

  public T enableCoreLibraryDesugaring(LibraryDesugaringTestConfiguration configuration) {
    this.libraryDesugaringTestConfiguration = configuration;
    return self();
  }

  @Override
  public T addRunClasspathFiles(Collection<Path> files) {
    additionalRunClassPath.addAll(files);
    return self();
  }

  public T addAssertionsConfiguration(
      Function<AssertionsConfiguration.Builder, AssertionsConfiguration>
          assertionsConfigurationGenerator) {
    builder.addAssertionsConfiguration(assertionsConfigurationGenerator);
    return self();
  }

  @Override
  public DebugTestConfig debugConfig(TestRuntime runtime) throws Exception {
    return compile().debugConfig(runtime);
  }

  private static Set<String> computeAllGlobalSynthetics(int minApiLevel) {
    try {
      Set<String> generatedGlobalSynthetics = Sets.newConcurrentHashSet();
      GlobalSyntheticsGeneratorCommand command =
          GlobalSyntheticsGeneratorCommand.builder()
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.API_DATABASE_LEVEL))
              .setMinApiLevel(minApiLevel)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .build();
      InternalOptions internalOptions = command.getInternalOptions();
      internalOptions.testing.globalSyntheticCreatedCallback =
          programClass ->
              generatedGlobalSynthetics.add(programClass.getType().toDescriptorString());
      GlobalSyntheticsGenerator.runForTesting(command.getInputApp(), internalOptions);
      return generatedGlobalSynthetics;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static class ChainedStringConsumer implements StringConsumer {

    private final List<StringConsumer> consumers;

    ChainedStringConsumer(List<StringConsumer> consumers) {
      this.consumers = consumers;
    }

    static Builder builder() {
      return new Builder();
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      consumers.forEach(consumer -> consumer.accept(string, handler));
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      consumers.forEach(consumer -> consumer.finished(handler));
    }

    static class Builder {

      private final List<StringConsumer> consumers = new ArrayList<>();

      Builder add(StringConsumer consumer) {
        assert consumer != null;
        consumers.add(consumer);
        return this;
      }

      Builder addIfNotNull(StringConsumer consumer) {
        return consumer != null ? add(consumer) : this;
      }

      ChainedStringConsumer build() {
        return new ChainedStringConsumer(consumers);
      }
    }
  }

  private static class MainDexClassesCollector implements StringConsumer {

    private StringBuilder builder = new StringBuilder();
    private Set<String> mainDexClasses;

    public Set<String> getMainDexClasses() {
      assert mainDexClasses != null;
      return mainDexClasses;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      builder.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      mainDexClasses =
          Stream.of(builder.toString().split(System.lineSeparator()))
              .filter(not(String::isEmpty))
              .map(
                  line -> {
                    assert line.endsWith(".class");
                    return line.substring(0, line.length() - ".class".length());
                  })
              .map(DescriptorUtils::getJavaTypeFromBinaryName)
              .collect(Collectors.toSet());
      builder = null;
    }
  }
}
