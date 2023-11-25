// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/***
 * Callback to be called to prepare all partitions registered by
 * {@link RegisterMappingPartitionCallback}. This is guaranteed to be called before calling
 * {@link MappingPartitionFromKeySupplier}.
 */
@FunctionalInterface
@KeepForApi
public interface PrepareMappingPartitionsCallback {

  PrepareMappingPartitionsCallback EMPTY_INSTANCE = () -> {};

  static PrepareMappingPartitionsCallback empty() {
    return EMPTY_INSTANCE;
  }

  void prepare();
}
