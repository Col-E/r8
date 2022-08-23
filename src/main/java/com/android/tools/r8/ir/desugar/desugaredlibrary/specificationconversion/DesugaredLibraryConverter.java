// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.isHumanSpecification;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.isMachineSpecification;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.StringResource.FileResource;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MultiAPILevelMachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class DesugaredLibraryConverter {

  public static void main(String[] args) throws IOException {
    Path jsonFile = Paths.get(args[0]);
    Path desugaredLibraryJar = Paths.get(args[1]);
    Path customConversionsJar = Paths.get(args[2]);
    Path androidJar = Paths.get(args[3]);
    Path output = Paths.get(args[4]);
    convertMultiLevelAnythingToMachineSpecification(
        jsonFile,
        ImmutableSet.of(desugaredLibraryJar, customConversionsJar),
        ImmutableSet.of(androidJar),
        output);
  }

  public static void convertMultiLevelAnythingToMachineSpecification(
      Path jsonSpec, Set<Path> desugaredLibraryFiles, Set<Path> libraryFiles, Path output)
      throws IOException {

    InternalOptions options = new InternalOptions();

    FileResource jsonResource = StringResource.fromFile(jsonSpec);
    JsonObject jsonConfig = parseJsonConfig(options, jsonResource);

    if (isMachineSpecification(jsonConfig, options.reporter, jsonResource.getOrigin())) {
      // Nothing to convert;
      Files.copy(jsonSpec, output);
      return;
    }

    DexApplication appForConversion =
        getAppForConversion(options, libraryFiles, desugaredLibraryFiles);
    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        getInputAsHumanSpecification(options, jsonResource, jsonConfig, appForConversion);
    String outputString = convertToMachineSpecification(options, appForConversion, humanSpec);

    Files.write(output, Collections.singleton(outputString));
  }

  private static String convertToMachineSpecification(
      InternalOptions options,
      DexApplication appForConversion,
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec)
      throws IOException {
    HumanToMachineSpecificationConverter converter =
        new HumanToMachineSpecificationConverter(Timing.empty());
    MultiAPILevelMachineDesugaredLibrarySpecification machineSpec =
        converter.convertAllAPILevels(humanSpec, appForConversion);
    Box<String> machineJson = new Box<>();
    MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter.export(
        machineSpec, (string, handler) -> machineJson.set(string), options.dexItemFactory());
    return machineJson.get();
  }

  private static JsonObject parseJsonConfig(InternalOptions options, FileResource jsonResource) {
    JsonObject jsonConfig;
    try {
      String jsonConfigString = jsonResource.getString();
      JsonParser parser = new JsonParser();
      jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();
    } catch (Exception e) {
      throw options.reporter.fatalError(new ExceptionDiagnostic(e, jsonResource.getOrigin()));
    }
    return jsonConfig;
  }

  /**
   * Parse the human specification, or parse and convert the legacy specification into human
   * specification.
   */
  private static MultiAPILevelHumanDesugaredLibrarySpecification getInputAsHumanSpecification(
      InternalOptions options,
      FileResource jsonResource,
      JsonObject jsonConfig,
      DexApplication appForConversion)
      throws IOException {
    if (!isHumanSpecification(jsonConfig, options.reporter, jsonResource.getOrigin())) {
      MultiAPILevelLegacyDesugaredLibrarySpecification legacySpec =
          new MultiAPILevelLegacyDesugaredLibrarySpecificationParser(
                  options.dexItemFactory(), options.reporter)
              .parseMultiLevelConfiguration(jsonResource);

      LegacyToHumanSpecificationConverter converter =
          new LegacyToHumanSpecificationConverter(Timing.empty());

      return converter.convertAllAPILevels(legacySpec, appForConversion);
    }
    return new MultiAPILevelHumanDesugaredLibrarySpecificationParser(
            options.dexItemFactory(), options.reporter)
        .parseMultiLevelConfiguration(jsonResource);
  }

  public static DexApplication getAppForConversion(
      InternalOptions options, Set<Path> androidJar, Set<Path> desugaredlibJar) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    builder.addProgramFiles(desugaredlibJar);
    AndroidApp inputApp = builder.addLibraryFiles(androidJar).build();
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return app;
  }
}
