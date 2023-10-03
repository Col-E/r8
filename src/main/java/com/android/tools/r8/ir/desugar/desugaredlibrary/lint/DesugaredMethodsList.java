// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import static java.lang.Integer.parseInt;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ArchiveProgramResourceProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.Keep;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;

@Keep
public class DesugaredMethodsList extends GenerateDesugaredLibraryLintFiles {

  private final AndroidApiLevel minApi;

  private final StringConsumer outputConsumer;

  DesugaredMethodsList(
      int minApi,
      StringResource desugarConfiguration,
      Collection<ProgramResourceProvider> desugarImplementation,
      StringConsumer outputConsumer,
      Collection<ClassFileResourceProvider> androidJar) {
    super(desugarConfiguration, desugarImplementation, null, androidJar);
    this.minApi = AndroidApiLevel.getAndroidApiLevel(minApi);
    this.outputConsumer = outputConsumer;
  }

  public static void run(DesugaredMethodsListCommand command) throws IOException {
    if (command.isHelp()) {
      System.out.println(DesugaredMethodsListCommand.getUsageMessage());
      return;
    }
    if (command.isVersion()) {
      System.out.println("DesugaredMethodsList " + Version.getVersionString());
      return;
    }
    new DesugaredMethodsList(
            command.getMinApi(),
            command.getDesugarLibrarySpecification(),
            command.getDesugarLibraryImplementation(),
            command.getOutputConsumer(),
            command.getLibrary())
        .run();
  }

  @Override
  public AndroidApiLevel run() throws IOException {
    AndroidApiLevel compilationLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    SupportedClasses supportedMethods =
        new SupportedClassesGenerator(options, androidJar, minApi, true)
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

  private static StringResource getSpecificationArg(String arg) {
    return arg == null ? null : StringResource.fromFile(Paths.get(arg));
  }

  private static Collection<ProgramResourceProvider> getImplementationArg(String arg) {
    if (arg == null) {
      return ImmutableList.of();
    }
    return ImmutableList.of(ArchiveProgramResourceProvider.fromArchive(Paths.get(arg)));
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 4 || args.length == 5) {
      DesugaredMethodsListCommand.Builder builder =
          DesugaredMethodsListCommand.builder()
              .setMinApi(parseInt(args[0]))
              .setDesugarLibrarySpecification(getSpecificationArg(args[1]))
              .setOutputPath(Paths.get(args[3]));
      for (ProgramResourceProvider programResourceProvider : getImplementationArg(args[2])) {
        builder.addDesugarLibraryImplementation(programResourceProvider);
      }
      builder.addLibrary(new ArchiveClassFileProvider(Paths.get(args[4])));
      DesugaredMethodsList.run(builder.build());
      return;
    }
    throw new RuntimeException(
        StringUtils.joinLines(
            "Invalid invocation.",
            "Usage: DesugaredMethodList <min-api> <desugar configuration> "
                + "<desugar implementation> <output file> <android jar path for Android "
                + MAX_TESTED_ANDROID_API_LEVEL
                + " or higher>"));
  }
}
