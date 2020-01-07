// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.Reporter;

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

  public enum AssertionTransformationScope {
    ALL,
    PACKAGE,
    CLASS
  }

  private final AssertionTransformation transformation;
  private final AssertionTransformationScope scope;
  private final String value;

  AssertionsConfiguration(
      AssertionTransformation transformation, AssertionTransformationScope scope, String value) {
    this.transformation = transformation;
    this.scope = scope;
    this.value = value;
  }

  public AssertionTransformation getTransformation() {
    return transformation;
  }

  public AssertionTransformationScope getScope() {
    return scope;
  }

  public String getValue() {
    return value;
  }

  static AssertionsConfiguration.Builder builder(Reporter reporter) {
    return new AssertionsConfiguration.Builder(reporter);
  }

  /**
   * Builder for constructing a <code>{@link AssertionsConfiguration}</code>.
   *
   * <p>A builder is obtained by calling {@link
   * BaseCompilerCommand.Builder#addAssertionsConfiguration}.
   */
  @Keep
  public static class Builder {
    Reporter reporter;
    private AssertionTransformation transformation;
    private AssertionTransformationScope scope;
    private String value;

    private Builder(Reporter reporter) {
      this.reporter = reporter;
    }

    /** Set how to handle javac generated assertion code. */
    public AssertionsConfiguration.Builder setTransformation(
        AssertionTransformation transformation) {
      this.transformation = transformation;
      return this;
    }

    /**
     * Unconditionally enable javac generated assertion code in all packages and classes. This
     * corresponds to passing <code>-enableassertions</code> or <code>-ea</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder setEnable() {
      setTransformation(AssertionTransformation.ENABLE);
      return this;
    }

    /**
     * Disable the javac generated assertion code in all packages and classes. This corresponds to
     * passing <code>-disableassertions</code> or <code>-da</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder setDisable() {
      setTransformation(AssertionTransformation.DISABLE);
      return this;
    }

    /** Passthrough of the javac generated assertion code in all packages and classes. */
    public AssertionsConfiguration.Builder setPassthrough() {
      setTransformation(AssertionTransformation.PASSTHROUGH);
      return this;
    }

    public AssertionsConfiguration.Builder setScopeAll() {
      this.scope = AssertionTransformationScope.ALL;
      this.value = null;
      return this;
    }

    /**
     * Apply the specified transformation in package <code>packageName</code> and all subpackages.
     * If <code>packageName</code> is the empty string, this specifies that the transformation is
     * applied ion the unnamed package.
     *
     * <p>If the transformation is 'enable' this corresponds to passing <code>
     * -enableassertions:packageName...</code> or <code>-ea:packageName...</code> to the java CLI.
     *
     * <p>If the transformation is 'disable' this corresponds to passing <code>
     * -disableassertions:packageName...</code> or <code>-da:packageName...</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder setScopePackage(String packageName) {
      this.scope = AssertionTransformationScope.PACKAGE;
      this.value = packageName;
      return this;
    }

    /**
     * Apply the specified transformation in class <code>className</code>.
     *
     * <p>If the transformation is 'enable' this corresponds to passing <code>
     * -enableassertions:className</code> or <code>-ea:className...</code> to the java CLI.
     *
     * <p>If the transformation is 'disable' this corresponds to passing <code>
     * -disableassertions:className</code> or <code>-da:className</code> to the java CLI.
     */
    public AssertionsConfiguration.Builder setScopeClass(String className) {
      this.scope = AssertionTransformationScope.CLASS;
      this.value = className;
      return this;
    }

    /** Build and return the {@link AssertionsConfiguration}. */
    public AssertionsConfiguration build() {
      if (transformation == null) {
        reporter.error("No transformation specified for building AccertionConfiguration");
      }
      if (scope == null) {
        reporter.error("No scope specified for building AccertionConfiguration");
      }
      if (scope == AssertionTransformationScope.PACKAGE && value == null) {
        reporter.error("No package name specified for building AccertionConfiguration");
      }
      if (scope == AssertionTransformationScope.CLASS && value == null) {
        reporter.error("No class name specified for building AccertionConfiguration");
      }
      return new AssertionsConfiguration(transformation, scope, value);
    }

    /**
     * Static helper to build an <code>AssertionConfiguration</code> which unconditionally enables
     * javac generated assertion code in all packages and classes. To be used like this:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(AssertionsConfiguration.Builder::enableAllAssertions)
     *     ...
     *     .build();
     * </pre>
     *
     * which is a shorthand for:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(builder -> builder.setEnabled().setScopeAll().build())
     *     ...
     *     .build();
     * </pre>
     */
    public static AssertionsConfiguration enableAllAssertions(
        AssertionsConfiguration.Builder builder) {
      return builder.setEnable().setScopeAll().build();
    }

    /**
     * Static helper to build an <code>AssertionConfiguration</code> which unconditionally disables
     * javac generated assertion code in all packages and classes. To be used like this:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(AssertionsConfiguration.Builder::disableAllAssertions)
     *     ...
     *     .build();
     * </pre>
     *
     * which is a shorthand for:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(builder -> builder.setDisabled().setScopeAll().build())
     *     ...
     *     .build();
     * </pre>
     */
    public static AssertionsConfiguration disableAllAssertions(
        AssertionsConfiguration.Builder builder) {
      return builder.setDisable().setScopeAll().build();
    }

    /**
     * Static helper to build an <code>AssertionConfiguration</code> which will passthrough javac
     * generated assertion code in all packages and classes. To be used like this:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(AssertionsConfiguration.Builder::passthroughAllAssertions)
     *     ...
     *     .build();
     * </pre>
     *
     * which is a shorthand for:
     *
     * <pre>
     *   D8Command command = D8Command.builder()
     *     .addAssertionsConfiguration(builder -> builder.setPassthrough().setScopeAll().build())
     *     ...
     *     .build();
     * </pre>
     */
    public static AssertionsConfiguration passthroughAllAssertions(
        AssertionsConfiguration.Builder builder) {
      return builder.setPassthrough().setScopeAll().build();
    }
  }
}
