// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.tracereferences.TraceReferencesFormattingConsumer.OutputFormat;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FlagFile;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Set;

class TraceReferencesCommandParser {

  private static final Set<String> OPTIONS_WITH_PARAMETER =
      ImmutableSet.of("--lib", "--target", "--source", "--format", "--output");

  static final String USAGE_MESSAGE =
      String.join(
          "\n",
          Arrays.asList(
              "Usage: referencetrace [options] [@<argfile>]",
              " Each <argfile> is a file containing additional arguments (one per line)",
              " and options are:",
              "  --lib <file|jdk-home>   # Add <file|jdk-home> as a library resource.",
              "  --target <file>         # Add <file> as a classpath resource.",
              "  --source <file>         # Add <file> as a classpath resource.",
              "  --output <file>         # Output result in <outfile>.",
              "  --version               # Print the version of referencetrace.",
              "  --help                  # Print this message."));
  /**
   * Parse the referencetrace command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return referencetrace command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(String[] args, Origin origin) {
    return new TraceReferencesCommandParser().parse(args, origin, TraceReferencesCommand.builder());
  }

  /**
   * Parse the referencetrace command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return referencetrace command builder with state set up according to parsed command line.
   */
  static TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, DiagnosticsHandler handler) {
    return new TraceReferencesCommandParser()
        .parse(args, origin, TraceReferencesCommand.builder(handler));
  }

  private TraceReferencesCommand.Builder parse(
      String[] args, Origin origin, TraceReferencesCommand.Builder builder) {
    String[] expandedArgs = FlagFile.expandFlagFiles(args, builder::error);
    Path output = null;
    OutputFormat format = TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE;
    for (int i = 0; i < expandedArgs.length; i++) {
      String arg = expandedArgs[i].trim();
      String nextArg = null;
      if (OPTIONS_WITH_PARAMETER.contains(arg)) {
        if (++i < expandedArgs.length) {
          nextArg = expandedArgs[i];
        } else {
          builder.error(
              new StringDiagnostic("Missing parameter for " + expandedArgs[i - 1] + ".", origin));
          break;
        }
      }
      if (arg.length() == 0) {
        continue;
      } else if (arg.equals("--help")) {
        builder.setPrintHelp(true);
      } else if (arg.equals("--version")) {
        builder.setPrintVersion(true);
      } else if (arg.equals("--lib")) {
        addLibraryArgument(builder, origin, nextArg);
      } else if (arg.equals("--target")) {
        builder.addTargetFiles(Paths.get(nextArg));
      } else if (arg.equals("--source")) {
        builder.addSourceFiles(Paths.get(nextArg));
      } else if (arg.equals("--format")) {
        if (nextArg.equals("printuses")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.PRINTUSAGE;
        }
        if (nextArg.equals("keep")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES;
        }
        if (nextArg.equals("keepallowobfuscation")) {
          format = TraceReferencesFormattingConsumer.OutputFormat.KEEP_RULES_WITH_ALLOWOBFUSCATION;
        }
        if (format == null) {
          builder.error(new StringDiagnostic("Unsupported format '" + nextArg + "'"));
        }
      } else if (arg.equals("--output")) {
        output = Paths.get(nextArg);
      } else if (arg.startsWith("@")) {
        builder.error(new StringDiagnostic("Recursive @argfiles are not supported: ", origin));
      } else {
        builder.error(new StringDiagnostic("Unsupported argument '" + arg + "'"));
      }
    }
    final Path finalOutput = output;
    builder.setConsumer(
        new TraceReferencesFormattingConsumer(format) {
          @Override
          public void finished() {
            PrintStream out = System.out;
            if (finalOutput != null) {
              try {
                out = new PrintStream(Files.newOutputStream(finalOutput));
              } catch (IOException e) {
                builder.error(new ExceptionDiagnostic(e));
              }
            }
            out.print(get());
          }
        });
    return builder;
  }

  /**
   * This method must match the lookup in {@link
   * com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
   */
  private static boolean isJdkHome(Path home) {
    Path jrtFsJar = home.resolve("lib").resolve("jrt-fs.jar");
    if (Files.exists(jrtFsJar)) {
      return true;
    }
    // JDK has rt.jar in jre/lib/rt.jar.
    Path rtJar = home.resolve("jre").resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    // JRE has rt.jar in lib/rt.jar.
    rtJar = home.resolve("lib").resolve("rt.jar");
    if (Files.exists(rtJar)) {
      return true;
    }
    return false;
  }

  static void addLibraryArgument(
      TraceReferencesCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder.addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }
}
