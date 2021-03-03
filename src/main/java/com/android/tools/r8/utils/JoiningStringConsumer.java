// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringConsumer.ForwardingConsumer;

/* Joining String Consumer to join strings that it accepts. */
public class JoiningStringConsumer extends ForwardingConsumer {

  private final String separator;
  private final StringConsumer consumer;
  private final StringBuilder builder = new StringBuilder();

  /**
   * @param consumer Consumer to forward to the joined input to. If null, nothing will be forwarded.
   */
  public JoiningStringConsumer(StringConsumer consumer, String separator) {
    super(consumer);
    this.consumer = consumer;
    this.separator = separator;
  }

  @Override
  public void accept(String string, DiagnosticsHandler handler) {
    if (builder.length() > 0) {
      builder.append(separator);
    }
    builder.append(string);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    super.accept(builder.toString(), handler);
    super.finished(handler);
  }

  public StringConsumer getConsumer() {
    return consumer;
  }
}
