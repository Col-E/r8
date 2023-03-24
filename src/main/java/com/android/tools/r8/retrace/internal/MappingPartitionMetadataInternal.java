// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY;
import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.internal.MetadataPartitionCollection.LazyMetadataPartitionCollection;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.google.common.primitives.Ints;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;

public interface MappingPartitionMetadataInternal extends MappingPartitionMetadata {

  String getKey(ClassReference classReference);

  MapVersion getMapVersion();

  default boolean canGetPartitionKeys() {
    return false;
  }

  default Collection<String> getPartitionKeys() {
    return null;
  }

  byte ZERO_BYTE = (byte) 0;
  // Magic byte put into the metadata
  byte[] MAGIC = new byte[] {(byte) 0xAA, (byte) 0xA8};

  static int magicOffset() {
    return MAGIC.length;
  }

  static MappingPartitionMetadataInternal deserialize(
      byte[] bytes, MapVersion fallBackMapVersion, DiagnosticsHandler diagnosticsHandler) {
    if (bytes == null) {
      return ObfuscatedTypeNameAsKeyMetadata.create(fallBackMapVersion);
    }
    if (bytes.length > 2) {
      if (startsWithMagic(bytes)) {
        int serializedKey =
            Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, bytes[magicOffset()], bytes[magicOffset() + 1]);
        if (serializedKey == OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS.getSerializedKey()) {
          return ObfuscatedTypeNameAsKeyMetadataWithPartitionNames.deserialize(bytes);
        }
      } else if (OBFUSCATED_TYPE_NAME_AS_KEY.getSerializedKey()
          == Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, bytes[0], bytes[1])) {
        return ObfuscatedTypeNameAsKeyMetadata.deserialize(bytes);
      }
    }
    // If we arrived here then we could not deserialize the metadata.
    RetracePartitionException exception =
        new RetracePartitionException("Unknown map partition strategy for metadata");
    diagnosticsHandler.error(new ExceptionDiagnostic(exception));
    throw exception;
  }

  private static boolean startsWithMagic(byte[] bytes) {
    if (bytes.length < MAGIC.length) {
      return false;
    }
    for (int i = 0; i < MAGIC.length; i++) {
      if (bytes[i] != MAGIC[i]) {
        return false;
      }
    }
    return true;
  }

  class ObfuscatedTypeNameAsKeyMetadata implements MappingPartitionMetadataInternal {

    private final MapVersion mapVersion;

    private ObfuscatedTypeNameAsKeyMetadata(MapVersion mapVersion) {
      this.mapVersion = mapVersion;
    }

    @Override
    public String getKey(ClassReference classReference) {
      return classReference.getTypeName();
    }

    @Override
    public MapVersion getMapVersion() {
      return mapVersion;
    }

    // The format is:
    // <type:short><map-version>
    @Override
    public byte[] getBytes() {
      try {
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(temp);
        dataOutputStream.writeShort(OBFUSCATED_TYPE_NAME_AS_KEY.getSerializedKey());
        dataOutputStream.writeBytes(mapVersion.getName());
        dataOutputStream.close();
        return temp.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static ObfuscatedTypeNameAsKeyMetadata deserialize(byte[] bytes) {
      MapVersion mapVersion = MapVersion.fromName(new String(bytes, 2, bytes.length - 2));
      return create(mapVersion);
    }

    public static ObfuscatedTypeNameAsKeyMetadata create(MapVersion mapVersion) {
      return new ObfuscatedTypeNameAsKeyMetadata(mapVersion);
    }
  }

  class ObfuscatedTypeNameAsKeyMetadataWithPartitionNames
      implements MappingPartitionMetadataInternal {

    private final MapVersion mapVersion;
    private final MetadataPartitionCollection metadataPartitionCollection;

    private ObfuscatedTypeNameAsKeyMetadataWithPartitionNames(
        MapVersion mapVersion, MetadataPartitionCollection metadataPartitionCollection) {
      this.mapVersion = mapVersion;
      this.metadataPartitionCollection = metadataPartitionCollection;
    }

    public static ObfuscatedTypeNameAsKeyMetadataWithPartitionNames create(
        MapVersion mapVersion, MetadataPartitionCollection metadataPartitionCollection) {
      return new ObfuscatedTypeNameAsKeyMetadataWithPartitionNames(
          mapVersion, metadataPartitionCollection);
    }

    @Override
    public String getKey(ClassReference classReference) {
      return classReference.getTypeName();
    }

    @Override
    public MapVersion getMapVersion() {
      return mapVersion;
    }

    @Override
    public boolean canGetPartitionKeys() {
      return true;
    }

    @Override
    public Collection<String> getPartitionKeys() {
      return metadataPartitionCollection.getPartitionKeys();
    }

    // The format is:
    // <type:short><map-version-length:short><map-version>[<partition_key>]
    @Override
    public byte[] getBytes() {
      try {
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(temp);
        dataOutputStream.write(MAGIC);
        dataOutputStream.writeShort(OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS.getSerializedKey());
        String name = mapVersion.getName();
        dataOutputStream.writeShort(name.length());
        dataOutputStream.writeBytes(name);
        dataOutputStream.write(metadataPartitionCollection.serialize());
        dataOutputStream.close();
        return temp.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static ObfuscatedTypeNameAsKeyMetadataWithPartitionNames deserialize(byte[] bytes) {
      int start = magicOffset();
      int length = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, bytes[start + 2], bytes[start + 3]);
      MapVersion mapVersion = MapVersion.fromName(new String(bytes, start + 4, length));
      int partitionCollectionStartIndex = start + 4 + length;
      return ObfuscatedTypeNameAsKeyMetadataWithPartitionNames.create(
          mapVersion,
          new LazyMetadataPartitionCollection(bytes, partitionCollectionStartIndex, bytes.length));
    }
  }
}
