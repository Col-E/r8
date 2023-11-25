// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.function.Consumer;

@KeepForApi
public interface ProguardMapPartitionerBuilder<
    B extends ProguardMapPartitionerBuilder<B, P>, P extends ProguardMapPartitioner> {

  B setProguardMapProducer(ProguardMapProducer proguardMapProducer);

  B setPartitionConsumer(Consumer<MappingPartition> consumer);

  B setAllowEmptyMappedRanges(boolean allowEmptyMappedRanges);

  B setAllowExperimentalMapping(boolean allowExperimentalMapping);

  P build();
}
