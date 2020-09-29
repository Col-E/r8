// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.google.common.collect.BiMap;
import java.util.Map;

/** The inverse of a {@link ManyToOneMap} used for generating graph lens maps. */
public class ManyToOneInverseMap<K, V> {
  private final BiMap<V, K> biMap;
  private final Map<V, K> extraMap;

  ManyToOneInverseMap(BiMap<V, K> biMap, Map<V, K> extraMap) {
    this.biMap = biMap;
    this.extraMap = extraMap;
  }

  public BiMap<V, K> getBiMap() {
    return biMap;
  }

  public Map<V, K> getExtraMap() {
    return extraMap;
  }
}
