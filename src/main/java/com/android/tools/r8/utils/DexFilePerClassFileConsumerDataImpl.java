// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexFilePerClassFileConsumerData;
import com.android.tools.r8.DiagnosticsHandler;
import java.util.Set;

public class DexFilePerClassFileConsumerDataImpl implements DexFilePerClassFileConsumerData {

  private final String primaryClassDescriptor;
  private final ByteDataView data;
  private final Set<String> classDescriptors;
  private final DiagnosticsHandler handler;

  public DexFilePerClassFileConsumerDataImpl(
      String primaryClassDescriptor,
      ByteDataView data,
      Set<String> classDescriptors,
      DiagnosticsHandler handler) {
    this.primaryClassDescriptor = primaryClassDescriptor;
    this.data = data;
    this.classDescriptors = classDescriptors;
    this.handler = handler;
  }

  @Override
  public String getPrimaryClassDescriptor() {
    return primaryClassDescriptor;
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
  public Set<String> getClassDescriptors() {
    return classDescriptors;
  }

  @Override
  public DiagnosticsHandler getDiagnosticsHandler() {
    return handler;
  }
}
