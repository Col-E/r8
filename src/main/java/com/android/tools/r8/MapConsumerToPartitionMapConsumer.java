// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapConsumer;
import com.android.tools.r8.naming.ProguardMapMarkerInfo;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.internal.ProguardMapProducerInternal;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.util.List;

public class MapConsumerToPartitionMapConsumer implements MapConsumer {

  protected final PartitionMapConsumer partitionMapConsumer;

  protected MapConsumerToPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
    assert partitionMapConsumer != null;
    this.partitionMapConsumer = partitionMapConsumer;
  }

  @Override
  public void accept(
      DiagnosticsHandler diagnosticsHandler,
      ProguardMapMarkerInfo makerInfo,
      ClassNameMapper classNameMapper) {
    try {
      List<String> newPreamble =
          ListUtils.joinNewArrayList(makerInfo.toPreamble(), classNameMapper.getPreamble());
      classNameMapper.setPreamble(newPreamble);
      partitionMapConsumer.acceptMappingPartitionMetadata(
          ProguardMapPartitioner.builder(diagnosticsHandler)
              .setProguardMapProducer(new ProguardMapProducerInternal(classNameMapper))
              .setPartitionConsumer(partitionMapConsumer::acceptMappingPartition)
              // Modifying these do not actually do anything currently since there is no parsing.
              .setAllowEmptyMappedRanges(false)
              .setAllowExperimentalMapping(false)
              .build()
              .run());
    } catch (IOException exception) {
      throw new Unreachable("IOExceptions should only occur when parsing");
    }
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    partitionMapConsumer.finished(handler);
  }
}
