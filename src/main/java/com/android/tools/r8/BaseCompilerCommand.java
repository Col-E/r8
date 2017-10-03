// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.OutputMode;
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

  BaseCompilerCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);

    this.outputPath = null;
    this.outputMode = OutputMode.Indexed;
    this.mode = null;
    this.minApiLevel = 0;
  }

  BaseCompilerCommand(
      AndroidApp app,
      Path outputPath,
      OutputMode outputMode,
      CompilationMode mode,
      int minApiLevel) {
    super(app);
    assert mode != null;
    assert minApiLevel > 0;
    this.outputPath = outputPath;
    this.outputMode = outputMode;
    this.mode = mode;
    this.minApiLevel = minApiLevel;
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

  abstract public static class Builder<C extends BaseCompilerCommand, B extends Builder<C, B>>
      extends BaseCommand.Builder<C, B> {

    private Path outputPath = null;
    private OutputMode outputMode = OutputMode.Indexed;
    private CompilationMode mode;
    private int minApiLevel = AndroidApiLevel.getDefault().getLevel();

    protected Builder(CompilationMode mode) {
      this(AndroidApp.builder(), mode, false);
    }

    protected Builder(CompilationMode mode, boolean ignoreDexInArchive) {
      this(AndroidApp.builder(), mode, ignoreDexInArchive);
    }

    // Internal constructor for testing.
    Builder(AndroidApp app, CompilationMode mode) {
      this(AndroidApp.builder(app), mode, false);
    }

    private Builder(AndroidApp.Builder builder, CompilationMode mode, boolean ignoreDexInArchive) {
      super(builder, ignoreDexInArchive);
      assert mode != null;
      this.mode = mode;
    }

    /** Get current compilation mode. */
    public CompilationMode getMode() {
      return mode;
    }

    /** Set compilation mode. */
    public B setMode(CompilationMode mode) {
      assert mode != null;
      this.mode = mode;
      return self();
    }

    /** Get the output path. Null if not set. */
    public Path getOutputPath() {
      return outputPath;
    }

    /** Get the output mode. */
    public OutputMode getOutputMode() {
      return outputMode;
    }

    /** Set an output path. Must be an existing directory or a zip file. */
    public B setOutputPath(Path outputPath) {
      this.outputPath = outputPath;
      return self();
    }

    /** Set an output mode. */
    public B setOutputMode(OutputMode outputMode) {
      this.outputMode = outputMode;
      return self();
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

    protected void validate() throws CompilationException {
      super.validate();
      if (getAppBuilder().hasMainDexList() && outputMode == OutputMode.FilePerInputClass) {
        throw new CompilationException(
            "Option --main-dex-list cannot be used with --file-per-class");
      }
      FileUtils.validateOutputFile(outputPath);
    }
  }
}
