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
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class DexSplitter {

  private static final String DEFAULT_OUTPUT_DIR = "output";

  private static final boolean PRINT_ARGS = false;

  public static class FeatureJar {
    private String jar;
    private String outputName;

    public FeatureJar(String jar, String outputName) {
      this.jar = jar;
      this.outputName = outputName;
    }

    public FeatureJar(String jar) {
      this(jar, featureNameFromJar(jar));
    }

    public String getJar() {
      return jar;
    }

    public String getOutputName() {
      return outputName;
    }

    private static String featureNameFromJar(String jar) {
      Path jarPath = Paths.get(jar);
      String featureName = jarPath.getFileName().toString();
      if (featureName.endsWith(".jar") || featureName.endsWith(".zip")) {
        featureName = featureName.substring(0, featureName.length() - 4);
      }
      return featureName;
    }

  }

  public static class Options {
    private List<String> inputArchives = new ArrayList<>();
    private List<FeatureJar> featureJars = new ArrayList<>();
    private String output = DEFAULT_OUTPUT_DIR;
    private String featureSplitMapping;
    private String proguardMap;

    public String getOutput() {
      return output;
    }

    public void setOutput(String output) {
      this.output = output;
    }

    public String getFeatureSplitMapping() {
      return featureSplitMapping;
    }

    public void setFeatureSplitMapping(String featureSplitMapping) {
      this.featureSplitMapping = featureSplitMapping;
    }

    public String getProguardMap() {
      return proguardMap;
    }

    public void setProguardMap(String proguardMap) {
      this.proguardMap = proguardMap;
    }

    public void addInputArchive(String inputArchive) {
      inputArchives.add(inputArchive);
    }

    protected void addFeatureJar(FeatureJar featureJar) {
      featureJars.add(featureJar);
    }

    public void addFeatureJar(String jar) {
      featureJars.add(new FeatureJar(jar));
    }

    public void addFeatureJar(String jar, String outputName) {
      featureJars.add(new FeatureJar(jar, outputName));
    }

    public ImmutableList<String> getInputArchives() {
      return ImmutableList.copyOf(inputArchives);
    }

    public ImmutableList<FeatureJar> getFeatureJars() {
      return ImmutableList.copyOf(featureJars);
    }
  }

  /**
   * Parse a feature jar argument and return the corresponding FeatureJar representation.
   * Default to use the name of the jar file if the argument contains no ':', if the argument
   * contains ':', then use the value after the ':' as the name.
   * @param argument
   * @return
   */
  private static FeatureJar parseFeatureJarArgument(String argument) {
    if (argument.contains(":")) {
      String[] parts = argument.split(":");
      if (parts.length > 2) {
        throw new RuntimeException("--feature-jar argument contains more than one :");
      }
      return new FeatureJar(parts[0], parts[1]);
    }
    return new FeatureJar(argument);
  }

  private static Options parseArguments(String[] args) throws IOException {
    Options options = new Options();
    ParseContext context = new ParseContext(args);
    while (context.head() != null) {
      List<String> inputs = OptionsParsing.tryParseMulti(context, "--input");
      if (inputs != null) {
        inputs.stream().forEach(options::addInputArchive);
        continue;
      }
      List<String> featureJars = OptionsParsing.tryParseMulti(context, "--feature-jar");
      if (featureJars != null) {
        featureJars.stream().forEach(
            (feature) -> options.addFeatureJar(parseFeatureJarArgument(feature)));
        continue;
      }
      String output = OptionsParsing.tryParseSingle(context, "--output", "-o");
      if (output != null) {
        options.setOutput(output);
        continue;
      }
      String proguardMap = OptionsParsing.tryParseSingle(context, "--proguard-map", null);
      if (proguardMap != null) {
        options.setProguardMap(proguardMap);
        continue;
      }
      String featureSplit = OptionsParsing.tryParseSingle(context, "--feature-splits", null);
      if (featureSplit != null) {
        options.setFeatureSplitMapping(featureSplit);
        continue;
      }
      throw new RuntimeException(String.format("Unknown options: '%s'.", context.head()));
    }
    return options;
  }

  private static FeatureClassMapping createFeatureClassMapping(Options options)
      throws IOException, FeatureMappingException, ResourceException {
    if (options.getFeatureSplitMapping() != null) {
      return FeatureClassMapping.fromSpecification(Paths.get(options.getFeatureSplitMapping()));
    }
    assert !options.getFeatureJars().isEmpty();
    return FeatureClassMapping.fromJarFiles(options.getFeatureJars());
  }

  private static void run(String[] args)
      throws CompilationFailedException, IOException, CompilationException, ExecutionException,
          ResourceException, FeatureMappingException {
    Options options = parseArguments(args);
    run(options);
  }

  public static void run(Options options)
      throws IOException, FeatureMappingException, ResourceException, CompilationException,
      ExecutionException, CompilationFailedException {
    if (options.getInputArchives().isEmpty()) {
      throw new RuntimeException("Need at least one --input");
    }
    if (options.getFeatureSplitMapping() == null && options.getFeatureJars().isEmpty()) {
      throw new RuntimeException("You must supply a feature split mapping or feature jars");
    }
    if (options.getFeatureSplitMapping() != null && !options.getFeatureJars().isEmpty()) {
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
        builder.build(), featureClassMapping, options.getOutput(), options.getProguardMap());
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
