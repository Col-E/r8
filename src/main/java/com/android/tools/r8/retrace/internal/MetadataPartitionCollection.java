// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.utils.StringUtils;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;

public class MetadataPartitionCollection {

  private static final char SEPARATOR = ';';

  private final Collection<String> partitionKeys;

  private MetadataPartitionCollection(Collection<String> partitionKeys) {
    this.partitionKeys = partitionKeys;
  }

  public Collection<String> getPartitionKeys() {
    return partitionKeys;
  }

  public byte[] serialize() {
    return StringUtils.join(SEPARATOR + "", partitionKeys).getBytes(StandardCharsets.UTF_8);
  }

  public static MetadataPartitionCollection create(Collection<String> partitionKeys) {
    return new MetadataPartitionCollection(partitionKeys);
  }

  public static MetadataPartitionCollection createLazy(
      byte[] bytes, int partitionCollectionStartIndex, int partitionCollectionEndIndex) {
    return new LazyMetadataPartitionCollection(
        bytes, partitionCollectionStartIndex, partitionCollectionEndIndex);
  }

  public static class LazyMetadataPartitionCollection extends MetadataPartitionCollection {

    private final byte[] bytes;
    private final int partitionCollectionStartIndex;
    private final int partitionCollectionEndIndex;
    private MetadataPartitionCollection metadataPartitionCollection = null;

    public LazyMetadataPartitionCollection(
        byte[] bytes, int partitionCollectionStartIndex, int partitionCollectionEndIndex) {
      super(Collections.emptyList());
      this.bytes = bytes;
      this.partitionCollectionStartIndex = partitionCollectionStartIndex;
      this.partitionCollectionEndIndex = partitionCollectionEndIndex;
    }

    @Override
    public Collection<String> getPartitionKeys() {
      if (metadataPartitionCollection == null) {
        metadataPartitionCollection = deserialize();
      }
      return metadataPartitionCollection.getPartitionKeys();
    }

    private MetadataPartitionCollection deserialize() {
      String allKeys =
          new String(
              bytes,
              partitionCollectionStartIndex,
              partitionCollectionEndIndex - partitionCollectionStartIndex);
      return create(StringUtils.split(allKeys, SEPARATOR));
    }
  }
}
