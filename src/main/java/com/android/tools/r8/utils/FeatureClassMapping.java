// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.io.IOException;
import java.nio.file.Path;
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
 * <p>We will do most specific matching, i.e., com.google.foobar.*:feature2 com.google.*:base will
 * put everything in the com.google namespace into base, except classes in com.google.foobar that
 * will go to feature2. Class based mappings takes precedence over packages (since they are more
 * specific): com.google.A:feature2 com.google.*:base Puts A into feature2, and all other classes
 * from com.google into base.
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

  public FeatureClassMapping(Path file) throws IOException, FeatureMappingException {
    this(FileUtils.readAllLines(file));
    this.mappingFile = file;
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
      throw new FeatureMappingException("Class: " + clazz + " is not mapped to any feature");
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

    if (parsedRules.containsKey(values[0])) {
      if (!parsedRules.get(values[0]).equals(values[1])) {
        error("Redefinition of predicate not allowed", lineNumber);
      }
      return; // Already have this rule.
    }
    parsedRules.put(values[0], values[1]);
    FeaturePredicate featurePredicate = new FeaturePredicate(values[0], values[1]);
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

    final String predicate;
    final String feature;
    // False implies class predicate.
    final boolean isWildcard;

    FeaturePredicate(String predicate, String feature) throws FeatureMappingException {
      isWildcard = predicate.endsWith(".*");
      if (isWildcard) {
        this.predicate = predicate.substring(0, predicate.length() - 3);
      } else {
        this.predicate = predicate;
      }
      validateIdentifiers(this.predicate.split("\\."));
      this.feature = feature;
    }

    private void validateIdentifiers(String[] names) throws FeatureMappingException {
      // TODO(ricow): knap saa mange regexp.
      Pattern p = Pattern.compile("[A-Za-z_][A-Za-z0-9_$]*");
      for (String name : names) {
        if (!p.matcher(name).matches()) {
          throw new FeatureMappingException(name + " is not a valid identifier");
        }
      }
    }

    boolean match(String className) {
      if (isWildcard) {
        return className.startsWith(predicate);
      } else {
        // We also put inner classes into the same feature.
        return className.startsWith(predicate);
      }
    }
  }
}
