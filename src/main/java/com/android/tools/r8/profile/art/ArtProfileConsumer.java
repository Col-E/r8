// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * API for consuming an ART profile provided by the compiler, which has been rewritten to match the
 * residual, optimized app.
 */
@KeepForApi
public interface ArtProfileConsumer {

  /**
   * Returns a {@link TextOutputStream} that will receive the rules of the residual ART profile in
   * the human-readable ART baseline profile format. This may return <code>null</code> to specify
   * that the compiler should not emit the residual ART profile in the human-readable ART baseline
   * profile format.
   *
   * @see <a href="https://developer.android.com/topic/performance/baselineprofiles">ART Baseline
   *     Profiles</a>
   */
  default TextOutputStream getHumanReadableArtProfileConsumer() {
    return null;
  }

  /**
   * Returns an {@link ArtProfileRuleConsumer} that will receive the rules of the residual ART
   * profile. If this returns <code>null</code> no rules will be emitted.
   */
  default ArtProfileRuleConsumer getRuleConsumer() {
    return null;
  }

  /**
   * Callback signifying that all rules of the residual ART profile have been emitted to the rule
   * consumer.
   *
   * @param handler Diagnostics handler for reporting.
   */
  void finished(DiagnosticsHandler handler);
}
