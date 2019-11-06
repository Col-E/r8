// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.desugar.corelib.CoreLibDesugarTestBase.KeepRuleConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.base.Suppliers;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class TestCompilerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<CR, RR>,
        RR extends TestRunResult,
        T extends TestCompilerBuilder<C, B, CR, RR, T>>
    extends TestBaseBuilder<C, B, CR, RR, T> {

  public static final Consumer<InternalOptions> DEFAULT_OPTIONS =
      options -> {
        options.testing.allowUnusedProguardConfigurationRules = false;
        options.testing.reportUnusedProguardConfigurationRules = true;
      };

  final Backend backend;

  // Default initialized setup. Can be overwritten if needed.
  private boolean useDefaultRuntimeLibrary = true;
  final List<Path> additionalRunClassPath = new ArrayList<>();
  private ProgramConsumer programConsumer;
  private StringConsumer mainDexListConsumer;
  private AndroidApiLevel defaultMinApiLevel = ToolHelper.getMinApiLevelForDexVm();
  private Consumer<InternalOptions> optionsConsumer = DEFAULT_OPTIONS;
  private PrintStream stdout = null;
  protected OutputMode outputMode = OutputMode.DexIndexed;

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

  abstract T self();

  abstract CR internalCompile(
      B builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException;

  public T addOptionsModification(Consumer<InternalOptions> optionsConsumer) {
    if (optionsConsumer != null) {
      this.optionsConsumer = this.optionsConsumer.andThen(optionsConsumer);
    }
    return self();
  }

  public CR compile() throws CompilationFailedException {
    AndroidAppConsumers sink = new AndroidAppConsumers();
    builder.setProgramConsumer(sink.wrapProgramConsumer(programConsumer));
    builder.setMainDexListConsumer(mainDexListConsumer);
    if (backend == Backend.DEX && defaultMinApiLevel != null) {
      builder.setMinApiLevel(defaultMinApiLevel.getLevel());
    }
    if (useDefaultRuntimeLibrary) {
      if (backend == Backend.DEX && builder.isMinApiLevelSet()) {
        builder.addLibraryFiles(
            ToolHelper.getFirstSupportedAndroidJar(
                AndroidApiLevel.getAndroidApiLevel(builder.getMinApiLevel())));
      } else {
        builder.addLibraryFiles(TestBase.runtimeJar(backend));
      }
    }
    PrintStream oldOut = System.out;
    try {
      if (stdout != null) {
        System.setOut(stdout);
      }
      CR cr = internalCompile(builder, optionsConsumer, Suppliers.memoize(sink::build));
      cr.addRunClasspathFiles(additionalRunClassPath);
      return cr;
    } finally {
      if (stdout != null) {
        System.setOut(oldOut);
      }
    }
  }

  public CR compileWithExpectedDiagnostics(
      Consumer<TestDiagnosticMessages> diagnosticsConsumer) throws CompilationFailedException {
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
    if (backend == Backend.DEX) {
      this.defaultMinApiLevel = null;
      builder.setMinApiLevel(minApiLevel.getLevel());
    }
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

  public T addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public abstract T addClasspathClasses(Collection<Class<?>> classes);

  public T addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public abstract T addClasspathFiles(Collection<Path> files);

  public T noDesugaring() {
    builder.setDisableDesugaring(true);
    return self();
  }

  public T redirectStdOut(PrintStream printStream) {
    assert stdout == null;
    stdout = printStream;
    return self();
  }

  @Deprecated
  public T enableCoreLibraryDesugaring() {
    // TODO(b/134732760): Use the other API instead.
    return enableCoreLibraryDesugaring(AndroidApiLevel.B);
  }

  public T enableCoreLibraryDesugaring(AndroidApiLevel minAPILevel) {
    return enableCoreLibraryDesugaring(minAPILevel, null);
  }

  public T enableCoreLibraryDesugaring(
      AndroidApiLevel minAPILevel, KeepRuleConsumer keepRuleConsumer) {
    throw new Unreachable("Should be overridden or is unsupported.");
  }

  @Override
  public T addRunClasspathFiles(Collection<Path> files) {
    additionalRunClassPath.addAll(files);
    return self();
  }
}
