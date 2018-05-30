// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexsplitter;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DexSplitterHelper;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FeatureClassMapping;
import com.android.tools.r8.utils.FeatureClassMapping.FeatureMappingException;
import com.android.tools.r8.utils.OptionsParsing;
import com.android.tools.r8.utils.OptionsParsing.ParseContext;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Keep
public final class DexSplitter {

  private static final String DEFAULT_OUTPUT_DIR = "output";
  private static final String DEFAULT_BASE_NAME = "base";

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

  @Keep
  public static final class Options {
    private final DiagnosticsHandler diagnosticsHandler = new DefaultDiagnosticsHandler();
    private List<String> inputArchives = new ArrayList<>();
    private List<FeatureJar> featureJars = new ArrayList<>();
    private String baseOutputName = DEFAULT_BASE_NAME;
    private String output = DEFAULT_OUTPUT_DIR;
    private String featureSplitMapping;
    private String proguardMap;

    public DiagnosticsHandler getDiagnosticsHandler() {
      return diagnosticsHandler;
    }

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

    public String getBaseOutputName() {
      return baseOutputName;
    }

    public void setBaseOutputName(String baseOutputName) {
      this.baseOutputName = baseOutputName;
    }

    public void addInputArchive(String inputArchive) {
      inputArchives.add(inputArchive);
    }

    private void addFeatureJar(FeatureJar featureJar) {
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

    ImmutableList<FeatureJar> getFeatureJars() {
      return ImmutableList.copyOf(featureJars);
    }

    // Shorthand error messages.
    public void error(String msg) {
      diagnosticsHandler.error(new StringDiagnostic(msg));
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

  private static Options parseArguments(String[] args) {
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
      String baseOutputName = OptionsParsing.tryParseSingle(context, "--base-output-name", null);
      if (baseOutputName != null) {
        options.setBaseOutputName(baseOutputName);
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
      throws FeatureMappingException {
    if (options.getFeatureSplitMapping() != null) {
      return FeatureClassMapping.fromSpecification(
          Paths.get(options.getFeatureSplitMapping()), options.getDiagnosticsHandler());
    }
    assert !options.getFeatureJars().isEmpty();
    return FeatureClassMapping.Internal.fromJarFiles(
        options.getFeatureJars(), options.getBaseOutputName(), options.getDiagnosticsHandler());
  }

  private static void run(String[] args)
      throws CompilationFailedException, FeatureMappingException {
    Options options = parseArguments(args);
    run(options);
  }

  public static void run(Options options)
      throws FeatureMappingException, CompilationFailedException {
    boolean errors = false;
    if (options.getInputArchives().isEmpty()) {
      errors = true;
      options.error("Need at least one --input");
    }
    if (options.getFeatureSplitMapping() == null && options.getFeatureJars().isEmpty()) {
      errors = true;
      options.error("You must supply a feature split mapping or feature jars");
    }
    if (options.getFeatureSplitMapping() != null && !options.getFeatureJars().isEmpty()) {
      errors = true;
      options.error("You can't supply both a feature split mapping and feature jars");
    }
    if (errors) {
      throw new AbortException();
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
    if (PRINT_ARGS) {
      printArgs(args);
    }
    ExceptionUtils.withMainProgramHandler(
        () -> {
          try {
            run(args);
          } catch (FeatureMappingException e) {
            // TODO(ricow): Report feature mapping errors via the reporter.
            System.err.println("Splitting failed: " + e.getMessage());
            System.exit(1);
          }
        });
  }

  private static void printArgs(String[] args) {
    System.err.printf("r8.DexSplitter");
    for (String s : args) {
      System.err.printf(" %s", s);
    }
    System.err.println("");
  }
}
