// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

/**
 * Immutable command structure for an invocation of the {@link D8} compiler.
 *
 * <p>To build a D8 command use the {@link D8Command.Builder} class. For example:
 *
 * <pre>
 *   D8Command command = D8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .setOutput(Paths.get("output.zip", OutputMode.DexIndexed))
 *     .build();
 * </pre>
 */
@Keep
public final class D8Command extends BaseCompilerCommand {

  private static class ClasspathInputOrigin extends InputFileOrigin {

    public ClasspathInputOrigin(Path file) {
      super("classpath input", file);
    }
  }

  /**
   * Builder for constructing a D8Command.
   *
   * <p>A builder is obtained by calling {@link D8Command#builder}.
   */
  @Keep
  public static class Builder extends BaseCompilerCommand.Builder<D8Command, Builder> {

    private boolean intermediate = false;

    private Builder() {}

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    private Builder(AndroidApp app) {
      super(app);
    }

    /** Add dex program-data. */
    public Builder addDexProgramData(byte[] data, Origin origin) {
      guard(() -> getAppBuilder().addDexProgramData(data, origin));
      return self();
    }

    /** Add classpath file resources. */
    public Builder addClasspathFiles(Path... files) {
      guard(() -> Arrays.stream(files).forEach(this::addClasspathFile));
      return self();
    }

    /** Add classpath file resources. */
    public Builder addClasspathFiles(Collection<Path> files) {
      guard(() -> files.forEach(this::addClasspathFile));
      return self();
    }

    private void addClasspathFile(Path file) {
      guard(() -> {
        try {
          getAppBuilder().addClasspathFile(file);
        } catch (IOException e) {
          error(new ClasspathInputOrigin(file), e);
        }
      });
    }

    /** Add classfile resources provider for class-path resources. */
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      guard(() -> getAppBuilder().addClasspathResourceProvider(provider));
      return self();
    }

    /**
     * Indicate if compilation is to intermediate results, i.e., intended for later merging.
     *
     * <p>Intermediate mode is implied if compiling results to a "file-per-class-file".
     */
    public Builder setIntermediate(boolean value) {
      this.intermediate = value;
      return self();
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.DEBUG;
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (getProgramConsumer() instanceof ClassFileConsumer) {
        reporter.error("D8 does not support compiling to Java class files");
      }
      if (getAppBuilder().hasMainDexList()) {
        if (intermediate) {
          reporter.error("Option --main-dex-list cannot be used with --intermediate");
        }
        if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
          reporter.error("Option --main-dex-list cannot be used with --file-per-class");
        }
      }
      super.validate();
    }

    @Override
    D8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new D8Command(isPrintHelp(), isPrintVersion());
      }

      intermediate |= getProgramConsumer() instanceof DexFilePerClassFileConsumer;

      return new D8Command(
          getAppBuilder().build(),
          getMode(),
          getProgramConsumer(),
          getMinApiLevel(),
          getReporter(),
          !getDisableDesugaring(),
          intermediate,
          isOptimizeMultidexForLinearAlloc());
    }
  }

  static final String USAGE_MESSAGE = D8CommandParser.USAGE_MESSAGE;

  private boolean intermediate = false;

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  // Internal builder to start from an existing AndroidApp.
  static Builder builder(AndroidApp app) {
    return new Builder(app);
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return D8CommandParser.parse(args, origin);
  }

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return D8CommandParser.parse(args, origin, handler);
  }

  private D8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      boolean enableDesugaring,
      boolean intermediate,
      boolean optimizeMultidexForLinearAlloc) {
    super(
        inputApp,
        mode,
        programConsumer,
        minApiLevel,
        diagnosticsHandler,
        enableDesugaring,
        optimizeMultidexForLinearAlloc);
    this.intermediate = intermediate;
  }

  private D8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(new DexItemFactory(), getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    internal.programConsumer = getProgramConsumer();
    internal.minimalMainDex = internal.debug;
    internal.minApiLevel = getMinApiLevel();
    internal.intermediate = intermediate;
    // Assert and fixup defaults.
    assert internal.enableMinification;
    internal.enableMinification = false;
    assert internal.enableTreeShaking;
    internal.enableTreeShaking = false;
    assert !internal.passthroughDexCode;
    internal.passthroughDexCode = true;

    // Disable some of R8 optimizations.
    assert internal.enableInlining;
    internal.enableInlining = false;
    assert internal.enableClassInlining;
    internal.enableClassInlining = false;
    assert internal.enableClassStaticizer;
    internal.enableClassStaticizer = false;
    assert internal.enableSwitchMapRemoval;
    internal.enableSwitchMapRemoval = false;
    assert internal.outline.enabled;
    internal.outline.enabled = false;
    assert internal.enableValuePropagation;
    internal.enableValuePropagation = false;

    internal.enableDesugaring = getEnableDesugaring();
    internal.enableLambdaMerging = false;
    internal.enableInheritanceClassInDexDistributor = isOptimizeMultidexForLinearAlloc();
    return internal;
  }
}
