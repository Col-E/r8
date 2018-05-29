// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class D8CommandParser extends BaseCompilerCommandParser {

  public static void main(String[] args) throws CompilationFailedException {
    D8Command command = parse(args, Origin.root()).build();
    if (command.isPrintHelp()) {
      System.out.println(USAGE_MESSAGE);
      System.exit(1);
    }
    D8.run(command);
  }

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          Arrays.asList(
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

  /**
   * Parse the D8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return D8 command builder with state set up according to parsed command line.
   */
  public static D8Command.Builder parse(String[] args, Origin origin) {
    return parse(args, origin, D8Command.builder());
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
  public static D8Command.Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return parse(args, origin, D8Command.builder(handler));
  }

  private static D8Command.Builder parse(String[] args, Origin origin, D8Command.Builder builder) {
    CompilationMode compilationMode = null;
    Path outputPath = null;
    OutputMode outputMode = null;
    boolean hasDefinedApiLevel = false;
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder);
    try {
      for (int i = 0; i < expandedArgs.length; i++) {
        String arg = expandedArgs[i].trim();
        if (arg.length() == 0) {
          continue;
        } else if (arg.equals("--help")) {
          builder.setPrintHelp(true);
        } else if (arg.equals("--version")) {
          builder.setPrintVersion(true);
        } else if (arg.equals("--debug")) {
          if (compilationMode == CompilationMode.RELEASE) {
            builder.error(
                new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
            continue;
          }
          compilationMode = CompilationMode.DEBUG;
        } else if (arg.equals("--release")) {
          if (compilationMode == CompilationMode.DEBUG) {
            builder.error(
                new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
            continue;
          }
          compilationMode = CompilationMode.RELEASE;
        } else if (arg.equals("--file-per-class")) {
          outputMode = OutputMode.DexFilePerClassFile;
        } else if (arg.equals("--output")) {
          String output = expandedArgs[++i];
          if (outputPath != null) {
            builder.error(
                new StringDiagnostic(
                    "Cannot output both to '" + outputPath.toString() + "' and '" + output + "'",
                    origin));
            continue;
          }
          outputPath = Paths.get(output);
        } else if (arg.equals("--lib")) {
          builder.addLibraryFiles(Paths.get(expandedArgs[++i]));
        } else if (arg.equals("--classpath")) {
          builder.addClasspathFiles(Paths.get(expandedArgs[++i]));
        } else if (arg.equals("--main-dex-list")) {
          builder.addMainDexListFiles(Paths.get(expandedArgs[++i]));
        } else if (arg.equals("--optimize-multidex-for-linearalloc")) {
          builder.setOptimizeMultidexForLinearAlloc(true);
        } else if (arg.equals("--min-api")) {
          String minApiString = expandedArgs[++i];
          if (hasDefinedApiLevel) {
            builder.error(new StringDiagnostic("Cannot set multiple --min-api options", origin));
          } else {
            parseMinApi(builder, minApiString, origin);
            hasDefinedApiLevel = true;
          }
        } else if (arg.equals("--intermediate")) {
          builder.setIntermediate(true);
        } else if (arg.equals("--no-desugaring")) {
          builder.setDisableDesugaring(true);
        } else {
          if (arg.startsWith("--")) {
            builder.error(new StringDiagnostic("Unknown option: " + arg, origin));
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
      throw builder.fatalError(e);
    }
  }
}
