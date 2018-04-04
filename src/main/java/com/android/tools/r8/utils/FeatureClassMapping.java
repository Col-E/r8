// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.dexsplitter.DexSplitter.FeatureJar;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Provides a mappings of classes to modules. The structure of the input file is as follows:
 * packageOrClass:module
 *
 * <p>Lines with a # prefix are ignored.
 *
 * <p>We will do most specific matching, i.e.,
 * <pre>
 *   com.google.foobar.*:feature2
 *   com.google.*:base
 * </pre>
 * will put everything in the com.google namespace into base, except classes in com.google.foobar
 * that will go to feature2. Class based mappings takes precedence over packages (since they are
 * more specific):
 * <pre>
 *   com.google.A:feature2
 *   com.google.*:base
 *  </pre>
 * Puts A into feature2, and all other classes from com.google into base.
 *
 * <p>Note that this format does not allow specifying inter-module dependencies, this is simply a
 * placement tool.
 */
public class FeatureClassMapping {

  HashMap<String, String> parsedRules = new HashMap<>(); // Already parsed rules.

  HashSet<FeaturePredicate> mappings = new HashSet<>();

  Path mappingFile;

  static final String COMMENT = "#";
  static final String SEPARATOR = ":";
  static final String BASE_NAME = "base";

  public static FeatureClassMapping fromSpecification(Path file)
      throws FeatureMappingException, IOException {
    FeatureClassMapping mapping = new FeatureClassMapping();
    List<String> lines = FileUtils.readAllLines(file);
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      mapping.parseAndAdd(line, i);
    }
    return mapping;
  }

  public static FeatureClassMapping fromJarFiles(List<FeatureJar> featureJars)
      throws FeatureMappingException, IOException {
    FeatureClassMapping mapping = new FeatureClassMapping();
    for (FeatureJar featureJar : featureJars) {
      Path jarPath = Paths.get(featureJar.getJar());
      ArchiveClassFileProvider provider = new ArchiveClassFileProvider(jarPath);
      for (String javaDescriptor : provider.getClassDescriptors()) {
          String javaType = DescriptorUtils.descriptorToJavaType(javaDescriptor);
          mapping.addMapping(javaType, featureJar.getOutputName());
      }
    }
    return mapping;
  }

  private FeatureClassMapping() {}

  private void addMapping(String clazz, String feature) throws FeatureMappingException {
    addRule(clazz, feature, 0);
  }

  FeatureClassMapping(List<String> lines) throws FeatureMappingException {
    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      parseAndAdd(line, i);
    }
  }

  public String featureForClass(String clazz) throws FeatureMappingException {
    // Todo(ricow): improve performance (e.g., direct lookup of class predicates through hashmap).
    FeaturePredicate bestMatch = null;
    for (FeaturePredicate mapping : mappings) {
      if (mapping.match(clazz)) {
        if (bestMatch == null || bestMatch.predicate.length() < mapping.predicate.length()) {
          bestMatch = mapping;
        }
      }
    }
    if (bestMatch == null) {
      return BASE_NAME;
    }
    return bestMatch.feature;
  }

  private void parseAndAdd(String line, int lineNumber) throws FeatureMappingException {
    if (line.startsWith(COMMENT)) {
      return; // Ignore comments
    }
    if (line.isEmpty()) {
      return; // Ignore blank lines
    }

    if (!line.contains(SEPARATOR)) {
      error("Mapping lines must contain a " + SEPARATOR, lineNumber);
    }
    String[] values = line.split(SEPARATOR);
    if (values.length != 2) {
      error("Mapping lines can only contain one " + SEPARATOR, lineNumber);
    }

    String predicate = values[0];
    String feature = values[1];
    addRule(predicate, feature, lineNumber);
  }

  private void addRule(String predicate, String feature, int lineNumber)
      throws FeatureMappingException {
    if (parsedRules.containsKey(predicate)) {
      if (!parsedRules.get(predicate).equals(feature)) {
        error("Redefinition of predicate " + predicate + "not allowed", lineNumber);
      }
      return; // Already have this rule.
    }
    parsedRules.put(predicate, feature);
    FeaturePredicate featurePredicate = new FeaturePredicate(predicate, feature);
    mappings.add(featurePredicate);
  }

  private void error(String error, int line) throws FeatureMappingException {
    throw new FeatureMappingException(
        "Invalid mappings specification: " + error + "\n in file " + mappingFile + ":" + line);
  }

  public static class FeatureMappingException extends Exception {
    FeatureMappingException(String message) {
      super(message);
    }
  }

  /** A feature predicate can either be a wildcard or class predicate. */
  private static class FeaturePredicate {
    private static Pattern identifier = Pattern.compile("[A-Za-z_\\-][A-Za-z0-9_$\\-]*");
    final String predicate;
    final String feature;
    final boolean isCatchAll;
    // False implies class predicate.
    final boolean isWildcard;

    FeaturePredicate(String predicate, String feature) throws FeatureMappingException {
      isWildcard = predicate.endsWith(".*");
      isCatchAll =  predicate.equals("*");
      if (isCatchAll) {
        this.predicate = "";
      } else if (isWildcard) {
        this.predicate = predicate.substring(0, predicate.length() - 2);
      } else {
        this.predicate = predicate;
      }
      if (!DescriptorUtils.isValidJavaType(this.predicate) && !isCatchAll) {
        throw new FeatureMappingException(this.predicate + " is not a valid identifier");
      }
      this.feature = feature;
    }

    boolean match(String className) {
      if (isCatchAll) {
        return true;
      } else if (isWildcard) {
        return className.startsWith(predicate);
      } else {
        // We also put inner classes into the same feature.
        return className.startsWith(predicate);
      }
    }
  }
}
