// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY;
import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS;
import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.getByKey;
import static com.android.tools.r8.utils.SerializationUtils.getZeroByte;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.retrace.internal.MetadataAdditionalInfo.LazyMetadataAdditionalInfo;
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

  default boolean canGetAdditionalInfo() {
    return false;
  }

  default MetadataAdditionalInfo getAdditionalInfo() {
    return MetadataAdditionalInfo.create(null, null);
  }

  // Magic byte put into the metadata
  byte[] MAGIC = new byte[] {(byte) 0xAA, (byte) 0xA8};

  static int magicOffset() {
    return MAGIC.length;
  }

  static MappingPartitionMetadataInternal deserialize(
      CompatByteBuffer buffer,
      MapVersion fallBackMapVersion,
      DiagnosticsHandler diagnosticsHandler) {
    if (buffer == null) {
      return ObfuscatedTypeNameAsKeyMetadata.create(fallBackMapVersion);
    }
    if (buffer.remaining() > 2) {
      int magicOrStrategyKey = buffer.getUShort();
      if (magicOrStrategyKey == Ints.fromBytes(getZeroByte(), getZeroByte(), MAGIC[0], MAGIC[1])) {
        magicOrStrategyKey = buffer.getShort();
      }
      switch (getByKey(magicOrStrategyKey)) {
        case OBFUSCATED_TYPE_NAME_AS_KEY:
          return ObfuscatedTypeNameAsKeyMetadata.deserialize(buffer);
        case OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS:
          return ObfuscatedTypeNameAsKeyMetadataWithPartitionNames.deserialize(buffer);
        default:
          throw new RetracePartitionException(
              "Could not find partition key strategy from serialized key: " + magicOrStrategyKey);
      }
    }
    // If we arrived here then we could not deserialize the metadata.
    RetracePartitionException exception =
        new RetracePartitionException("Unknown map partition strategy for metadata");
    diagnosticsHandler.error(new ExceptionDiagnostic(exception));
    throw exception;
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

    public static ObfuscatedTypeNameAsKeyMetadata deserialize(CompatByteBuffer buffer) {
      byte[] array = buffer.array();
      MapVersion mapVersion = MapVersion.fromName(new String(array, 2, array.length - 2));
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
    private final MetadataAdditionalInfo metadataAdditionalInfo;

    private ObfuscatedTypeNameAsKeyMetadataWithPartitionNames(
        MapVersion mapVersion,
        MetadataPartitionCollection metadataPartitionCollection,
        MetadataAdditionalInfo metadataAdditionalInfo) {
      this.mapVersion = mapVersion;
      this.metadataPartitionCollection = metadataPartitionCollection;
      this.metadataAdditionalInfo = metadataAdditionalInfo;
    }

    public static ObfuscatedTypeNameAsKeyMetadataWithPartitionNames create(
        MapVersion mapVersion,
        MetadataPartitionCollection metadataPartitionCollection,
        MetadataAdditionalInfo metadataAdditionalInfo) {
      return new ObfuscatedTypeNameAsKeyMetadataWithPartitionNames(
          mapVersion, metadataPartitionCollection, metadataAdditionalInfo);
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

    @Override
    public boolean canGetAdditionalInfo() {
      return true;
    }

    @Override
    public MetadataAdditionalInfo getAdditionalInfo() {
      return metadataAdditionalInfo;
    }

    // The format is:
    // <MAGIC><type:short><map-version-length:short><map-version>{partitions}{additionalinfo}
    @Override
    public byte[] getBytes() {
      try {
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(temp);
        dataOutputStream.write(MAGIC);
        dataOutputStream.writeShort(OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS.getSerializedKey());
        dataOutputStream.writeUTF(mapVersion.getName());
        metadataPartitionCollection.serialize(dataOutputStream);
        metadataAdditionalInfo.serialize(dataOutputStream);
        dataOutputStream.close();
        return temp.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public static ObfuscatedTypeNameAsKeyMetadataWithPartitionNames deserialize(
        CompatByteBuffer buffer) {
      String utf = buffer.getUTFOfUByteSize();
      MapVersion mapVersion = MapVersion.fromName(utf);
      LazyMetadataPartitionCollection metadataPartitionCollection =
          LazyMetadataPartitionCollection.create(buffer);
      LazyMetadataAdditionalInfo lazyMetadataAdditionalInfo =
          LazyMetadataAdditionalInfo.create(buffer);
      return ObfuscatedTypeNameAsKeyMetadataWithPartitionNames.create(
          mapVersion, metadataPartitionCollection, lazyMetadataAdditionalInfo);
    }
  }
}
