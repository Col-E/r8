// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;

@KeepForApi
public interface PartitionMapConsumer extends Finishable {

  void acceptMappingPartition(MappingPartition mappingPartition);

  void acceptMappingPartitionMetadata(MappingPartitionMetadata mappingPartitionMetadata);
}
