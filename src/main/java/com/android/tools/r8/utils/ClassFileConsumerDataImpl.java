// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumerData;
import com.android.tools.r8.DiagnosticsHandler;

/** Internal implementation of the consumer data. */
public class ClassFileConsumerDataImpl implements ClassFileConsumerData {

  private final ByteDataView data;
  private final String descriptor;
  private final DiagnosticsHandler handler;

  public ClassFileConsumerDataImpl(
      ByteDataView data, String descriptor, DiagnosticsHandler handler) {
    this.data = data;
    this.descriptor = descriptor;
    this.handler = handler;
  }

  @Override
  public ByteDataView getByteDataView() {
    return data;
  }

  @Override
  public byte[] getByteDataCopy() {
    return data.copyByteData();
  }

  @Override
  public String getClassDescriptor() {
    return descriptor;
  }

  @Override
  public DiagnosticsHandler getDiagnosticsHandler() {
    return handler;
  }
}
