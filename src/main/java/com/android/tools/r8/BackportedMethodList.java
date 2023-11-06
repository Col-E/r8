// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.DesugaredMethodsList;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.DesugaredMethodsListCommand;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.StringUtils;

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
@KeepForApi
public class BackportedMethodList {

  static final String USAGE_MESSAGE =
      StringUtils.joinLines(
          "Usage: BackportedMethodList [options]",
          " Options are:",
          "  --output <file>          # Output result in <file>.",
          "  --min-api <number>       # Minimum Android API level for the application",
          "  --desugared-lib <file>   # Desugared library configuration (JSON from the",
          "                           # configuration)",
          "  --lib <file>             # The compilation SDK library (android.jar)",
          "  --android-platform-build # Compilation of platform code",
          "  --version                # Print the version of BackportedMethodList.",
          "  --help                   # Print this message.");

  public static void run(BackportedMethodListCommand command) throws CompilationFailedException {
    if (command.isPrintHelp()) {
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("BackportedMethodList " + Version.getVersionString());
      return;
    }
    DesugaredMethodsList.run(convert(command));
  }

  private static DesugaredMethodsListCommand convert(BackportedMethodListCommand command) {
    DesugaredMethodsListCommand.Builder builder =
        DesugaredMethodsListCommand.builder(command.getReporter());
    for (ClassFileResourceProvider libraryResourceProvider :
        command.getInputApp().getLibraryResourceProviders()) {
      builder.addLibrary(libraryResourceProvider);
    }
    String jsonSource = command.getDesugaredLibraryConfiguration().getJsonSource();
    if (jsonSource != null) {
      builder.setDesugarLibrarySpecification(
          StringResource.fromString(jsonSource, Origin.unknown()));
    }
    if (command.isAndroidPlatformBuild()) {
      builder.setAndroidPlatformBuild();
    }
    return builder
        .setMinApi(command.getMinApiLevel())
        .setOutputConsumer(command.getBackportedMethodListConsumer())
        .build();
  }

  public static void run(String[] args) throws CompilationFailedException {
    run(BackportedMethodListCommand.parse(args).build());
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}
