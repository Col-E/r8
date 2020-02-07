// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.concurrent.ExecutorService;

/**
 * Tool to extract the list of methods which is backported by the D8 and R8 compilers.
 *
 * <p>The D8 and R8 compilers will backport some simple Java APIs which are not present on all API
 * levels. One example of this is the static method <code>int Integer.divideUnsigned(int a, int b)
 * </code> which was added from API level 26.
 *
 * <p>As these backported methods is supported on all API levels, tools like linters and code
 * checkers need this information to avoid false negatives when analyzing code.
 *
 * <p>This tool will generate a list of all the backported methods for the associated version of D8
 * and R8.
 *
 * <p>This tool will <strong>not</strong> provide information about the APIs supported when using
 * library desugaring. That information is provided in the dependencies used for library desugaring.
 * However, in place of library desugaring backporting will be able to backport additional methods.
 * If library desugaring is used, then passing information about that to this tool will provide the
 * more precise list. See b/149078312.
 *
 * <p>The tool is invoked by calling {@link #run(BackportedMethodListCommand)
 * BackportedMethodList.run} with an appropriate {@link BackportedMethodListCommand}.
 *
 * <p>For example:
 *
 * <pre>
 *   BackportedMethodList.run(BackportedMethodListCommand.builder()
 *       .setMinApiLevel(apiLevel)
 *       .setOutputPath(Paths.get("methods-list.txt"))
 *       .build());
 * </pre>
 *
 * The above generates the list of backported methods for a compilation with a min API of <code>
 * apiLevel</code> into the file <code>methods-list.txt</code>.
 */
@Keep
public class BackportedMethodList {

  static final String USAGE_MESSAGE =
      StringUtils.joinLines(
          "Usage: BackportedMethodList [options]",
          " Options are:",
          "  --output <file>         # Output result in <file>.",
          "  --min-api <number>      # Minimum Android API level for the application",
          "  --desugared-lib <file>  # Desugared library configuration (JSON from the",
          "                          # configuration)",
          "  --lib <file>            # The compilation SDK library (android.jar)",
          "  --version               # Print the version of BackportedMethodList.",
          "  --help                  # Print this message.");

  private static String formatMethod(DexMethod method) {
    return DescriptorUtils.getClassBinaryNameFromDescriptor(method.holder.descriptor.toString())
        + '#'
        + method.name
        + method.proto.toDescriptorString();
  }

  public static void run(BackportedMethodListCommand command) throws CompilationFailedException {
    if (command.isPrintHelp()) {
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("BackportedMethodList " + Version.getVersionString());
      return;
    }
    InternalOptions options = command.getInternalOptions();
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    try {
      ExceptionUtils.withD8CompilationHandler(
          command.getReporter(),
          () -> {
            BackportedMethodRewriter.generateListOfBackportedMethods(
                    command.getInputApp(), options, executorService)
                .stream()
                .map(BackportedMethodList::formatMethod)
                .sorted()
                .forEach(
                    formattedMethod ->
                        command
                            .getBackportedMethodListConsumer()
                            .accept(formattedMethod, command.getReporter()));
            command.getBackportedMethodListConsumer().finished(command.getReporter());
          });
    } finally {
      executorService.shutdown();
    }
  }

  public static void run(String[] args) throws CompilationFailedException {
    run(BackportedMethodListCommand.parse(args).build());
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
