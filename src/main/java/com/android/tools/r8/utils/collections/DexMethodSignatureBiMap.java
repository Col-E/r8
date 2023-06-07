// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.google.common.collect.HashBiMap;

public class DexMethodSignatureBiMap<T> extends DexMethodSignatureMap<T> {

  public DexMethodSignatureBiMap() {
    super(HashBiMap.create());
  }
}
