// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.google.common.primitives.Ints;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public interface MappingPartitionMetadataInternal extends MappingPartitionMetadata {

  String getKey(ClassReference classReference);

  MapVersion getMapVersion();

  byte ZERO_BYTE = (byte) 0;

  static MappingPartitionMetadataInternal createFromBytes(
      byte[] bytes, MapVersion fallBackMapVersion, DiagnosticsHandler diagnosticsHandler) {
    if (bytes == null) {
      return obfuscatedTypeNameAsKey(fallBackMapVersion);
    } else if (bytes.length > 2) {
      int serializedStrategyId = Ints.fromBytes(ZERO_BYTE, ZERO_BYTE, bytes[0], bytes[1]);
      MapVersion mapVersion = MapVersion.fromName(new String(bytes, 2, bytes.length - 2));
      if (serializedStrategyId == OBFUSCATED_TYPE_NAME_AS_KEY.serializedKey) {
        return obfuscatedTypeNameAsKey(mapVersion);
      }
    }
    RuntimeException exception = new RuntimeException("Unable to build key strategy from metadata");
    diagnosticsHandler.error(new ExceptionDiagnostic(exception));
    throw exception;
  }

  static ObfuscatedTypeNameAsKeyMetadata obfuscatedTypeNameAsKey(MapVersion mapVersion) {
    return new ObfuscatedTypeNameAsKeyMetadata(mapVersion);
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

    @Override
    public byte[] getBytes() {
      try {
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        DataOutputStream dataOutputStream = new DataOutputStream(temp);
        dataOutputStream.writeShort(OBFUSCATED_TYPE_NAME_AS_KEY.serializedKey);
        dataOutputStream.writeBytes(mapVersion.getName());
        dataOutputStream.close();
        return temp.toByteArray();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
