// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.tracereferences.internal.TraceReferencesResult;
import java.nio.file.Path;

/**
 * Consumer to format the result of running {@link TraceReferences} as keep rules.
 *
 * <p>To build an instance of this consumer, use the {@link TraceReferencesKeepRules.Builder} class.
 * For example:
 *
 * <pre>
 *   TraceReferencesKeepRules consumer = TraceReferencesKeepRules.builder()
 *     .setAllowObfuscation(true)
 *     .setOutputPath(Paths.get("references-to-keep.rules"))
 *     .build();
 * </pre>
 */
@KeepForApi
public class TraceReferencesKeepRules extends TraceReferencesConsumer.ForwardingConsumer {

  private final TraceReferencesResult.Builder traceReferencesResultBuilder;
  private final StringConsumer consumer;
  private final boolean allowObfuscation;

  private TraceReferencesKeepRules(
      TraceReferencesResult.Builder traceReferencesResultBuilder,
      StringConsumer consumer,
      boolean allowObfuscation) {
    super(traceReferencesResultBuilder);
    this.traceReferencesResultBuilder = traceReferencesResultBuilder;
    this.consumer = consumer;
    this.allowObfuscation = allowObfuscation;
  }

  public boolean allowObfuscation() {
    return allowObfuscation;
  }

  /**
   * Builder for constructing a {@link TraceReferencesKeepRules].
   *
   * <p>A builder is obtained by calling {@link TraceReferencesKeepRules#builder}.
   */
  @KeepForApi
  public static class Builder {
    private StringConsumer consumer;
    private boolean allowObfuscation;

    /**
     * Indicate if the generated keep rules should have the <code>allowobfuscation</code> modifier.
     */
    public Builder setAllowObfuscation(boolean value) {
      allowObfuscation = value;
      return this;
    }

    /**
     * Set the output of the keep rules to a file.
     *
     * @param output Path to write the output to.
     */
    public Builder setOutputPath(Path output) {
      this.consumer = new FileConsumer(output);
      return this;
    }

    /**
     * Set the output of the keep rules to a {@link com.android.tools.r8.StringConsumer}.
     *
     * @param consumer Consumer to send the output to.
     */
    public Builder setOutputConsumer(StringConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    /** Build the {@link TraceReferencesKeepRules} instance. */
    public TraceReferencesKeepRules build() {
      return new TraceReferencesKeepRules(
          TraceReferencesResult.builder(), consumer, allowObfuscation);
    }
  }

  /** Create a builder for constructing an instance of {@link TraceReferencesKeepRules.Builder}. */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    super.finished(handler);
    Formatter formatter = new KeepRuleFormatter(allowObfuscation);
    formatter.format(traceReferencesResultBuilder.build());
    consumer.accept(formatter.get(), handler);
    consumer.finished(handler);
  }
}
