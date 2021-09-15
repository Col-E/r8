// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class ApplicationReaderMap {

  public static Map<String, String> getDescriptorMap(InternalOptions options) {
    ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
    if (options.shouldDesugarRecords() && !options.testing.disableRecordApplicationReaderMap) {
      builder.put(DexItemFactory.recordTagDescriptorString, DexItemFactory.recordDescriptorString);
    }
    return builder.build();
  }

  public static Map<DexType, DexType> getTypeMap(InternalOptions options) {
    DexItemFactory factory = options.dexItemFactory();
    ImmutableMap.Builder<DexType, DexType> builder = ImmutableMap.builder();
    getDescriptorMap(options)
        .forEach(
            (k, v) -> {
              builder.put(factory.createType(k), factory.createType(v));
            });
    return builder.build();
  }
}
