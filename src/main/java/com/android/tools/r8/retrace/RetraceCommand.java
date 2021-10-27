// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.retrace.internal.StackTraceRegularExpressionParser;
import java.util.List;
import java.util.function.Consumer;

@Keep
public class RetraceCommand {

  private final List<String> stackTrace;
  private final Consumer<List<String>> retracedStackTraceConsumer;
  // Not inheriting to allow for static builder methods.
  private final RetraceOptions options;

  private RetraceCommand(
      List<String> stackTrace,
      Consumer<List<String>> retracedStackTraceConsumer,
      RetraceOptions options) {
    this.stackTrace = stackTrace;
    this.retracedStackTraceConsumer = retracedStackTraceConsumer;
    this.options = options;

    assert this.stackTrace != null || options.isVerifyMappingFileHash();
    assert this.retracedStackTraceConsumer != null;
  }

  public boolean printTimes() {
    return System.getProperty("com.android.tools.r8.printtimes") != null;
  }

  public boolean printMemory() {
    return System.getProperty("com.android.tools.r8.printmemory") != null;
  }

  public List<String> getStackTrace() {
    return stackTrace;
  }

  public Consumer<List<String>> getRetracedStackTraceConsumer() {
    return retracedStackTraceConsumer;
  }

  public RetraceOptions getOptions() {
    return options;
  }

  /**
   * Utility method for obtaining a RetraceCommand builder.
   *
   * @param diagnosticsHandler The diagnostics handler for consuming messages.
   */
  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  /** Utility method for obtaining a RetraceCommand builder with a default diagnostics handler. */
  public static Builder builder() {
    return new Builder(new DiagnosticsHandler() {});
  }

  @Keep
  public static class Builder {

    private boolean isVerbose;
    private final DiagnosticsHandler diagnosticsHandler;
    private ProguardMapProducer proguardMapProducer;
    private String regularExpression = StackTraceRegularExpressionParser.DEFAULT_REGULAR_EXPRESSION;
    private List<String> stackTrace;
    private Consumer<List<String>> retracedStackTraceConsumer;
    private boolean verifyMappingFileHash = false;

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
    }

    /** Set if the produced stack trace should have additional information. */
    public Builder setVerbose(boolean verbose) {
      this.isVerbose = verbose;
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

    /**
     * Set the obfuscated stack trace that is to be retraced.
     *
     * @param stackTrace Stack trace having the top entry(the closest stack to the error) as the
     *     first line.
     */
    public Builder setStackTrace(List<String> stackTrace) {
      this.stackTrace = stackTrace;
      return this;
    }

    /** Set if the mapping-file hash should be checked if present. */
    public Builder setVerifyMappingFileHash(boolean verifyMappingFileHash) {
      this.verifyMappingFileHash = verifyMappingFileHash;
      return this;
    }

    /**
     * Set a consumer for receiving the retraced stack trace.
     *
     * @param consumer Consumer for receiving the retraced stack trace.
     */
    public Builder setRetracedStackTraceConsumer(Consumer<List<String>> consumer) {
      this.retracedStackTraceConsumer = consumer;
      return this;
    }

    public RetraceCommand build() {
      if (this.diagnosticsHandler == null) {
        throw new RuntimeException("DiagnosticsHandler not specified");
      }
      if (this.proguardMapProducer == null) {
        throw new RuntimeException("ProguardMapSupplier not specified");
      }
      if (this.stackTrace == null && !verifyMappingFileHash) {
        throw new RuntimeException("StackTrace not specified");
      }
      if (this.retracedStackTraceConsumer == null) {
        throw new RuntimeException("RetracedStackConsumer not specified");
      }
      RetraceOptions retraceOptions =
          RetraceOptions.builder(diagnosticsHandler)
              .setRegularExpression(regularExpression)
              .setProguardMapProducer(proguardMapProducer)
              .setVerbose(isVerbose)
              .setVerifyMappingFileHash(verifyMappingFileHash)
              .build();
      return new RetraceCommand(stackTrace, retracedStackTraceConsumer, retraceOptions);
    }

  }
}
