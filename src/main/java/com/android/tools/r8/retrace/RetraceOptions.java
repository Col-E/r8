// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser.DEFAULT_REGULAR_EXPRESSION;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;

/**
 * The base options for running retrace with support for continuously retrace strings without
 * parsing the proguard map multiple times.
 */
@Keep
public class RetraceOptions {

  private final boolean isVerbose;
  private final boolean verifyMappingFileHash;
  private final String regularExpression;
  private final DiagnosticsHandler diagnosticsHandler;
  private final ProguardMapProducer proguardMapProducer;

  private RetraceOptions(
      String regularExpression,
      DiagnosticsHandler diagnosticsHandler,
      ProguardMapProducer proguardMapProducer,
      boolean isVerbose,
      boolean verifyMappingFileHash) {
    this.regularExpression = regularExpression;
    this.diagnosticsHandler = diagnosticsHandler;
    this.proguardMapProducer = proguardMapProducer;
    this.isVerbose = isVerbose;
    this.verifyMappingFileHash = verifyMappingFileHash;

    assert diagnosticsHandler != null;
    assert proguardMapProducer != null;
  }

  public boolean isVerbose() {
    return isVerbose;
  }

  public boolean isVerifyMappingFileHash() {
    return verifyMappingFileHash;
  }

  public String getRegularExpression() {
    return regularExpression;
  }

  public DiagnosticsHandler getDiagnosticsHandler() {
    return diagnosticsHandler;
  }

  public ProguardMapProducer getProguardMapProducer() {
    return proguardMapProducer;
  }

  /** Utility method for obtaining a builder with a default diagnostics handler. */
  public static Builder builder() {
    return builder(new DiagnosticsHandler() {});
  }

  /** Utility method for obtaining a builder. */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  public static String defaultRegularExpression() {
    return DEFAULT_REGULAR_EXPRESSION;
  }

  @Keep
  public static class Builder {

    private boolean isVerbose;
    private boolean verifyMappingFileHash;
    private final DiagnosticsHandler diagnosticsHandler;
    private ProguardMapProducer proguardMapProducer;
    private String regularExpression = defaultRegularExpression();

    Builder(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
    }

    /** Set if the produced stack trace should have additional information. */
    public Builder setVerbose(boolean verbose) {
      this.isVerbose = verbose;
      return this;
    }

    /** Set if the mapping-file hash should be checked if present. */
    public Builder setVerifyMappingFileHash(boolean verifyMappingFileHash) {
      this.verifyMappingFileHash = verifyMappingFileHash;
      return this;
    }

    /**
     * Set a producer for the proguard mapping contents.
     *
     * @param producer Producer for
     */
    public Builder setProguardMapProducer(ProguardMapProducer producer) {
      this.proguardMapProducer = producer;
      return this;
    }

    /**
     * Set a regular expression for parsing the incoming text. The Regular expression must not use
     * naming groups and has special wild cards according to proguard retrace. Note, this will
     * override the default regular expression.
     *
     * @param regularExpression The regular expression to use.
     */
    public Builder setRegularExpression(String regularExpression) {
      this.regularExpression = regularExpression;
      return this;
    }

    public RetraceOptions build() {
      if (this.diagnosticsHandler == null) {
        throw new RuntimeException("DiagnosticsHandler not specified");
      }
      if (this.proguardMapProducer == null) {
        throw new RuntimeException("ProguardMapSupplier not specified");
      }
      if (this.regularExpression == null) {
        throw new RuntimeException("Regular expression not specified");
      }
      return new RetraceOptions(
          regularExpression,
          diagnosticsHandler,
          proguardMapProducer,
          isVerbose,
          verifyMappingFileHash);
    }
  }
}
