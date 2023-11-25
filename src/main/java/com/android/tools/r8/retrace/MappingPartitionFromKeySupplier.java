// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/***
 * Supplier to provide the bytes for a partition specified by a key. Retrace guarantee that the
 * supplier is always called after a call with the key to {@link RegisterMappingPartitionCallback}
 * and a call to {@link PrepareMappingPartitionsCallback}
 *
 */
@FunctionalInterface
@KeepForApi
public interface MappingPartitionFromKeySupplier {

  byte[] get(String key);
}
