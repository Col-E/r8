// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.testing.AndroidBuildVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ForwardingOutputStream;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingOutputStream;
import com.android.tools.r8.utils.codeinspector.EnumUnboxingInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedLambdaClassesInspector;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import com.google.common.base.Suppliers;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class TestCompilerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult<RR>,
        T extends TestCompilerBuilder<C, B, CR, RR, T>>
    extends TestBaseBuilder<C, B, CR, RR, T> {

  public static final Consumer<InternalOptions> DEFAULT_OPTIONS =
      options -> {
        options.testing.allowClassInlinerGracefulExit = false;
        options.testing.reportUnusedProguardConfigurationRules = true;
        options.enableHorizontalClassMerging = true;
      };

  final Backend backend;

  // Default initialized setup. Can be overwritten if needed.
  private boolean allowStdoutMessages = false;
  private boolean allowStderrMessages = false;
  private boolean useDefaultRuntimeLibrary = true;
  private final List<Path> additionalRunClassPath = new ArrayList<>();
  private ProgramConsumer programConsumer;
  private StringConsumer mainDexListConsumer;
  protected int minApiLevel = ToolHelper.getMinApiLevelForDexVm().getLevel();
  private Consumer<InternalOptions> optionsConsumer = DEFAULT_OPTIONS;
  private ByteArrayOutputStream stdout = null;
  private PrintStream oldStdout = null;
  private ByteArrayOutputStream stderr = null;
  private PrintStream oldStderr = null;
  protected OutputMode outputMode = OutputMode.DexIndexed;

  private boolean isAndroidBuildVersionAdded = false;

  public boolean isTestShrinkerBuilder() {
    return false;
  }

  public T addAndroidBuildVersion() {
    addProgramClasses(AndroidBuildVersion.class);
    isAndroidBuildVersionAdded = true;
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

  abstract CR internalCompile(
      B builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException;

  public T addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    if (optionsConsumer != null) {
      this.optionsConsumer = this.optionsConsumer.andThen(optionsConsumer);
    }
    return self();
  }

  public T allowCheckDiscardedErrors(boolean skipReporting) {
    return addOptionsModification(
        options -> {
          options.testing.allowCheckDiscardedErrors = true;
          options.testing.dontReportFailingCheckDiscarded = skipReporting;
        });
  }

  public T addEnumUnboxingInspector(Consumer<EnumUnboxingInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.unboxedEnumsConsumer =
                ((dexItemFactory, unboxedEnums) ->
                    inspector.accept(new EnumUnboxingInspector(dexItemFactory, unboxedEnums))));
  }

  public T addHorizontallyMergedClassesInspector(
      Consumer<HorizontallyMergedClassesInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.horizontallyMergedClassesConsumer =
                ((dexItemFactory, horizontallyMergedClasses) ->
                    inspector.accept(
                        new HorizontallyMergedClassesInspector(
                            dexItemFactory, horizontallyMergedClasses))));
  }

  public T addHorizontallyMergedClassesInspectorIf(
      boolean condition, Consumer<HorizontallyMergedClassesInspector> inspector) {

    if (condition) {
      return addHorizontallyMergedClassesInspector(inspector);
    }
    return self();
  }

  public T addHorizontallyMergedLambdaClassesInspector(
      Consumer<HorizontallyMergedLambdaClassesInspector> inspector) {
    return addOptionsModification(
        options ->
            options.testing.horizontallyMergedLambdaClassesConsumer =
                ((dexItemFactory, horizontallyMergedLambdaClasses) ->
                    inspector.accept(
                        new HorizontallyMergedLambdaClassesInspector(
                            dexItemFactory, horizontallyMergedLambdaClasses))));
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

  public CR compile() throws CompilationFailedException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    builder.setProgramConsumer(sink.wrapProgramConsumer(programConsumer));
    builder.setMainDexListConsumer(mainDexListConsumer);
    if (backend.isDex() || !isTestShrinkerBuilder()) {
      assert !builder.isMinApiLevelSet()
          : "Don't set the API level directly through BaseCompilerCommand.Builder in tests";
      builder.setMinApiLevel(minApiLevel);
    }
    if (useDefaultRuntimeLibrary) {
      if (backend == Backend.DEX) {
        assert builder.isMinApiLevelSet();
        builder.addLibraryFiles(
            ToolHelper.getFirstSupportedAndroidJar(
                AndroidApiLevel.getAndroidApiLevel(builder.getMinApiLevel())));
      } else {
        builder.addLibraryFiles(TestBase.runtimeJar(backend));
      }
    }
    assertNull(oldStdout);
    oldStdout = System.out;
    assertNull(oldStderr);
    oldStderr = System.err;
    CR cr;
    try {
      if (stdout != null) {
        assertTrue(allowStdoutMessages);
        System.setOut(new PrintStream(new ForwardingOutputStream(stdout, System.out)));
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
          internalCompile(builder, optionsConsumer, Suppliers.memoize(sink::build))
              .addRunClasspathFiles(additionalRunClassPath);
      if (isAndroidBuildVersionAdded) {
        cr.setSystemProperty(AndroidBuildVersion.PROPERTY, "" + builder.getMinApiLevel());
      }
      return cr;
    } finally {
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

  @Override
  public DebugTestConfig debugConfig() {
    // Rethrow exceptions since debug config is usually used in a delayed wrapper which
    // does not declare exceptions.
    try {
      return compile().debugConfig();
    } catch (CompilationFailedException e) {
      throw new RuntimeException(e);
    }
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

  public T setMinApiThreshold(TestRuntime runtime) {
    if (runtime.isDex()) {
      setMinApiThreshold(runtime.asDex().getMinApiLevel());
    }
    return self();
  }

  public T setMinApi(AndroidApiLevel minApiLevel) {
    return setMinApi(minApiLevel.getLevel());
  }

  public T setMinApi(int minApiLevel) {
    this.minApiLevel = minApiLevel;
    return self();
  }

  /** @deprecated use {@link #setMinApi(AndroidApiLevel)} instead. */
  @Deprecated
  public T setMinApi(TestRuntime runtime) {
    if (runtime.isDex()) {
      setMinApi(runtime.asDex().getMinApiLevel());
    }
    return self();
  }

  public T disableDesugaring() {
    return setDisableDesugaring(true);
  }

  public T setDisableDesugaring(boolean disableDesugaring) {
    builder.setDisableDesugaring(disableDesugaring);
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

  public T noDesugaring() {
    builder.setDisableDesugaring(true);
    return self();
  }

  public T allowStdoutMessages() {
    allowStdoutMessages = true;
    return self();
  }

  public T collectStdout() {
    assert stdout == null;
    stdout = new ByteArrayOutputStream();
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

  public T enableCoreLibraryDesugaring(AndroidApiLevel minAPILevel) {
    return enableCoreLibraryDesugaring(minAPILevel, null);
  }

  public T enableCoreLibraryDesugaring(
      AndroidApiLevel minApiLevel, KeepRuleConsumer keepRuleConsumer) {
    assert minApiLevel.getLevel() < AndroidApiLevel.O.getLevel();
    builder.addDesugaredLibraryConfiguration(
        StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING));
    // TODO(b/158543446): This should not be setting an implicit library file. Doing so causes
    //  inconsistent library setup depending on the api level and makes tests hard to read and
    //  reason about.
    // Use P to mimic current Android Studio.
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P));
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
}
