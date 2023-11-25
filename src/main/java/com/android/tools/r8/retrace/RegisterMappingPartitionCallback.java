// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/***
 * Interface for registering a mapping partition to be used later.
 */
@FunctionalInterface
@KeepForApi
public interface RegisterMappingPartitionCallback {

  RegisterMappingPartitionCallback EMPTY_INSTANCE = key -> {};

  static RegisterMappingPartitionCallback empty() {
    return EMPTY_INSTANCE;
  }

  void register(String key);
}
