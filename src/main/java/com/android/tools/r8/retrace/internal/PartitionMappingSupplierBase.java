// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.MappingPartitionFromKeySupplier;
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

public abstract class PartitionMappingSupplierBase<T extends PartitionMappingSupplierBase<T>> {

  private final RegisterMappingPartitionCallback registerCallback;
  private final PrepareMappingPartitionsCallback prepareCallback;
  private final boolean allowExperimental;
  private final byte[] metadata;
  private final MapVersion fallbackMapVersion;

  private ClassNameMapper classNameMapper;
  private final Set<String> pendingKeys = new LinkedHashSet<>();
  private final Set<String> builtKeys = new HashSet<>();

  private MappingPartitionMetadataInternal mappingPartitionMetadataCache;

  protected PartitionMappingSupplierBase(
      RegisterMappingPartitionCallback registerCallback,
      PrepareMappingPartitionsCallback prepareCallback,
      boolean allowExperimental,
      byte[] metadata,
      MapVersion fallbackMapVersion) {
    this.registerCallback = registerCallback;
    this.prepareCallback = prepareCallback;
    this.allowExperimental = allowExperimental;
    this.metadata = metadata;
    this.fallbackMapVersion = fallbackMapVersion;
  }

  protected MappingPartitionMetadataInternal getMetadata(DiagnosticsHandler diagnosticsHandler) {
    if (mappingPartitionMetadataCache != null) {
      return mappingPartitionMetadataCache;
    }
    return mappingPartitionMetadataCache =
        MappingPartitionMetadataInternal.deserialize(
            CompatByteBuffer.wrapOrNull(metadata), fallbackMapVersion, diagnosticsHandler);
  }

  public T registerClassUse(DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    return registerKeyUse(classReference.getTypeName());
  }

  public T registerMethodUse(
      DiagnosticsHandler diagnosticsHandler, MethodReference methodReference) {
    return registerClassUse(diagnosticsHandler, methodReference.getHolderClass());
  }

  public T registerFieldUse(DiagnosticsHandler diagnosticsHandler, FieldReference fieldReference) {
    return registerClassUse(diagnosticsHandler, fieldReference.getHolderClass());
  }

  public T registerKeyUse(String key) {
    // TODO(b/274735214): only call the register partition if we have a partition for it.
    if (!builtKeys.contains(key) && pendingKeys.add(key)) {
      registerCallback.register(key);
    }
    return self();
  }

  public void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler) {
    String errorMessage = "Cannot verify map file hash for partitions";
    diagnosticsHandler.error(new StringDiagnostic(errorMessage));
    throw new RuntimeException(errorMessage);
  }

  public Set<MapVersionMappingInformation> getMapVersions(DiagnosticsHandler diagnosticsHandler) {
    return Collections.singleton(
        getMetadata(diagnosticsHandler).getMapVersion().toMapVersionMappingInformation());
  }

  protected RetracerImpl createRetracerFromPartitionSupplier(
      DiagnosticsHandler diagnosticsHandler, MappingPartitionFromKeySupplier partitionSupplier) {
    if (!pendingKeys.isEmpty()) {
      prepareCallback.prepare();
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
                    reader,
                    getMetadata(diagnosticsHandler).getMapVersion(),
                    diagnosticsHandler,
                    true,
                    allowExperimental,
                    builder -> builder.setBuildPreamble(true))
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

  public abstract T self();
}
