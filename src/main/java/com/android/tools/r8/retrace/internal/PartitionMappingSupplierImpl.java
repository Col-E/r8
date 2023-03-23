// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.MappingPartitionFromKeySupplier;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.PrepareMappingPartitionsCallback;
import com.android.tools.r8.retrace.RegisterMappingPartitionCallback;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * IntelliJ highlights the class as being invalid because it cannot see getClassNameMapper is
 * defined on the class for some reason.
 */
public class PartitionMappingSupplierImpl extends PartitionMappingSupplier {

  private final byte[] metadata;
  private final RegisterMappingPartitionCallback registerPartitionCallback;
  private final PrepareMappingPartitionsCallback prepare;
  private final MappingPartitionFromKeySupplier partitionSupplier;
  private final boolean allowExperimental;
  private final MapVersion fallbackMapVersion;

  private ClassNameMapper classNameMapper;
  private final Set<String> pendingKeys = new LinkedHashSet<>();
  private final Set<String> builtKeys = new HashSet<>();

  private MappingPartitionMetadataInternal mappingPartitionMetadataCache;

  PartitionMappingSupplierImpl(
      byte[] metadata,
      RegisterMappingPartitionCallback registerPartitionCallback,
      PrepareMappingPartitionsCallback prepare,
      MappingPartitionFromKeySupplier partitionSupplier,
      boolean allowExperimental,
      MapVersion fallbackMapVersion) {
    this.metadata = metadata;
    this.registerPartitionCallback = registerPartitionCallback;
    this.prepare = prepare;
    this.partitionSupplier = partitionSupplier;
    this.allowExperimental = allowExperimental;
    this.fallbackMapVersion = fallbackMapVersion;
  }

  private MappingPartitionMetadataInternal getMetadata(DiagnosticsHandler diagnosticsHandler) {
    if (mappingPartitionMetadataCache != null) {
      return mappingPartitionMetadataCache;
    }
    return mappingPartitionMetadataCache =
        MappingPartitionMetadataInternal.deserialize(
            metadata, fallbackMapVersion, diagnosticsHandler);
  }

  @Override
  public PartitionMappingSupplier registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    registerKeyUse(getMetadata(diagnosticsHandler).getKey(classReference));
    return this;
  }

  private void registerKeyUse(String key) {
    // TODO(b/274735214): only call the register partition if we have a partition for it.
    if (!builtKeys.contains(key) && pendingKeys.add(key)) {
      registerPartitionCallback.register(key);
    }
  }

  @Override
  public void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler) {
    String errorMessage = "Cannot verify map file hash for partitions";
    diagnosticsHandler.error(new StringDiagnostic(errorMessage));
    throw new RuntimeException(errorMessage);
  }

  @Override
  public Set<MapVersionMappingInformation> getMapVersions(DiagnosticsHandler diagnosticsHandler) {
    return Collections.singleton(
        getMetadata(diagnosticsHandler).getMapVersion().toMapVersionMappingInformation());
  }

  @Override
  public RetracerImpl createRetracer(DiagnosticsHandler diagnosticsHandler) {
    MappingPartitionMetadataInternal metadata = getMetadata(diagnosticsHandler);
    if (!pendingKeys.isEmpty()) {
      prepare.prepare();
    }
    for (String pendingKey : pendingKeys) {
      try {
        byte[] suppliedPartition = partitionSupplier.get(pendingKey);
        // TODO(b/274735214): only expect a partition if have generated one for the key.
        if (suppliedPartition == null) {
          continue;
        }
        LineReader reader =
            new ProguardMapReaderWithFilteringInputBuffer(
                new ByteArrayInputStream(suppliedPartition), alwaysTrue(), true);
        classNameMapper =
            ClassNameMapper.mapperFromLineReaderWithFiltering(
                    reader, metadata.getMapVersion(), diagnosticsHandler, true, allowExperimental)
                .combine(this.classNameMapper);
      } catch (IOException e) {
        throw new InvalidMappingFileException(e);
      }
    }
    builtKeys.addAll(pendingKeys);
    pendingKeys.clear();
    if (classNameMapper == null) {
      classNameMapper = ClassNameMapper.builder().build();
    }
    return RetracerImpl.createInternal(
        MappingSupplierInternalImpl.createInternal(classNameMapper), diagnosticsHandler);
  }
}
