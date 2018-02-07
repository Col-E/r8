// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import com.android.tools.r8.CompilationException;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DexSplitterHelper;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.utils.FeatureClassMapping;
import com.android.tools.r8.utils.FeatureClassMapping.FeatureMappingException;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DexSplitter {

  private static final String DEFAULT_OUTPUT_ARCHIVE_FILENAME = "split";

  private static final boolean PRINT_ARGS = false;

  private static class Options {
    List<String> inputArchives = new ArrayList<>();
    List<String> featureJars = new ArrayList<>();
    String splitBaseName = DEFAULT_OUTPUT_ARCHIVE_FILENAME;
    String featureSplitMapping;
    String proguardMap;
  }

  private static Options parseArguments(String[] args) throws IOException {
    Options options = new Options();
    ParseContext context = new ParseContext(args);
    while (context.head() != null) {
      List<String> input = OptionsParsing.tryParseMulti(context, "--input");
      if (input != null) {
        options.inputArchives.addAll(input);
        continue;
      }
      List<String> featureJars = OptionsParsing.tryParseMulti(context, "--feature-jar");
      if (featureJars != null) {
        options.featureJars.addAll(featureJars);
        continue;
      }
      String output = OptionsParsing.tryParseSingle(context, "--output", "-o");
      if (output != null) {
        options.splitBaseName = output;
        continue;
      }
      String proguardMap = OptionsParsing.tryParseSingle(context, "--proguard-map", null);
      if (proguardMap != null) {
        options.proguardMap = proguardMap;
        continue;
      }
      String featureSplit = OptionsParsing.tryParseSingle(context, "--feature-splits", null);
      if (featureSplit != null) {
        options.featureSplitMapping = featureSplit;
        continue;
      }
      throw new RuntimeException(String.format("Unknown options: '%s'.", context.head()));
    }
    return options;
  }

  private static FeatureClassMapping createFeatureClassMapping(Options options)
      throws IOException, FeatureMappingException, ResourceException {
    if (options.featureSplitMapping != null) {
      return FeatureClassMapping.fromSpecification(Paths.get(options.featureSplitMapping));
    }
    assert !options.featureJars.isEmpty();
    return FeatureClassMapping.fromJarFiles(options.featureJars);
  }

  public static void run(String[] args)
      throws CompilationFailedException, IOException, CompilationException, ExecutionException,
          ResourceException, FeatureMappingException {
    Options options = parseArguments(args);
    if (options.inputArchives.isEmpty()) {
      throw new RuntimeException("Need at least one --input");
    }
    if (options.featureSplitMapping == null && options.featureJars.isEmpty()) {
      throw new RuntimeException("You must supply a feature split mapping or feature jars");
    }
    if (options.featureSplitMapping != null && !options.featureJars.isEmpty()) {
      throw new RuntimeException("You can't supply both a feature split mapping and feature jars");
    }

    D8Command.Builder builder = D8Command.builder();
    for (String s : options.inputArchives) {
      builder.addProgramFiles(Paths.get(s));
    }
    // We set the actual consumer on the ApplicationWriter when we have calculated the distribution
    // since we don't yet know the distribution.
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());

    FeatureClassMapping featureClassMapping = createFeatureClassMapping(options);

    DexSplitterHelper.run(
        builder.build(), featureClassMapping, options.splitBaseName, options.proguardMap);
  }

  public static void main(String[] args) {
    try {
      if (PRINT_ARGS) {
        printArgs(args);
      }
      run(args);
    } catch (CompilationFailedException
        | IOException
        | CompilationException
        | ExecutionException
        | ResourceException
        | FeatureMappingException e) {
      System.err.println("Splitting failed: " + e.getMessage());
      System.exit(1);
    }
  }

  private static void printArgs(String[] args) {
    System.err.printf("r8.DexSplitter");
    for (String s : args) {
      System.err.printf(" %s", s);
    }
    System.err.println("");
  }
}
