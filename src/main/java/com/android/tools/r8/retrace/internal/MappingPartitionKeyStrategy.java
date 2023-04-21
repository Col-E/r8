// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

public enum MappingPartitionKeyStrategy {
  UNKNOWN(-1),
  OBFUSCATED_TYPE_NAME_AS_KEY(0),
  OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS(1);

  private static final MappingPartitionKeyStrategy DEFAULT_STRATEGY =
      OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS;

  private final int serializedKey;

  MappingPartitionKeyStrategy(int serializedKey) {
    this.serializedKey = serializedKey;
  }

  public static MappingPartitionKeyStrategy getByKey(int serializedKey) {
    switch (serializedKey) {
      case 0:
        return OBFUSCATED_TYPE_NAME_AS_KEY;
      case 1:
        return OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS;
      default:
        return UNKNOWN;
    }
  }

  public int getSerializedKey() {
    return serializedKey;
  }

  public static MappingPartitionKeyStrategy getDefaultStrategy() {
    return DEFAULT_STRATEGY;
  }
}
