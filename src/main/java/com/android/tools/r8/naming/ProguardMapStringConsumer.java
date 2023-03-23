// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ProguardMapConsumer;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.utils.ChainableStringConsumer;

/***
 * Default implementation of a ProguardMapConsumer that wraps around a string consumer for streamed
 * string output.
 */
public class ProguardMapStringConsumer extends ProguardMapConsumer
    implements ChainableStringConsumer {

  private final StringConsumer stringConsumer;
  private final DiagnosticsHandler diagnosticsHandler;

  private ProguardMapStringConsumer(
      StringConsumer stringConsumer, DiagnosticsHandler diagnosticsHandler) {
    assert stringConsumer != null;
    assert diagnosticsHandler != null;
    this.stringConsumer = stringConsumer;
    this.diagnosticsHandler = diagnosticsHandler;
  }

  @Override
  public void accept(ProguardMapMarkerInfo markerInfo, ClassNameMapper classNameMapper) {
    accept(markerInfo.serializeToString());
    classNameMapper.write(this);
  }

  @Override
  public ChainableStringConsumer accept(String string) {
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
    private DiagnosticsHandler diagnosticsHandler;

    public Builder setStringConsumer(StringConsumer stringConsumer) {
      this.stringConsumer = stringConsumer;
      return this;
    }

    public Builder setDiagnosticsHandler(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
      return this;
    }

    public ProguardMapStringConsumer build() {
      return new ProguardMapStringConsumer(stringConsumer, diagnosticsHandler);
    }
  }
}
