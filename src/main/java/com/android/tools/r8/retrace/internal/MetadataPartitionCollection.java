// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.utils.SerializationUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.DataOutputStream;
import java.io.IOException;
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

  // The format is:
  // <length-in-bytes:int><data>
  public void serialize(DataOutputStream dataOutputStream) throws IOException {
    SerializationUtils.writeUTFOfIntSize(
        dataOutputStream, StringUtils.join(SEPARATOR + "", partitionKeys));
  }

  private static MetadataPartitionCollection deserialize(byte[] bytes) {
    String allKeys = new String(bytes, StandardCharsets.UTF_8);
    return create(StringUtils.split(allKeys, SEPARATOR));
  }

  public static MetadataPartitionCollection create(Collection<String> partitionKeys) {
    return new MetadataPartitionCollection(partitionKeys);
  }

  public static class LazyMetadataPartitionCollection extends MetadataPartitionCollection {

    private byte[] bytes;
    private MetadataPartitionCollection metadataPartitionCollection = null;

    private LazyMetadataPartitionCollection(byte[] bytes) {
      super(Collections.emptyList());
      this.bytes = bytes;
    }

    @Override
    public Collection<String> getPartitionKeys() {
      if (metadataPartitionCollection == null) {
        metadataPartitionCollection = deserialize(bytes);
        bytes = null;
      }
      return metadataPartitionCollection.getPartitionKeys();
    }

    public static LazyMetadataPartitionCollection create(CompatByteBuffer buffer) {
      return new LazyMetadataPartitionCollection(buffer.getBytesOfIntSize());
    }
  }
}
