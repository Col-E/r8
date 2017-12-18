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

/**
 * Base class for commands and command builders for compiler applications/tools which besides an
 * Android application (and a main-dex list) also takes compilation output, compilation mode and
 * min API level as input.
 */
abstract class BaseCompilerCommand extends BaseCommand {

  // TODO(b/70656566): Remove this once the deprecated API is removed.
  protected static class OutputOptions {
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

  public CompilationMode getMode() {
    return mode;
  }

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

  public ProgramConsumer getProgramConsumer() {
    return programConsumer;
  }

  public Reporter getReporter() {
    return reporter;
  }

  public boolean getEnableDesugaring() {
    return enableDesugaring;
  }

  abstract public static class Builder<C extends BaseCompilerCommand, B extends Builder<C, B>>
      extends BaseCommand.Builder<C, B> {

    private ProgramConsumer programConsumer = null;
    private Path outputPath = null;
    // TODO(b/70656566): Remove default output mode when deprecated API is removed.
    private OutputMode outputMode = OutputMode.Indexed;

    private CompilationMode mode;
    private int minApiLevel = AndroidApiLevel.getDefault().getLevel();
    private boolean enableDesugaring = true;

    protected Builder() {
    }

    // Internal constructor for testing.
    Builder(AndroidApp app) {
      super(AndroidApp.builder(app));
    }

    Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(AndroidApp.builder(app), diagnosticsHandler);
    }

    protected Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
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

    /**
     * Get the minimum API level (aka SDK version).
     */
    public int getMinApiLevel() {
      return minApiLevel;
    }

    /**
     * Set the minimum required API level (aka SDK version).
     */
    public B setMinApiLevel(int minApiLevel) {
      assert minApiLevel > 0;
      this.minApiLevel = minApiLevel;
      return self();
    }

    public B setEnableDesugaring(boolean enableDesugaring) {
      this.enableDesugaring = enableDesugaring;
      return self();
    }

    public boolean getEnableDesugaring() {
      return enableDesugaring;
    }

    @Override
    protected void validate() {
      assert mode != null;
      FileUtils.validateOutputFile(outputPath, reporter);
      super.validate();
    }
  }
}
