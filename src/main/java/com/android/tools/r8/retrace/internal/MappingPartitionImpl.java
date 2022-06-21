// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.MappingPartition;

public class MappingPartitionImpl implements MappingPartition {

  private final String key;
  private final byte[] payload;

  public MappingPartitionImpl(String key, byte[] payload) {
    this.key = key;
    this.payload = payload;
  }

  @Override
  public String getKey() {
    return key;
  }

  @Override
  public byte[] getPayload() {
    return payload;
  }
}
