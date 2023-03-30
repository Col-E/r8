// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;

public class BaseCompilerCommandParser<
    C extends BaseCompilerCommand, B extends BaseCompilerCommand.Builder<C, B>> {

  protected static final String ART_PROFILE_FLAG = "--art-profile";
  protected static final String MIN_API_FLAG = "--min-api";
  protected static final String STARTUP_PROFILE_FLAG = "--startup-profile";
  protected static final String THREAD_COUNT_FLAG = "--thread-count";
  protected static final String MAP_DIAGNOSTICS = "--map-diagnostics";
  protected static final String DUMP_INPUT_TO_FILE = "--dumpinputtofile";
  protected static final String DUMP_INPUT_TO_DIRECTORY = "--dumpinputtodirectory";

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

  private enum AssertionTransformationType {
    ENABLE,
    DISABLE,
    PASSTHROUGH,
    HANDLER
  }

  private AssertionsConfiguration.Builder prepareBuilderForScope(
      AssertionsConfiguration.Builder builder,
      AssertionTransformationType transformation,
      MethodReference assertionHandler) {
    switch (transformation) {
      case ENABLE:
        return builder.setCompileTimeEnable();
      case DISABLE:
        return builder.setCompileTimeDisable();
      case PASSTHROUGH:
        return builder.setPassthrough();
      case HANDLER:
        return builder.setAssertionHandler(assertionHandler);
      default:
        throw new Unreachable();
    }
  }

  private void addAssertionTransformation(
      B builder,
      AssertionTransformationType transformation,
      MethodReference assertionHandler,
      String scope) {
    if (scope == null) {
      builder.addAssertionsConfiguration(
          b -> prepareBuilderForScope(b, transformation, assertionHandler).setScopeAll().build());
    } else {
      assert scope.length() > 0;
      if (scope.endsWith(PACKAGE_ASSERTION_POSTFIX)) {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopePackage(
                        scope.substring(0, scope.length() - PACKAGE_ASSERTION_POSTFIX.length()))
                    .build());
      } else {
        builder.addAssertionsConfiguration(
            b ->
                prepareBuilderForScope(b, transformation, assertionHandler)
                    .setScopeClass(scope)
                    .build());
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
    String FORCE_ASSERTIONS_HANDLER = "--force-assertions-handler";
    String FORCE_AH = "--force-ah";

    AssertionTransformationType transformation = null;
    MethodReference assertionsHandler = null;
    String remaining = null;
    if (arg.startsWith(FORCE_ENABLE_ASSERTIONS)) {
      transformation = AssertionTransformationType.ENABLE;
      remaining = arg.substring(FORCE_ENABLE_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_EA)) {
      transformation = AssertionTransformationType.ENABLE;
      remaining = arg.substring(FORCE_EA.length());
    } else if (arg.startsWith(FORCE_DISABLE_ASSERTIONS)) {
      transformation = AssertionTransformationType.DISABLE;
      remaining = arg.substring(FORCE_DISABLE_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_DA)) {
      transformation = AssertionTransformationType.DISABLE;
      remaining = arg.substring(FORCE_DA.length());
    } else if (arg.startsWith(FORCE_PASSTHROUGH_ASSERTIONS)) {
      transformation = AssertionTransformationType.PASSTHROUGH;
      remaining = arg.substring(FORCE_PASSTHROUGH_ASSERTIONS.length());
    } else if (arg.startsWith(FORCE_PA)) {
      transformation = AssertionTransformationType.PASSTHROUGH;
      remaining = arg.substring(FORCE_PA.length());
    } else if (arg.startsWith(FORCE_ASSERTIONS_HANDLER)) {
      transformation = AssertionTransformationType.HANDLER;
      remaining = arg.substring(FORCE_ASSERTIONS_HANDLER.length());
    } else if (arg.startsWith(FORCE_AH)) {
      transformation = AssertionTransformationType.HANDLER;
      remaining = arg.substring(FORCE_AH.length());
    }
    if (transformation == AssertionTransformationType.HANDLER) {
      if (remaining.length() == 0 || (remaining.length() == 1 && remaining.charAt(0) == ':')) {
        throw builder.fatalError(
            new StringDiagnostic("Missing required argument <handler method>", origin));
      }
      if (remaining.charAt(0) != ':') {
        return false;
      }
      remaining = remaining.substring(1);
      int index = remaining.indexOf(':');
      if (index == 0) {
        throw builder.fatalError(
            new StringDiagnostic("Missing required argument <handler method>", origin));
      }
      String assertionsHandlerString = index > 0 ? remaining.substring(0, index) : remaining;
      int lastDotIndex = assertionsHandlerString.lastIndexOf('.');
      if (assertionsHandlerString.length() < 3
          || lastDotIndex <= 0
          || lastDotIndex == assertionsHandlerString.length() - 1
          || !DescriptorUtils.isValidJavaType(assertionsHandlerString.substring(0, lastDotIndex))) {
        throw builder.fatalError(
            new StringDiagnostic(
                "Invalid argument <handler method>: " + assertionsHandlerString, origin));
      }
      assertionsHandler =
          Reference.methodFromDescriptor(
              DescriptorUtils.javaTypeToDescriptor(
                  assertionsHandlerString.substring(0, lastDotIndex)),
              assertionsHandlerString.substring(lastDotIndex + 1),
              "(Ljava/lang/Throwable;)V");
      remaining = remaining.substring(assertionsHandlerString.length());
    }
    if (transformation != null) {
      if (remaining.length() == 0) {
        addAssertionTransformation(builder, transformation, assertionsHandler, null);
        return true;
      } else {
        if (remaining.length() == 1 && remaining.charAt(0) == ':') {
          throw builder.fatalError(new StringDiagnostic("Missing optional argument", origin));
        }
        if (remaining.charAt(0) != ':') {
          return false;
        }
        String classOrPackageScope = remaining.substring(1);
        if (classOrPackageScope.contains(";")
            || classOrPackageScope.contains("[")
            || classOrPackageScope.contains("/")) {
          builder.error(
              new StringDiagnostic("Illegal assertion scope: " + classOrPackageScope, origin));
        }
        addAssertionTransformation(
            builder, transformation, assertionsHandler, remaining.substring(1));
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
    if (arg.equals("none")) {
      return DiagnosticsLevel.NONE;
    }
    errorHandler.accept(
        new StringDiagnostic(
            "Invalid diagnostics level '"
                + arg
                + "'. Valid levels are 'error', 'warning', 'info' and 'none'.",
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

  int tryParseDump(B builder, String arg, String[] args, int argsIndex, Origin origin) {
    if (!arg.equals(DUMP_INPUT_TO_FILE) && !arg.equals(DUMP_INPUT_TO_DIRECTORY)) {
      return -1;
    }
    if (args.length <= argsIndex + 1) {
      builder.error(new StringDiagnostic("Missing argument(s) for " + arg + ".", origin));
      return args.length - argsIndex;
    }
    if (arg.equals(DUMP_INPUT_TO_FILE)) {
      builder.dumpInputToFile(Paths.get(args[argsIndex + 1]));
    } else {
      assert arg.equals(DUMP_INPUT_TO_DIRECTORY);
      builder.dumpInputToDirectory(Paths.get(args[argsIndex + 1]));
    }
    return 1;
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
