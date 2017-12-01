// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.FileSystemOutputSink;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IOExceptionDiagnostic;
import com.android.tools.r8.utils.IgnoreContentsOutputSink;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Base class for commands and command builders for compiler applications/tools which besides an
 * Android application (and a main-dex list) also takes compilation output, compilation mode and
 * min API level as input.
 */
abstract class BaseCompilerCommand extends BaseCommand {

  private final Path outputPath;
  private final OutputMode outputMode;
  private final CompilationMode mode;
  private final int minApiLevel;
  private final Reporter reporter;
  private final boolean enableDesugaring;
  private OutputSink outputSink;

  BaseCompilerCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    outputPath = null;
    outputMode = OutputMode.Indexed;
    mode = null;
    minApiLevel = 0;
    reporter = new Reporter(new DefaultDiagnosticsHandler());
    enableDesugaring = true;
  }

  BaseCompilerCommand(
      AndroidApp app,
      Path outputPath,
      OutputMode outputMode,
      CompilationMode mode,
      int minApiLevel,
      Reporter reporter,
      boolean enableDesugaring) {
    super(app);
    assert mode != null;
    assert minApiLevel > 0;
    this.outputPath = outputPath;
    this.outputMode = outputMode;
    this.mode = mode;
    this.minApiLevel = minApiLevel;
    this.reporter = reporter;
    this.enableDesugaring = enableDesugaring;
  }

  public Path getOutputPath() {
    return outputPath;
  }

  public CompilationMode getMode() {
    return mode;
  }

  public int getMinApiLevel() {
    return minApiLevel;
  }

  public OutputMode getOutputMode() {
    return outputMode;
  }

  public Reporter getReporter() {
    return reporter;
  }

  private OutputSink createOutputSink() {
    if (outputPath == null) {
      return new IgnoreContentsOutputSink();
    } else {
      try {
        // TODO(zerny): Calling getInternalOptions here is incorrect since any modifications by an
        // options consumer will not be visible to the sink.
        return FileSystemOutputSink.create(outputPath, getInternalOptions());
      } catch (IOException e) {
        throw reporter.fatalError(
            new IOExceptionDiagnostic(e, new Location(new PathOrigin(outputPath))));
      }
    }
  }

  public OutputSink getOutputSink() {
    if (outputSink == null) {
      outputSink = createOutputSink();
    }
    return outputSink;
  }

  public boolean getEnableDesugaring() {
    return enableDesugaring;
  }

  abstract public static class Builder<C extends BaseCompilerCommand, B extends Builder<C, B>>
      extends BaseCommand.Builder<C, B> {

    private Path outputPath = null;
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
     * Get the output path. Null if not set.
     */
    public Path getOutputPath() {
      return outputPath;
    }

    /**
     * Get the output mode.
     */
    public OutputMode getOutputMode() {
      return outputMode;
    }

    /**
     * Set an output path. Must be an existing directory or a zip file.
     */
    public B setOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return self();
    }

    /**
     * Set an output mode.
     */
    public B setOutputMode(OutputMode outputMode) {
      this.outputMode = outputMode;
      return self();
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
      if (getAppBuilder().hasMainDexList() && outputMode == OutputMode.FilePerInputClass) {
        reporter.error("Option --main-dex-list cannot be used with --file-per-class");
      }
      FileUtils.validateOutputFile(outputPath, reporter);
      super.validate();
    }
  }
}
