// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import java.util.ArrayList;
import java.util.List;

@Keep
public class AssertionsConfiguration {

  /** The possible transformations of the javac generated assertion code during compilation. */
  @Keep
  public enum AssertionTransformation {
    /** Unconditionally enable the javac generated assertion code. */
    ENABLE,
    /**
     * Unconditionally disable the javac generated assertion code. This will most likely remove the
     * javac generated assertion code completely.
     */
    DISABLE,
    /** Passthrough of the javac generated assertion code. */
    PASSTHROUGH
  }

  public enum ConfigurationType {
    ALL,
    PACKAGE,
    CLASS
  }

  public static class ConfigurationEntry {
    private final AssertionTransformation transformation;
    private final ConfigurationType type;
    private final String value;

    private ConfigurationEntry(
        AssertionTransformation transformation, ConfigurationType type, String value) {
      assert value != null || type == ConfigurationType.ALL;
      this.transformation = transformation;
      this.type = type;
      this.value = value;
    }

    public AssertionTransformation getTransformation() {
      return transformation;
    }

    public ConfigurationType getType() {
      return type;
    }

    public String getValue() {
      return value;
    }
  }

  // Methods which need to be public.
  public static class InternalAssertionConfiguration {

    public static List<ConfigurationEntry> getConfiguration(AssertionsConfiguration configuration) {
      return configuration.entries;
    }
  }

  private final List<ConfigurationEntry> entries;

  private AssertionsConfiguration(List<ConfigurationEntry> entries) {
    this.entries = entries;
  }

  static AssertionsConfiguration.Builder builder(AssertionsConfiguration previous) {
    return new AssertionsConfiguration.Builder(
        previous != null ? previous.entries : new ArrayList<>());
  }

  /**
   * Builder for constructing a <code>{@link AssertionsConfiguration}</code>.
   *
   * <p>A builder is obtained by calling {@link
   * BaseCompilerCommand.Builder#addAssertionsConfiguration}.
   */
  @Keep
  public static class Builder {
    private final List<ConfigurationEntry> entries;

    private Builder(List<ConfigurationEntry> previousEntries) {
      assert previousEntries != null;
      this.entries = previousEntries;
    }

    private void addEntry(
        AssertionTransformation transformation, ConfigurationType type, String value) {
      entries.add(new ConfigurationEntry(transformation, type, value));
    }

    /** Set how to handle javac generated assertion code. */
    public AssertionsConfiguration.Builder setTransformation(
        AssertionTransformation transformation) {
      addEntry(transformation, ConfigurationType.ALL, null);
      return this;
    }

    AssertionsConfiguration.Builder setDefault(AssertionTransformation transformation) {
      // Add the default by inserting a transform all entry at the beginning of the list, if there
      // isn't already one.
      ConfigurationEntry defaultEntry =
          new ConfigurationEntry(transformation, ConfigurationType.ALL, null);
      if (entries.size() == 0 || entries.get(0).type != ConfigurationType.ALL) {
        entries.listIterator().add(defaultEntry);
      }
      return this;
    }

    /**
     * Unconditionally enable javac generated assertion code in all packages and classes. This
     * corresponds to passing <code>-enableassertions</code> or <code>-ea</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder enable() {
      setTransformation(AssertionTransformation.ENABLE);
      return this;
    }

    /**
     * Disable the javac generated assertion code in all packages and classes. This corresponds to
     * passing <code>-disableassertions</code> or <code>-da</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder disable() {
      setTransformation(AssertionTransformation.DISABLE);
      return this;
    }

    /** Passthrough of the javac generated assertion code in all packages and classes. */
    public AssertionsConfiguration.Builder passthrough() {
      setTransformation(AssertionTransformation.PASSTHROUGH);
      return this;
    }

    /** Set how to handle javac generated assertion code in package and all subpackages. */
    public AssertionsConfiguration.Builder setTransformationForPackage(
        String packageName, AssertionTransformation transformation) {
      addEntry(transformation, ConfigurationType.PACKAGE, packageName);
      return this;
    }

    /**
     * Unconditionally enable javac generated assertion code in package <code>packageName</code> and
     * all subpackages. This corresponds to passing <code>-enableassertions:packageName...</code> or
     * <code>-ea:packageName...</code> to the java CLI.
     *
     * <p>If <code>packageName</code> is the empty string, assertions are enabled in the unnamed
     * package, which corresponds to passing <code>-enableassertions:...</code> or <code>-ea:...
     * </code> to the java CLI.
     */
    public AssertionsConfiguration.Builder enableForPackage(String packageName) {
      return setTransformationForPackage(packageName, AssertionTransformation.ENABLE);
    }

    /**
     * Disable the javac generated assertion code in package <code>packageName</code> and all
     * subpackages. This corresponds to passing <code>-disableassertions:packageName...</code> or
     * <code>-da:packageName...</code> to the java CLI.
     *
     * <p>If <code>packageName</code> is the empty string assertions are disabled in the unnamed
     * package, which corresponds to passing <code>-disableassertions:...</code> or <code>-da:...
     * </code> to the java CLI.
     */
    public AssertionsConfiguration.Builder disableForPackage(String packageName) {
      return setTransformationForPackage(packageName, AssertionTransformation.DISABLE);
    }

    public AssertionsConfiguration.Builder passthroughForPackage(String packageName) {
      return setTransformationForPackage(packageName, AssertionTransformation.PASSTHROUGH);
    }

    /** Set how to handle javac generated assertion code in class. */
    public AssertionsConfiguration.Builder setTransformationForClass(
        String className, AssertionTransformation transformation) {
      addEntry(transformation, ConfigurationType.CLASS, className);
      return this;
    }

    /**
     * Unconditionally enable javac generated assertion in class <code>className</code>. This
     * corresponds to passing <code> -enableassertions:className</code> or <code>-ea:className
     * </code> to the java CLI.
     */
    public AssertionsConfiguration.Builder enableForClass(String className) {
      return setTransformationForClass(className, AssertionTransformation.ENABLE);
    }

    /**
     * Disable the javac generated assertion code in class <code>className</code>. This corresponds
     * to passing <code> -disableassertions:className</code> or <code>-da:className</code> to the
     * java CLI.
     */
    public AssertionsConfiguration.Builder disableForClass(String className) {
      return setTransformationForClass(className, AssertionTransformation.DISABLE);
    }

    public AssertionsConfiguration.Builder passthroughForClass(String className) {
      return setTransformationForClass(className, AssertionTransformation.PASSTHROUGH);
    }

    /** Build and return the {@link AssertionsConfiguration}. */
    public AssertionsConfiguration build() {
      return new AssertionsConfiguration(entries);
    }
  }
}
