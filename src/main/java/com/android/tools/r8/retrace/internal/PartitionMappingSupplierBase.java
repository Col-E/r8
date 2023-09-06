// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Finishable;
import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.FinishedPartitionMappingCallback;
import com.android.tools.r8.retrace.InvalidMappingFileException;
import com.android.tools.r8.retrace.MappingPartitionFromKeySupplier;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.PrepareMappingPartitionsCallback;
import com.android.tools.r8.retrace.RegisterMappingPartitionCallback;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

public abstract class PartitionMappingSupplierBase<T extends PartitionMappingSupplierBase<T>>
    implements Finishable {

  private final RegisterMappingPartitionCallback registerCallback;
  private final PrepareMappingPartitionsCallback prepareCallback;

  @SuppressWarnings("UnusedVariable")
  private final FinishedPartitionMappingCallback finishedCallback;

  private final boolean allowExperimental;
  private final byte[] metadata;
  private final MapVersion fallbackMapVersion;

  private ClassNameMapper classNameMapper;
  private final Set<String> pendingKeys = new LinkedHashSet<>();
  private final Set<String> builtKeys = new HashSet<>();

  private final Box<MappingPartitionMetadataInternal> mappingPartitionMetadataCache = new Box<>();
  private final Box<Predicate<String>> typeNameCouldHavePartitionCache = new Box<>();

  protected PartitionMappingSupplierBase(
      RegisterMappingPartitionCallback registerCallback,
      PrepareMappingPartitionsCallback prepareCallback,
      FinishedPartitionMappingCallback finishedCallback,
      boolean allowExperimental,
      byte[] metadata,
      MapVersion fallbackMapVersion) {
    this.registerCallback = registerCallback;
    this.prepareCallback = prepareCallback;
    this.finishedCallback = finishedCallback;
    this.allowExperimental = allowExperimental;
    this.metadata = metadata;
    this.fallbackMapVersion = fallbackMapVersion;
  }

  public MappingPartitionMetadataInternal getMetadata(DiagnosticsHandler diagnosticsHandler) {
    if (mappingPartitionMetadataCache.isSet()) {
      return mappingPartitionMetadataCache.get();
    }
    synchronized (mappingPartitionMetadataCache) {
      if (mappingPartitionMetadataCache.isSet()) {
        return mappingPartitionMetadataCache.get();
      }
      MappingPartitionMetadataInternal data =
          MappingPartitionMetadataInternal.deserialize(
              CompatByteBuffer.wrapOrNull(metadata), fallbackMapVersion, diagnosticsHandler);
      mappingPartitionMetadataCache.set(data);
      return data;
    }
  }

  public T registerClassUse(DiagnosticsHandler diagnosticsHandler, ClassReference classReference) {
    // Check if the package name is registered before requesting the bytes for a partition.
    String typeName = classReference.getTypeName();
    if (isPotentialRetraceClass(diagnosticsHandler, typeName)) {
      return registerKeyUse(typeName);
    }
    return self();
  }

  private boolean isPotentialRetraceClass(DiagnosticsHandler diagnosticsHandler, String typeName) {
    if (typeNameCouldHavePartitionCache.isSet()) {
      return typeNameCouldHavePartitionCache.get().test(typeName);
    }
    synchronized (typeNameCouldHavePartitionCache) {
      if (typeNameCouldHavePartitionCache.isSet()) {
        return typeNameCouldHavePartitionCache.get().test(typeName);
      }
      Predicate<String> typeNameCouldHavePartitionPredicate =
          getPartitionPredicate(getPackagesWithClasses(diagnosticsHandler));
      typeNameCouldHavePartitionCache.set(typeNameCouldHavePartitionPredicate);
      return typeNameCouldHavePartitionPredicate.test(typeName);
    }
  }

  private Predicate<String> getPartitionPredicate(Set<String> packagesWithClasses) {
    return name ->
        packagesWithClasses == null
            || packagesWithClasses.contains(DescriptorUtils.getPackageNameFromTypeName(name));
  }

  public T registerMethodUse(
      DiagnosticsHandler diagnosticsHandler, MethodReference methodReference) {
    return registerClassUse(diagnosticsHandler, methodReference.getHolderClass());
  }

  public T registerFieldUse(DiagnosticsHandler diagnosticsHandler, FieldReference fieldReference) {
    return registerClassUse(diagnosticsHandler, fieldReference.getHolderClass());
  }

  public T registerKeyUse(String key) {
    if (!builtKeys.contains(key) && pendingKeys.add(key)) {
      registerCallback.register(key);
    }
    return self();
  }

  private Set<String> getPackagesWithClasses(DiagnosticsHandler diagnosticsHandler) {
    MappingPartitionMetadataInternal metadata = getMetadata(diagnosticsHandler);
    if (metadata == null || !metadata.canGetAdditionalInfo()) {
      return null;
    }
    MetadataAdditionalInfo additionalInfo = metadata.getAdditionalInfo();
    if (!additionalInfo.hasObfuscatedPackages()) {
      return null;
    }
    return additionalInfo.getObfuscatedPackages();
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

  public PartitionMappingSupplier getPartitionMappingSupplier() {
    return null;
  }

  protected RetracerImpl createRetracerFromPartitionSupplier(
      DiagnosticsHandler diagnosticsHandler, MappingPartitionFromKeySupplier partitionSupplier) {
    if (!pendingKeys.isEmpty()) {
      prepareCallback.prepare();
    }
    for (String pendingKey : pendingKeys) {
      try {
        byte[] suppliedPartition = partitionSupplier.get(pendingKey);
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

  @Override
  public void finished(DiagnosticsHandler handler) {
    finishedCallback.finished(handler);
  }

  public abstract T self();
}
