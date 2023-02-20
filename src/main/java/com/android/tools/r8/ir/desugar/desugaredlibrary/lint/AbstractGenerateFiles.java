// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.concurrent.ExecutorService;

public abstract class AbstractGenerateFiles {

  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";

  // If we increment this api level, we need to verify everything works correctly.
  static final AndroidApiLevel MAX_TESTED_ANDROID_API_LEVEL = AndroidApiLevel.U;

  private final DexItemFactory factory = new DexItemFactory();
  private final Reporter reporter = new Reporter();
  final InternalOptions options = new InternalOptions(factory, reporter);

  final MachineDesugaredLibrarySpecification desugaredLibrarySpecification;
  final Path desugaredLibrarySpecificationPath;
  final Collection<Path> desugaredLibraryImplementation;
  final Path outputDirectory;

  public AbstractGenerateFiles(
      String desugarConfigurationPath, String desugarImplementationPath, String outputDirectory)
      throws Exception {
    this(
        Paths.get(desugarConfigurationPath),
        ImmutableList.of(Paths.get(desugarImplementationPath)),
        Paths.get(outputDirectory));
  }

  AbstractGenerateFiles(
      Path desugarConfigurationPath,
      Collection<Path> desugarImplementationPath,
      Path outputDirectory)
      throws Exception {
    this.desugaredLibrarySpecificationPath = desugarConfigurationPath;
    DesugaredLibrarySpecification specification =
        readDesugaredLibraryConfiguration(desugarConfigurationPath);
    Path androidJarPath = getAndroidJarPath(specification.getRequiredCompilationApiLevel());
    DexApplication app = createApp(androidJarPath, options);
    this.desugaredLibrarySpecification = specification.toMachineSpecification(app, Timing.empty());
    this.desugaredLibraryImplementation = desugarImplementationPath;
    this.outputDirectory = outputDirectory;
    if (!Files.isDirectory(this.outputDirectory)) {
      throw new Exception("Output directory " + outputDirectory + " is not a directory");
    }
  }

  static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    String jar =
        apiLevel == AndroidApiLevel.MASTER
            ? "third_party/android_jar/lib-master/android.jar"
            : String.format(ANDROID_JAR_PATTERN, apiLevel.getLevel());
    return Paths.get(jar);
  }


  private DesugaredLibrarySpecification readDesugaredLibraryConfiguration(
      Path desugarConfigurationPath) {
    return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
        StringResource.fromFile(desugarConfigurationPath),
        factory,
        reporter,
        false,
        AndroidApiLevel.B.getLevel());
  }

  private static DexApplication createApp(Path androidLib, InternalOptions options)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    AndroidApp inputApp = builder.addLibraryFiles(androidLib).build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return app;
  }

  abstract AndroidApiLevel run() throws Exception;

  public static void main(String[] args) throws Exception {
    if (args.length == 3) {
      new GenerateLintFiles(args[0], args[1], args[2]).run();
      return;
    }
    if (args.length == 4 && args[0].equals("--generate-api-docs")) {
      new GenerateHtmlDoc(args[1], args[2], args[3]).run();
      return;
    }
    throw new RuntimeException(
        StringUtils.joinLines(
            "Invalid invocation.",
            "Usage: GenerateLineFiles [--generate-api-docs] "
                + "<desugar configuration> <desugar implementation> <output directory>"));
  }
}
