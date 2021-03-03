// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringConsumer.ForwardingConsumer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sorting consumer that accepts all input and then sorts it when calling finished */
public class SortingStringConsumer extends ForwardingConsumer {

  private final List<String> accepted = new ArrayList<>();

  /**
   * @param consumer Consumer to forward to the sorted consumed input to. If null, nothing will be
   *     forwarded.
   */
  public SortingStringConsumer(StringConsumer consumer) {
    super(consumer);
  }

  @Override
  public void accept(String string, DiagnosticsHandler handler) {
    this.accepted.add(string);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    Collections.sort(accepted);
    accepted.forEach(string -> super.accept(string, handler));
    super.finished(handler);
  }
}
