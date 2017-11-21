// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CompilationFailedException;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OutputMode;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

/**
 * Immutable command structure for an invocation of the {@code D8} compiler.
 *
 * <p>To build a D8 command use the {@code D8Command.Builder} class. For example:
 *
 * <pre>
 *   D8Command command = D8Command.builder()
 *     .addProgramFiles(path1, path2)
 *     .setMode(CompilationMode.RELEASE)
 *     .build();
 * </pre>
 */
public class D8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a D8Command.
   */
  public static class Builder extends BaseCompilerCommand.Builder<D8Command, Builder> {

    private boolean intermediate = false;

    protected Builder() {
      setMode(CompilationMode.DEBUG);
    }

    protected Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      setMode(CompilationMode.DEBUG);
    }

    private Builder(AndroidApp app) {
      super(app);
      setMode(CompilationMode.DEBUG);
   }

    /** Add classpath file resources. */
    public Builder addClasspathFiles(Path... files) {
      Arrays.stream(files).forEach(this::addClasspathFile);
      return self();
    }

    /** Add classpath file resources. */
    public Builder addClasspathFiles(Collection<Path> files) {
      files.forEach(this::addClasspathFile);
      return self();
    }

    private void addClasspathFile(Path file) {
      try {
        getAppBuilder().addClasspathFile(file);
      } catch (IOException e) {
        error("Error with classpath entry: ", file, e);
      }
    }

    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      getAppBuilder().addClasspathResourceProvider(provider);
      return self();
    }

    public Builder setIntermediate(boolean value) {
      this.intermediate = value;
      return self();
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    protected void validate() throws CompilationFailedException {
      if (getAppBuilder().hasMainDexList() && intermediate) {
        reporter.error("Option --main-dex-list cannot be used with --intermediate");
      }
      super.validate();
    }

    /**
     * Build the final D8Command.
     */
    @Override
    public D8Command build() throws CompilationFailedException {
      if (isPrintHelp() || isPrintVersion()) {
        return new D8Command(isPrintHelp(), isPrintVersion());
      }

      validate();
      D8Command command = new D8Command(
          getAppBuilder().build(),
          getOutputPath(),
          getOutputMode(),
          getMode(),
          getMinApiLevel(),
          reporter,
          getEnableDesugaring(),
          intermediate);

      failIfPendingErrors();

      return command;
    }
  }

  static final String USAGE_MESSAGE = String.join("\n", ImmutableList.of(
      "Usage: d8 [options] <input-files>",
      " where <input-files> are any combination of dex, class, zip, jar, or apk files",
      " and options are:",
      "  --debug                 # Compile with debugging information (default).",
      "  --release               # Compile without debugging information.",
      "  --output <file>         # Output result in <outfile>.",
      "                          # <file> must be an existing directory or a zip file.",
      "  --lib <file>            # Add <file> as a library resource.",
      "  --classpath <file>      # Add <file> as a classpath resource.",
      "  --min-api               # Minimum Android API level compatibility",
      "  --intermediate          # Compile an intermediate result intended for later",
      "                          # merging.",
      "  --file-per-class        # Produce a separate dex file per class",
      "  --main-dex-list <file>  # List of classes to place in the primary dex file.",
      "  --version               # Print the version of d8.",
      "  --help                  # Print this message."));

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

  public static Builder parse(String[] args, Location location) throws CompilationFailedException {
    CompilationMode modeSet = null;
    Path outputPath = null;
    Builder builder = builder();
    try {
      for (int i = 0; i < args.length; i++) {
        String arg = args[i].trim();
        if (arg.length() == 0) {
          continue;
        } else if (arg.equals("--help")) {
          builder.setPrintHelp(true);
        } else if (arg.equals("--version")) {
          builder.setPrintVersion(true);
        } else if (arg.equals("--debug")) {
          if (modeSet == CompilationMode.RELEASE) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot compile in both --debug and --release mode.",
                location));
            continue;
          }
          builder.setMode(CompilationMode.DEBUG);
          modeSet = CompilationMode.DEBUG;
        } else if (arg.equals("--release")) {
          if (modeSet == CompilationMode.DEBUG) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot compile in both --debug and --release mode.",
                location));
            continue;
          }
          builder.setMode(CompilationMode.RELEASE);
          modeSet = CompilationMode.RELEASE;
        } else if (arg.equals("--file-per-class")) {
          builder.setOutputMode(OutputMode.FilePerInputClass);
        } else if (arg.equals("--output")) {
          String output = args[++i];
          if (outputPath != null) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot output both to '" + outputPath.toString() + "' and '" + output + "'",
                location));
            continue;
          }
          outputPath = Paths.get(output);
        } else if (arg.equals("--lib")) {
          builder.addLibraryFiles(Paths.get(args[++i]));
        } else if (arg.equals("--classpath")) {
          builder.addClasspathFiles(Paths.get(args[++i]));
        } else if (arg.equals("--main-dex-list")) {
          builder.addMainDexListFiles(Paths.get(args[++i]));
        } else if (arg.equals("--min-api")) {
          builder.setMinApiLevel(Integer.valueOf(args[++i]));
        } else if (arg.equals("--intermediate")) {
          builder.setIntermediate(true);
        } else {
          if (arg.startsWith("--")) {
            builder.getReporter().error(new StringDiagnostic("Unknown option: " + arg,
                location));
            continue;
          }
          builder.addProgramFiles(Paths.get(arg));
        }
      }
      return builder.setOutputPath(outputPath);
    } catch (CompilationError e) {
      throw builder.fatalError(e);
    }
  }

  private D8Command(
      AndroidApp inputApp,
      Path outputPath,
      OutputMode outputMode,
      CompilationMode mode,
      int minApiLevel,
      Reporter diagnosticsHandler,
      boolean enableDesugaring,
      boolean intermediate) {
    super(inputApp, outputPath, outputMode, mode, minApiLevel, diagnosticsHandler,
        enableDesugaring);
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
    internal.minimalMainDex = internal.debug;
    internal.minApiLevel = getMinApiLevel();
    internal.intermediate = intermediate;
    // Assert and fixup defaults.
    assert !internal.skipMinification;
    internal.skipMinification = true;
    assert internal.useTreeShaking;
    internal.useTreeShaking = false;

    // Disable some of R8 optimizations.
    assert internal.inlineAccessors;
    internal.inlineAccessors = false;
    assert internal.removeSwitchMaps;
    internal.removeSwitchMaps = false;
    assert internal.outline.enabled;
    internal.outline.enabled = false;
    assert internal.propagateMemberValue;
    internal.propagateMemberValue = false;

    internal.outputMode = getOutputMode();
    internal.enableDesugaring = getEnableDesugaring();
    return internal;
  }
}
