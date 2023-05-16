// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.LineReader;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.PartitionFileNameInformation;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapPartitionerBuilder;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal.ObfuscatedTypeNameAsKeyMetadata;
import com.android.tools.r8.retrace.internal.MappingPartitionMetadataInternal.ObfuscatedTypeNameAsKeyMetadataWithPartitionNames;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringMappedBuffer;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TriConsumer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ProguardMapPartitionerOnClassNameToText implements ProguardMapPartitioner {

  private final ProguardMapProducer proguardMapProducer;
  private final Consumer<MappingPartition> mappingPartitionConsumer;
  private final DiagnosticsHandler diagnosticsHandler;
  private final boolean allowEmptyMappedRanges;
  private final boolean allowExperimentalMapping;
  private final MappingPartitionKeyStrategy mappingPartitionKeyStrategy;

  private ProguardMapPartitionerOnClassNameToText(
      ProguardMapProducer proguardMapProducer,
      Consumer<MappingPartition> mappingPartitionConsumer,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowEmptyMappedRanges,
      boolean allowExperimentalMapping,
      MappingPartitionKeyStrategy mappingPartitionKeyStrategy) {
    this.proguardMapProducer = proguardMapProducer;
    this.mappingPartitionConsumer = mappingPartitionConsumer;
    this.diagnosticsHandler = diagnosticsHandler;
    this.allowEmptyMappedRanges = allowEmptyMappedRanges;
    this.allowExperimentalMapping = allowExperimentalMapping;
    this.mappingPartitionKeyStrategy = mappingPartitionKeyStrategy;
  }

  private ClassNameMapper getPartitionsFromProguardMapProducer(
      TriConsumer<ClassNameMapper, ClassNamingForNameMapper, String> consumer) throws IOException {
    if (proguardMapProducer instanceof ProguardMapProducerInternal) {
      return getPartitionsFromInternalProguardMapProducer(consumer);
    } else {
      return getPartitionsFromStringBackedProguardMapProducer(consumer);
    }
  }

  private ClassNameMapper getPartitionsFromInternalProguardMapProducer(
      TriConsumer<ClassNameMapper, ClassNamingForNameMapper, String> consumer) {
    ClassNameMapper classNameMapper =
        ((ProguardMapProducerInternal) proguardMapProducer).getClassNameMapper();
    classNameMapper
        .getClassNameMappings()
        .forEach(
            (key, mappingForClass) ->
                consumer.accept(classNameMapper, mappingForClass, mappingForClass.toString()));
    return classNameMapper;
  }

  private ClassNameMapper getPartitionsFromStringBackedProguardMapProducer(
      TriConsumer<ClassNameMapper, ClassNamingForNameMapper, String> consumer) throws IOException {
    PartitionLineReader reader =
        new PartitionLineReader(
            proguardMapProducer.isFileBacked()
                ? new ProguardMapReaderWithFilteringMappedBuffer(
                    proguardMapProducer.getPath(), alwaysTrue(), true)
                : new ProguardMapReaderWithFilteringInputBuffer(
                    proguardMapProducer.get(), alwaysTrue(), true));
    // Produce a global mapper to read all from the reader but also to capture all source file
    // mappings.
    ClassNameMapper classMapper =
        ClassNameMapper.mapperFromLineReaderWithFiltering(
            reader,
            MapVersion.MAP_VERSION_UNKNOWN,
            diagnosticsHandler,
            allowEmptyMappedRanges,
            allowExperimentalMapping,
            builder -> builder.setBuildPreamble(true).setAddVersionAsPreamble(true));
    reader.forEachClassMapping(
        (classMapping, entries) -> {
          try {
            String payload = StringUtils.join("\n", entries);
            ClassNameMapper classNameMapper =
                ClassNameMapper.mapperFromString(
                    payload, null, allowEmptyMappedRanges, allowExperimentalMapping, false);
            Map<String, ClassNamingForNameMapper> classNameMappings =
                classNameMapper.getClassNameMappings();
            if (classNameMappings.size() != 1) {
              diagnosticsHandler.error(
                  new StringDiagnostic("Multiple class names in payload\n: " + payload));
              return;
            }
            consumer.accept(classMapper, classNameMappings.values().iterator().next(), payload);
          } catch (IOException e) {
            diagnosticsHandler.error(new ExceptionDiagnostic(e));
          }
        });
    return classMapper;
  }

  @Override
  public MappingPartitionMetadata run() throws IOException {
    HashSet<String> keys = new LinkedHashSet<>();
    // We can then iterate over all sections.
    ClassNameMapper classMapper =
        getPartitionsFromProguardMapProducer(
            (classNameMapper, classNamingForNameMapper, payload) -> {
              Set<String> seenMappings = new HashSet<>();
              PartitionFileNameInformation.Builder partitionFileNameBuilder =
                  PartitionFileNameInformation.builder();
              classNamingForNameMapper.visitAllFullyQualifiedReferences(
                  holder -> {
                    if (classNameMapper.getSourceFile(holder) != null && seenMappings.add(holder)) {
                      partitionFileNameBuilder.addClassToFileNameMapping(
                          holder, classNameMapper.getSourceFile(holder));
                    }
                  });
              StringBuilder payloadBuilder = new StringBuilder();
              if (!partitionFileNameBuilder.isEmpty()) {
                payloadBuilder
                    .append("# ")
                    .append(partitionFileNameBuilder.build().serialize())
                    .append("\n");
              }
              payloadBuilder.append(payload);
              mappingPartitionConsumer.accept(
                  new MappingPartitionImpl(
                      classNamingForNameMapper.renamedName,
                      payloadBuilder.toString().getBytes(StandardCharsets.UTF_8)));
              keys.add(classNamingForNameMapper.renamedName);
            });
    MapVersion mapVersion = MapVersion.MAP_VERSION_UNKNOWN;
    MapVersionMappingInformation mapVersionInfo = classMapper.getFirstMapVersionInformation();
    if (mapVersionInfo != null) {
      mapVersion = mapVersionInfo.getMapVersion();
    }
    if (mappingPartitionKeyStrategy == MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY) {
      return ObfuscatedTypeNameAsKeyMetadata.create(mapVersion);
    } else if (mappingPartitionKeyStrategy
        == MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS) {
      return ObfuscatedTypeNameAsKeyMetadataWithPartitionNames.create(
          mapVersion,
          MetadataPartitionCollection.create(keys),
          MetadataAdditionalInfo.create(
              classMapper.getPreamble(), classMapper.getObfuscatedPackages()));
    } else {
      RetracePartitionException retraceError =
          new RetracePartitionException("Unknown mapping partitioning strategy");
      diagnosticsHandler.error(new ExceptionDiagnostic(retraceError));
      throw retraceError;
    }
  }

  public static class PartitionLineReader implements LineReader {

    private final ProguardMapReaderWithFiltering lineReader;
    private final Map<String, List<String>> readSections = new LinkedHashMap<>();
    private List<String> currentList;

    public PartitionLineReader(ProguardMapReaderWithFiltering lineReader) {
      this.lineReader = lineReader;
      currentList = new ArrayList<>();
    }

    @Override
    public String readLine() throws IOException {
      String readLine = lineReader.readLine();
      if (readLine == null) {
        return null;
      }
      if (lineReader.isClassMapping()) {
        currentList = new ArrayList<>();
        readSections.put(readLine, currentList);
      }
      currentList.add(readLine);
      return readLine;
    }

    @Override
    public void close() throws IOException {
      lineReader.close();
    }

    public void forEachClassMapping(BiConsumer<String, List<String>> consumer) {
      readSections.forEach(consumer);
    }
  }

  public static class ProguardMapPartitionerBuilderImpl
      implements ProguardMapPartitionerBuilder<
          ProguardMapPartitionerBuilderImpl, ProguardMapPartitionerOnClassNameToText> {

    protected ProguardMapProducer proguardMapProducer;
    protected Consumer<MappingPartition> mappingPartitionConsumer;
    protected final DiagnosticsHandler diagnosticsHandler;
    protected boolean allowEmptyMappedRanges = false;
    protected boolean allowExperimentalMapping = false;

    public ProguardMapPartitionerBuilderImpl(DiagnosticsHandler diagnosticsHandler) {
      this.diagnosticsHandler = diagnosticsHandler;
    }

    @Override
    public ProguardMapPartitionerBuilderImpl setPartitionConsumer(
        Consumer<MappingPartition> consumer) {
      this.mappingPartitionConsumer = consumer;
      return this;
    }

    @Override
    public ProguardMapPartitionerBuilderImpl setProguardMapProducer(
        ProguardMapProducer proguardMapProducer) {
      this.proguardMapProducer = proguardMapProducer;
      return this;
    }

    @Override
    public ProguardMapPartitionerBuilderImpl setAllowEmptyMappedRanges(
        boolean allowEmptyMappedRanges) {
      this.allowEmptyMappedRanges = allowEmptyMappedRanges;
      return this;
    }

    @Override
    public ProguardMapPartitionerBuilderImpl setAllowExperimentalMapping(
        boolean allowExperimentalMapping) {
      this.allowExperimentalMapping = allowExperimentalMapping;
      return this;
    }

    @Override
    public ProguardMapPartitionerOnClassNameToText build() {
      return new ProguardMapPartitionerOnClassNameToText(
          proguardMapProducer,
          mappingPartitionConsumer,
          diagnosticsHandler,
          allowEmptyMappedRanges,
          allowExperimentalMapping,
          MappingPartitionKeyStrategy.getDefaultStrategy());
    }
  }

  // This class should not be exposed to clients and is only used from tests to control the
  // partitioning strategy.
  public static class ProguardMapPartitionerBuilderImplInternal
      extends ProguardMapPartitionerBuilderImpl {

    private MappingPartitionKeyStrategy mappingPartitionKeyStrategy =
        MappingPartitionKeyStrategy.OBFUSCATED_TYPE_NAME_AS_KEY_WITH_PARTITIONS;

    public ProguardMapPartitionerBuilderImplInternal(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    public ProguardMapPartitionerBuilderImplInternal setMappingPartitionKeyStrategy(
        MappingPartitionKeyStrategy mappingPartitionKeyStrategy) {
      this.mappingPartitionKeyStrategy = mappingPartitionKeyStrategy;
      return this;
    }

    @Override
    public ProguardMapPartitionerOnClassNameToText build() {
      return new ProguardMapPartitionerOnClassNameToText(
          proguardMapProducer,
          mappingPartitionConsumer,
          diagnosticsHandler,
          allowEmptyMappedRanges,
          allowExperimentalMapping,
          mappingPartitionKeyStrategy);
    }
  }
}
