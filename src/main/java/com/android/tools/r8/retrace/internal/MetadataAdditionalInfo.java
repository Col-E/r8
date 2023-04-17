// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.utils.SerializationUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

public class MetadataAdditionalInfo {

  public enum AdditionalInfoTypes {
    UNKNOWN(-1),
    PREAMBLE(0);

    private final int serializedKey;

    AdditionalInfoTypes(int serializedKey) {
      this.serializedKey = serializedKey;
    }

    static AdditionalInfoTypes getByKey(int serializedKey) {
      if (serializedKey == 0) {
        return PREAMBLE;
      }
      return UNKNOWN;
    }
  }

  protected final List<String> preamble;

  private MetadataAdditionalInfo(List<String> preamble) {
    this.preamble = preamble;
  }

  public boolean hasPreamble() {
    return preamble != null;
  }

  public Collection<String> getPreamble() {
    return preamble;
  }

  // The serialized format is an extensible list where we first record the offsets for each data
  // section and then emit the data.
  // <total-size:int><number-of-elements:short>[<type-i:short><length-i:int><data-i>]
  public void serialize(DataOutputStream dataOutputStream) throws IOException {
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    DataOutputStream additionalInfoStream = new DataOutputStream(temp);
    additionalInfoStream.writeShort(1);
    additionalInfoStream.writeShort(AdditionalInfoTypes.PREAMBLE.serializedKey);
    SerializationUtils.writeUTFOfIntSize(additionalInfoStream, StringUtils.unixLines(preamble));
    byte[] payload = temp.toByteArray();
    dataOutputStream.writeInt(payload.length);
    dataOutputStream.write(payload);
  }

  private static MetadataAdditionalInfo deserialize(byte[] bytes) {
    CompatByteBuffer compatByteBuffer = CompatByteBuffer.wrap(bytes);
    int numberOfElements = compatByteBuffer.getShort();
    List<String> preamble = null;
    for (int i = 0; i < numberOfElements; i++) {
      // We are parsing <type:short><length:int><bytes>
      int additionInfoTypeKey = compatByteBuffer.getShort();
      AdditionalInfoTypes additionalInfoType = AdditionalInfoTypes.getByKey(additionInfoTypeKey);
      if (additionalInfoType == AdditionalInfoTypes.PREAMBLE) {
        preamble = StringUtils.splitLines(compatByteBuffer.getUTFOfIntSize());
      } else {
        throw new RetracePartitionException(
            "Could not additional info from key: " + additionInfoTypeKey);
      }
    }
    return new MetadataAdditionalInfo(preamble);
  }

  public static MetadataAdditionalInfo create(List<String> preamble) {
    return new MetadataAdditionalInfo(preamble);
  }

  public static class LazyMetadataAdditionalInfo extends MetadataAdditionalInfo {

    private byte[] bytes;
    private MetadataAdditionalInfo metadataAdditionalInfo = null;

    public LazyMetadataAdditionalInfo(byte[] bytes) {
      super(null);
      this.bytes = bytes;
    }

    @Override
    public boolean hasPreamble() {
      MetadataAdditionalInfo metadataAdditionalInfo = getMetadataAdditionalInfo();
      return metadataAdditionalInfo != null && metadataAdditionalInfo.hasPreamble();
    }

    @Override
    public Collection<String> getPreamble() {
      MetadataAdditionalInfo metadataAdditionalInfo = getMetadataAdditionalInfo();
      return metadataAdditionalInfo == null ? null : metadataAdditionalInfo.getPreamble();
    }

    private MetadataAdditionalInfo getMetadataAdditionalInfo() {
      if (metadataAdditionalInfo == null) {
        metadataAdditionalInfo = MetadataAdditionalInfo.deserialize(bytes);
        bytes = null;
      }
      return metadataAdditionalInfo;
    }

    public static LazyMetadataAdditionalInfo create(CompatByteBuffer buffer) {
      return new LazyMetadataAdditionalInfo(buffer.getBytesOfIntSize());
    }
  }
}
