// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.StringDiagnostic;

@Keep
public class AssertionsConfiguration {

  /** The possible transformations of the javac generated assertion code during compilation. */
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

  private AssertionTransformation transformation;

  private AssertionsConfiguration(AssertionTransformation transformation) {
    this.transformation = transformation;
  }

  static Builder builder(DiagnosticsHandler handler) {
    return new Builder(handler);
  }

  /**
   * Builder for constructing a <code>{@link AssertionsConfiguration}</code>.
   *
   * <p>A builder is obtained by calling {@link
   * BaseCompilerCommand.Builder#addAssertionsConfiguration}.
   */
  public AssertionTransformation getTransformation() {
    return transformation;
  }

  @Keep
  public static class Builder {
    private AssertionTransformation transformation = null;

    private final DiagnosticsHandler handler;

    private Builder(DiagnosticsHandler handler) {
      this.handler = handler;
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
      handler.error(new StringDiagnostic("Unsupported"));
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
      handler.error(new StringDiagnostic("Unsupported"));
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
      return new AssertionsConfiguration(transformation);
    }
  }
}
