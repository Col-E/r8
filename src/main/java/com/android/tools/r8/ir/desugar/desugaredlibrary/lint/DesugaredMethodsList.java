// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;

@Keep
public class DesugaredMethodsList extends GenerateDesugaredLibraryLintFiles {

  private final AndroidApiLevel minApi;
  private final boolean androidPlatformBuild;
  private final StringConsumer outputConsumer;

  DesugaredMethodsList(
      int minApi,
      boolean androidPlatformBuild,
      Reporter reporter,
      StringResource desugarConfiguration,
      Collection<ProgramResourceProvider> desugarImplementation,
      StringConsumer outputConsumer,
      Collection<ClassFileResourceProvider> androidJar) {
    super(reporter, desugarConfiguration, desugarImplementation, null, androidJar);
    this.minApi = AndroidApiLevel.getAndroidApiLevel(minApi);
    this.androidPlatformBuild = androidPlatformBuild;
    this.outputConsumer = outputConsumer;
  }

  public static void run(DesugaredMethodsListCommand command) throws CompilationFailedException {
    if (command.isHelp()) {
      System.out.println(DesugaredMethodsListCommand.getUsageMessage());
      return;
    }
    if (command.isVersion()) {
      System.out.println("DesugaredMethodsList " + Version.getVersionString());
      return;
    }
    ExecutorService executorService = ThreadUtils.getExecutorService(ThreadUtils.NOT_SPECIFIED);
    try {
      ExceptionUtils.withD8CompilationHandler(
          command.getReporter(),
          () ->
              new DesugaredMethodsList(
                      command.getMinApi(),
                      command.isAndroidPlatformBuild(),
                      command.getReporter(),
                      command.getDesugarLibrarySpecification(),
                      command.getDesugarLibraryImplementation(),
                      command.getOutputConsumer(),
                      command.getLibrary())
                  .run());
    } finally {
      executorService.shutdown();
    }
  }

  @Override
  public AndroidApiLevel run() throws IOException {
    AndroidApiLevel compilationLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    SupportedClasses supportedMethods =
        new SupportedClassesGenerator(options, androidJar, minApi, androidPlatformBuild, true)
            .run(desugaredLibraryImplementation, desugaredLibrarySpecificationResource);
    System.out.println(
        "Generating lint files for "
            + getDebugIdentifier()
            + " (compile API "
            + compilationLevel
            + ")");
    writeLintFiles(compilationLevel, minApi, supportedMethods);
    return compilationLevel;
  }

  @Override
  void writeOutput(
      AndroidApiLevel compilationApiLevel,
      AndroidApiLevel minApiLevel,
      List<String> desugaredApisSignatures) {
    for (String desugaredApisSignature : desugaredApisSignatures) {
      outputConsumer.accept(desugaredApisSignature, options.reporter);
    }
    outputConsumer.finished(options.reporter);
  }

  public static void run(String[] args) throws CompilationFailedException, IOException {
    run(DesugaredMethodsListCommand.parse(args));
  }

  public static void main(String[] args) {
    ExceptionUtils.withMainProgramHandler(
        () -> {
          try {
            run(args);
          } catch (IOException e) {
            throw new CompilationError(e.getMessage(), e);
          }
        });
  }
}
