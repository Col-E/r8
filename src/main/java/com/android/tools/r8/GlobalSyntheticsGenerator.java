// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutorService;

/**
 * The GlobalSyntheticsGenerator, a tool for generating a dex file for all possible global
 * synthetics.
 */
public class GlobalSyntheticsGenerator {

  /**
   * Main API entry for the global synthetics generator.
   *
   * @param command GlobalSyntheticsGenerator command.
   */
  public static void run(GlobalSyntheticsGeneratorCommand command)
      throws CompilationFailedException {
    runForTesting(command.getInputApp(), command.getInternalOptions());
  }

  /**
   * Main API entry for the global synthetics generator.
   *
   * @param command GlobalSyntheticsGenerator command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(GlobalSyntheticsGeneratorCommand command, ExecutorService executor)
      throws CompilationFailedException {
    run(command.getInputApp(), command.getInternalOptions(), executor);
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    run(app, options, executorService);
  }

  private static void run(AndroidApp app, InternalOptions options, ExecutorService executorService)
      throws CompilationFailedException {
    try {
      ExceptionUtils.withD8CompilationHandler(
          options.reporter,
          () -> {
            // TODO(b/280016114): Implement
            throw new RuntimeException("Implement GlobalSyntheticsGenerator");
          });
    } finally {
      executorService.shutdown();
    }
  }

  private static void run(String[] args) throws CompilationFailedException {
    GlobalSyntheticsGeneratorCommand command =
        GlobalSyntheticsGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(GlobalSyntheticsGeneratorCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("GlobalSyntheticsGenerator " + Version.getVersionString());
      return;
    }
    run(command);
  }

  /**
   * Command-line entry to GlobalSynthetics.
   *
   * <p>See {@link GlobalSyntheticsGeneratorCommandParser#getUsageMessage()} or run {@code
   * globalsyntheticsgenerator --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(
          StringUtils.joinLines(
              "Invalid invocation.", GlobalSyntheticsGeneratorCommandParser.getUsageMessage()));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
