// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.StringUtils;

/***
 * Default implementation of a MapConsumer that wraps around a string consumer for streamed string
 * output.
 */
public class ProguardMapStringConsumer implements MapConsumer, ChainableStringConsumer {

  private final StringConsumer stringConsumer;
  private DiagnosticsHandler diagnosticsHandler;

  private ProguardMapStringConsumer(StringConsumer stringConsumer) {
    assert stringConsumer != null;
    this.stringConsumer = stringConsumer;
  }

  @Override
  public void accept(
      DiagnosticsHandler diagnosticsHandler,
      ProguardMapMarkerInfo markerInfo,
      ClassNameMapper classNameMapper) {
    this.diagnosticsHandler = diagnosticsHandler;
    accept(markerInfo.serializeToString());
    accept(StringUtils.unixLines(classNameMapper.getPreamble()));
    classNameMapper.write(this);
  }

  @Override
  public ChainableStringConsumer accept(String string) {
    assert diagnosticsHandler != null;
    stringConsumer.accept(string, diagnosticsHandler);
    return this;
  }

  public StringConsumer getStringConsumer() {
    return stringConsumer;
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    stringConsumer.finished(handler);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private StringConsumer stringConsumer;

    public Builder setStringConsumer(StringConsumer stringConsumer) {
      this.stringConsumer = stringConsumer;
      return this;
    }

    public ProguardMapStringConsumer build() {
      return new ProguardMapStringConsumer(stringConsumer);
    }
  }
}
