// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.AssertionsConfiguration.AssertionTransformation;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Consumer;

public class BaseCompilerCommandParser<
    C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>> {

  protected static final String MIN_API_FLAG = "--min-api";
  protected static final String THREAD_COUNT_FLAG = "--thread-count";
  protected static final String MAP_DIAGNOSTICS = "--map-diagnostics";

  static final Iterable<String> ASSERTIONS_USAGE_MESSAGE =
      Arrays.asList(
          "  --force-enable-assertions[:[<class name>|<package name>...]]",
          "  --force-ea[:[<class name>|<package name>...]]",
          "                          # Forcefully enable javac generated assertion code.",
          "  --force-disable-assertions[:[<class name>|<package name>...]]",
          "  --force-da[:[<class name>|<package name>...]]",
          "                          # Forcefully disable javac generated assertion code. This",
          "                          # is the default handling of javac assertion code when",
          "                          # generating DEX file format.",
          "  --force-passthrough-assertions[:[<class name>|<package name>...]]",
          "  --force-pa[:[<class name>|<package name>...]]",
          "                          # Don't change javac generated assertion code. This",
          "                          # is the default handling of javac assertion code when",
          "                          # generating class file format.");

  static final Iterable<String> THREAD_COUNT_USAGE_MESSAGE =
      Arrays.asList(
          "  " + THREAD_COUNT_FLAG + " <number of threads>",
          "                          # Number of threads to use for compilation. If not specified",
          "                          # the number will be based on heuristics taking the number",
          "                          # of cores into account.");

  public static final Iterable<String> MAP_DIAGNOSTICS_USAGE_MESSAGE =
      Arrays.asList(
          "  " + MAP_DIAGNOSTICS + "[:<type>] <from-level> <to-level>",
          "                          # Map diagnostics of <type> (default any) reported as",
          "                          # <from-level> to <to-level> where <from-level> and",
          "                          # <to-level> are one of 'info', 'warning', or 'error' and the",
          "                          # optional <type> is either the simple or fully qualified",
          "                          # Java type name of a diagnostic. If <type> is unspecified,",
          "                          # all diagnostics at <from-level> will be mapped.",
          "                          # Note that fatal compiler errors cannot be mapped.");

  public static void parsePositiveIntArgument(
      Consumer<Diagnostic> errorConsumer,
      String flag,
      String argument,
      Origin origin,
      Consumer<Integer> setter) {
    int value;
    try {
      value = Integer.parseInt(argument);
    } catch (NumberFormatException e) {
      errorConsumer.accept(
          new StringDiagnostic("Invalid argument to " + flag + ": " + argument, origin));
      return;
    }
    if (value < 1) {
      errorConsumer.accept(
          new StringDiagnostic("Invalid argument to " + flag + ": " + argument, origin));
      return;
    }
    setter.accept(value);
  }

  private static String PACKAGE_ASSERTION_POSTFIX = "...";

  private void addAssertionTransformation(
      B builder, AssertionTransformation transformation, String scope) {
    if (scope == null) {
      builder.addAssertionsConfiguration(
          b -> b.setTransformation(transformation).setScopeAll().build());
    } else {
      assert scope.length() > 0;
      if (scope.endsWith(PACKAGE_ASSERTION_POSTFIX)) {
        builder.addAssertionsConfiguration(
            b ->
                b.setTransformation(transformation)
                    .setScopePackage(
                        scope.substring(0, scope.length() - PACKAGE_ASSERTION_POSTFIX.length()))
                    .build());
      } else {
        builder.addAssertionsConfiguration(
            b -> b.setTransformation(transformation).setScopeClass(scope).build());
      }
    }
  }

  boolean tryParseAssertionArgument(B builder, String arg, Origin origin) {
    String FORCE_ENABLE_ASSERTIONS = "--force-enable-assertions";
    String FORCE_EA = "--force-ea";
    String FORCE_DISABLE_ASSERTIONS = "--force-disable-assertions";
    String FORCE_DA = "--force-da";
    String FORCE_PASSTHROUGH_ASSERTIONS = "--force-passthrough-assertions";
    String FORCE_PA = "--force-pa";

    AssertionTransformation transformation = null;
    String remaining = null;
    if (arg.startsWith(FORCE_ENABLE_ASSERTIONS)) {
      transformation = AssertionTransformation.ENABLE;
      remaining = arg.substring(FORCE_ENABLE_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_EA)) {
      transformation = AssertionTransformation.ENABLE;
      remaining = arg.substring(FORCE_EA.length());
    } else if (arg.startsWith(FORCE_DISABLE_ASSERTIONS)) {
      transformation = AssertionTransformation.DISABLE;
      remaining = arg.substring(FORCE_DISABLE_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_DA)) {
      transformation = AssertionTransformation.DISABLE;
      remaining = arg.substring(FORCE_DA.length());
    } else if (arg.startsWith(FORCE_PASSTHROUGH_ASSERTIONS)) {
      transformation = AssertionTransformation.PASSTHROUGH;
      remaining = arg.substring(FORCE_PASSTHROUGH_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_PA)) {
      transformation = AssertionTransformation.PASSTHROUGH;
      remaining = arg.substring(FORCE_PA.length());
    }
    if (transformation != null) {
      if (remaining.length() == 0) {
        addAssertionTransformation(builder, transformation, null);
        return true;
      } else {
        if (remaining.length() == 1 || remaining.charAt(0) != ':') {
          return false;
        }
        String classOrPackageScope = remaining.substring(1);
        if (classOrPackageScope.contains(";")
            || classOrPackageScope.contains("[")
            || classOrPackageScope.contains("/")) {
          builder.error(
              new StringDiagnostic("Illegal assertion scope: " + classOrPackageScope, origin));
        }
        addAssertionTransformation(builder, transformation, remaining.substring(1));
        return true;
      }
    } else {
      return false;
    }
  }

  int tryParseMapDiagnostics(B builder, String arg, String[] args, int argsIndex, Origin origin) {
    return tryParseMapDiagnostics(
        builder::error, builder.getReporter(), arg, args, argsIndex, origin);
  }

  private static DiagnosticsLevel tryParseLevel(
      Consumer<Diagnostic> errorHandler, String arg, Origin origin) {
    if (arg.equals("error")) {
      return DiagnosticsLevel.ERROR;
    }
    if (arg.equals("warning")) {
      return DiagnosticsLevel.WARNING;
    }
    if (arg.equals("info")) {
      return DiagnosticsLevel.INFO;
    }
    errorHandler.accept(
        new StringDiagnostic(
            "Invalid diagnostics level '"
                + arg
                + "'. Valid levels are 'error', 'warning' and 'info'.",
            origin));

    return null;
  }

  public static int tryParseMapDiagnostics(
      Consumer<Diagnostic> errorHandler,
      Reporter reporter,
      String arg,
      String[] args,
      int argsIndex,
      Origin origin) {
    if (!arg.startsWith(MAP_DIAGNOSTICS)) {
      return -1;
    }
    if (args.length <= argsIndex + 2) {
      errorHandler.accept(new StringDiagnostic("Missing argument(s) for " + arg + ".", origin));
      return args.length - argsIndex;
    }
    String remaining = arg.substring(MAP_DIAGNOSTICS.length());
    String diagnosticsClassName = "";
    if (remaining.length() > 0) {
      if (remaining.length() == 1 || remaining.charAt(0) != ':') {
        errorHandler.accept(
            new StringDiagnostic("Invalid diagnostics type specification " + arg + ".", origin));
        return 0;
      }
      diagnosticsClassName = remaining.substring(1);
    }
    DiagnosticsLevel from = tryParseLevel(errorHandler, args[argsIndex + 1], origin);
    DiagnosticsLevel to = tryParseLevel(errorHandler, args[argsIndex + 2], origin);
    if (from != null && to != null) {
      reporter.addDiagnosticsLevelMapping(from, diagnosticsClassName, to);
    }
    return 2;
  }

  /**
   * This method must match the lookup in
   * {@link com.android.tools.r8.JdkClassFileProvider#fromJdkHome}.
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

  static void addLibraryArgument(BaseCommand.Builder builder, Origin origin, String arg) {
    Path path = Paths.get(arg);
    if (isJdkHome(path)) {
      try {
        builder
            .addLibraryResourceProvider(JdkClassFileProvider.fromJdkHome(path));
      } catch (IOException e) {
        builder.error(new ExceptionDiagnostic(e, origin));
      }
    } else {
      builder.addLibraryFiles(path);
    }
  }
}
