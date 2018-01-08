// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for commands and command builders for compiler applications/tools which besides an
 * Android application (and optional main-dex list) also configure compilation output, compilation
 * mode and min API level.
 *
 * <p>For concrete builders, see for example {@link D8Command.Builder} and {@link
 * R8Command.Builder}.
 */
public abstract class BaseCompilerCommand extends BaseCommand {

  // TODO(b/70656566): Remove this once the deprecated API is removed.
  @Deprecated
  static class OutputOptions {
    final Path path;
    final OutputMode mode;

    public OutputOptions(Path path, OutputMode mode) {
      this.path = path;
      this.mode = mode;
    }
  }

  private final OutputOptions outputOptions;

  private final CompilationMode mode;
  private final ProgramConsumer programConsumer;
  private final int minApiLevel;
  private final Reporter reporter;
  private final boolean enableDesugaring;

  BaseCompilerCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    programConsumer = null;
    mode = null;
    minApiLevel = 0;
    reporter = new Reporter(new DefaultDiagnosticsHandler());
    enableDesugaring = true;
    outputOptions = null;
  }

  BaseCompilerCommand(
      AndroidApp app,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      OutputOptions outputOptions,
      int minApiLevel,
      Reporter reporter,
      boolean enableDesugaring) {
    super(app);
    assert minApiLevel > 0;
    assert mode != null;
    this.mode = mode;
    this.programConsumer = programConsumer;
    this.minApiLevel = minApiLevel;
    this.reporter = reporter;
    this.enableDesugaring = enableDesugaring;
    this.outputOptions = outputOptions;
  }

  /**
   * Get the compilation mode, e.g., {@link CompilationMode#DEBUG} or {@link
   * CompilationMode#RELEASE}.
   */
  public CompilationMode getMode() {
    return mode;
  }

  /** Get the minimum API level to compile against. */
  public int getMinApiLevel() {
    return minApiLevel;
  }

  // Package private predicate for the API transition.
  boolean usingDeprecatedAPI() {
    return outputOptions != null;
  }

  @Deprecated
  public Path getOutputPath() {
    if (!usingDeprecatedAPI()) {
      throw new CompilationError("Use of deprecated API may not be used with new consumer API");
    }
    return outputOptions.path;
  }

  @Deprecated
  public OutputMode getOutputMode() {
    if (!usingDeprecatedAPI()) {
      throw new CompilationError("Use of deprecated API may not be used with new consumer API");
    }
    return outputOptions.mode;
  }

  /**
   * Get the program consumer that will receive the compilation output.
   *
   * <p>Note that the concrete consumer reference is final, the consumer itself is likely stateful.
   */
  public ProgramConsumer getProgramConsumer() {
    return programConsumer;
  }

  /** Get the use-desugaring state. True if enabled, false otherwise. */
  public boolean getEnableDesugaring() {
    return enableDesugaring;
  }

  Reporter getReporter() {
    return reporter;
  }
  /**
   * Base builder for compilation commands.
   *
   * @param <C> Command the builder is building, e.g., {@link R8Command} or {@link D8Command}.
   * @param <B> Concrete builder extending this base, e.g., {@link R8Command.Builder} or {@link
   *     D8Command.Builder}.
   */
  public abstract static class Builder<C extends BaseCompilerCommand, B extends Builder<C, B>>
      extends BaseCommand.Builder<C, B> {

    private ProgramConsumer programConsumer = null;
    private Path outputPath = null;
    // TODO(b/70656566): Remove default output mode when deprecated API is removed.
    private OutputMode outputMode = OutputMode.Indexed;

    private CompilationMode mode;
    private int minApiLevel = AndroidApiLevel.getDefault().getLevel();
    private boolean enableDesugaring = true;

    Builder() {}

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    // Internal constructor for testing.
    Builder(AndroidApp app) {
      super(AndroidApp.builder(app));
    }

    // Internal constructor for testing.
    Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(AndroidApp.builder(app), diagnosticsHandler);
    }

    /**
     * Get current compilation mode.
     */
    public CompilationMode getMode() {
      return mode;
    }

    /**
     * Set compilation mode.
     */
    public B setMode(CompilationMode mode) {
      assert mode != null;
      this.mode = mode;
      return self();
    }

    /**
     * Get the output path.
     *
     * @return Current output path, null if no output path-and-mode have been set.
     * @see #setOutput(Path, OutputMode)
     */
    public Path getOutputPath() {
      return outputPath;
    }

    /**
     * Get the output mode.
     *
     * @return Currently set output mode, null if no output path-and-mode have been set.
     * @see #setOutput(Path, OutputMode)
     */
    public OutputMode getOutputMode() {
      return outputMode;
    }

    /**
     * Get the program consumer.
     *
     * @return The currently set program consumer, null if no program consumer or output
     *     path-and-mode is set, e.g., neither {@link #setProgramConsumer} nor
     *     {@link #setOutput} have been called.
     */
    public ProgramConsumer getProgramConsumer() {
      return programConsumer;
    }

    /**
     * Set the program consumer.
     *
     * <p>Setting the program consumer will override any previous set consumer or any previous set
     * output path & mode.
     *
     * @param programConsumer Program consumer to set as current. A null argument will clear the
     *     program consumer / output.
     */
    public B setProgramConsumer(ProgramConsumer programConsumer) {
      // Setting an explicit program consumer resets any output-path/mode setup.
      outputPath = null;
      outputMode = null;
      this.programConsumer = programConsumer;
      return self();
    }

    /**
     * Set the output path-and-mode.
     *
     * <p>Setting the output path-and-mode will override any previous set consumer or any previous
     * output path-and-mode, and implicitly sets the appropriate program consumer to write the
     * output.
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     */
    public B setOutput(Path outputPath, OutputMode outputMode) {
      assert outputPath != null;
      assert outputMode != null;
      assert !outputMode.isDeprecated();
      this.outputPath = outputPath;
      this.outputMode = outputMode;
      programConsumer = createProgramOutputConsumer(outputPath, outputMode);
      return self();
    }

    /**
     * Set an output path. Must be an existing directory or a zip file.
     *
     * @see #setOutput
     */
    @Deprecated
    public B setOutputPath(Path outputPath) {
      // Ensure this is not mixed with uses of the new consumer API.
      assert programConsumer == null;
      this.outputPath = outputPath;
      return self();
    }

    /**
     * Set an output mode.
     *
     * @see #setOutput
     */
    @Deprecated
    public B setOutputMode(OutputMode outputMode) {
      // Ensure this is not mixed with uses of the new consumer API.
      assert programConsumer == null;
      assert outputMode == null || outputMode.isDeprecated();
      assert this.outputMode == null || this.outputMode.isDeprecated();
      this.outputMode = outputMode;
      return self();
    }

    private InternalProgramOutputPathConsumer createProgramOutputConsumer(
        Path path,
        OutputMode mode) {
      if (mode.isDexIndexed()) {
        return FileUtils.isArchive(path)
            ? new DexIndexedConsumer.ArchiveConsumer(path)
            : new DexIndexedConsumer.DirectoryConsumer(path);
      }
      if (mode.isDexFilePerClassFile()) {
        return FileUtils.isArchive(path)
            ? new DexFilePerClassFileConsumer.ArchiveConsumer(path)
            : new DexFilePerClassFileConsumer.DirectoryConsumer(path);
      }
      if (mode.isClassFile()) {
        return FileUtils.isArchive(path)
            ? new ClassFileConsumer.ArchiveConsumer(path)
            : new ClassFileConsumer.DirectoryConsumer(path);
      }
      throw new Unreachable("Unexpected output mode: " + mode);
    }

    /** Get the minimum API level (aka SDK version). */
    public int getMinApiLevel() {
      return minApiLevel;
    }

    /** Set the minimum required API level (aka SDK version). */
    public B setMinApiLevel(int minApiLevel) {
      assert minApiLevel > 0;
      this.minApiLevel = minApiLevel;
      return self();
    }

    /**
     * Force enable or disable desugaring.
     *
     * <p>There are a few use cases where it makes sense to force disable desugaring, such as:
     * <li>if all inputs are known to be at most Java 7; or
     * <li>if a separate desugar tool has been used prior to compiling with D8.
     *
     * <p>Note that even for API 27, desugaring is still required for closures support on ART.
     */
    public B setEnableDesugaring(boolean enableDesugaring) {
      this.enableDesugaring = enableDesugaring;
      return self();
    }

    /** Get the enable/disable state of desugaring. */
    public boolean getEnableDesugaring() {
      return enableDesugaring;
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (mode == null) {
        reporter.error("Expected valid compilation mode, was null");
      }
      FileUtils.validateOutputFile(outputPath, reporter);
      List<Class> programConsumerClasses = new ArrayList<>(3);
      if (programConsumer instanceof DexIndexedConsumer) {
        programConsumerClasses.add(DexIndexedConsumer.class);
      }
      if (programConsumer instanceof DexFilePerClassFileConsumer) {
        programConsumerClasses.add(DexFilePerClassFileConsumer.class);
      }
      if (programConsumer instanceof ClassFileConsumer) {
        programConsumerClasses.add(ClassFileConsumer.class);
      }
      if (programConsumerClasses.size() > 1) {
        StringBuilder builder = new StringBuilder()
            .append("Invalid program consumer.")
            .append(" A program consumer can implement at most one consumer type but ")
            .append(programConsumer.getClass().getName())
            .append(" implements types:");
        for (Class clazz : programConsumerClasses) {
          builder.append(" ").append(clazz.getName());
        }
        reporter.error(builder.toString());
      }
      super.validate();
    }
  }
}
