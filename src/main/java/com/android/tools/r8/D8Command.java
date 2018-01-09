// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
public class D8Command extends BaseCompilerCommand {

  /**
   * Builder for constructing a D8Command.
   *
   * <p>A builder is obtained by calling {@link D8Command#builder}.
   */
  public static class Builder extends BaseCompilerCommand.Builder<D8Command, Builder> {

    private boolean intermediate = false;

    Builder() {
      setMode(CompilationMode.DEBUG);
    }

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      setMode(CompilationMode.DEBUG);
    }

    private Builder(AndroidApp app) {
      super(app);
      setMode(CompilationMode.DEBUG);
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
          error("Error with classpath entry: ", file, e);
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
          intermediate);
    }

    private static DexIndexedConsumer createIndexedConsumer(Path path) {
      if (path == null) {
        return DexIndexedConsumer.emptyConsumer();
      }
      return FileUtils.isArchive(path)
          ? new DexIndexedConsumer.ArchiveConsumer(path)
          : new DexIndexedConsumer.DirectoryConsumer(path);
    }

    private static DexFilePerClassFileConsumer createPerClassFileConsumer(Path path) {
      if (path == null) {
        return DexFilePerClassFileConsumer.emptyConsumer();
      }
      return FileUtils.isArchive(path)
          ? new DexFilePerClassFileConsumer.ArchiveConsumer(path)
          : new DexFilePerClassFileConsumer.DirectoryConsumer(path);
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
      "  --file-per-class        # Produce a separate dex file per input class",
      "  --no-desugaring         # Force disable desugaring.",
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
    return parse(args, origin, builder());
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
    return parse(args, origin, builder(handler));
  }

  private static Builder parse(String[] args, Origin origin, Builder builder) {
    CompilationMode compilationMode = null;
    Path outputPath = null;
    OutputMode outputMode = null;
    boolean hasDefinedApiLevel = false;
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
          if (compilationMode == CompilationMode.RELEASE) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot compile in both --debug and --release mode.",
                origin));
            continue;
          }
          compilationMode = CompilationMode.DEBUG;
        } else if (arg.equals("--release")) {
          if (compilationMode == CompilationMode.DEBUG) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot compile in both --debug and --release mode.",
                origin));
            continue;
          }
          compilationMode = CompilationMode.RELEASE;
        } else if (arg.equals("--file-per-class")) {
          outputMode = OutputMode.DexFilePerClassFile;
        } else if (arg.equals("--output")) {
          String output = args[++i];
          if (outputPath != null) {
            builder.getReporter().error(new StringDiagnostic(
                "Cannot output both to '" + outputPath.toString() + "' and '" + output + "'",
                origin));
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
          hasDefinedApiLevel = parseMinApi(builder, args[++i], hasDefinedApiLevel, origin);
        } else if (arg.equals("--intermediate")) {
          builder.setIntermediate(true);
        } else if (arg.equals("--no-desugaring")) {
          builder.setDisableDesugaring(true);
        } else {
          if (arg.startsWith("--")) {
            builder.getReporter().error(new StringDiagnostic("Unknown option: " + arg,
                origin));
            continue;
          }
          builder.addProgramFiles(Paths.get(arg));
        }
      }
      if (compilationMode != null) {
        builder.setMode(compilationMode);
      }
      if (outputMode == null) {
        outputMode = OutputMode.DexIndexed;
      }
      if (outputPath == null) {
        outputPath = Paths.get(".");
      }
      return builder.setOutput(outputPath, outputMode);
    } catch (CompilationError e) {
      throw builder.getReporter().fatalError(e);
    }
  }

  private D8Command(
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      boolean enableDesugaring,
      boolean intermediate) {
    super(
        inputApp,
        mode,
        programConsumer,
        minApiLevel,
        diagnosticsHandler,
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
    internal.programConsumer = getProgramConsumer();
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

    internal.enableDesugaring = getEnableDesugaring();
    return internal;
  }
}
