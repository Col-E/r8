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
import com.android.tools.r8.naming.mappinginformation.FileNameInformation;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.retrace.MappingPartition;
import com.android.tools.r8.retrace.MappingPartitionMetadata;
import com.android.tools.r8.retrace.ProguardMapPartitioner;
import com.android.tools.r8.retrace.ProguardMapPartitionerBuilder;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringInputBuffer;
import com.android.tools.r8.retrace.internal.ProguardMapReaderWithFiltering.ProguardMapReaderWithFilteringMappedBuffer;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class ProguardMapPartitionerOnClassNameToText implements ProguardMapPartitioner {

  private final ProguardMapProducer proguardMapProducer;
  private final Consumer<MappingPartition> mappingPartitionConsumer;
  private final DiagnosticsHandler diagnosticsHandler;
  private final boolean allowEmptyMappedRanges;
  private final boolean allowExperimentalMapping;

  private ProguardMapPartitionerOnClassNameToText(
      ProguardMapProducer proguardMapProducer,
      Consumer<MappingPartition> mappingPartitionConsumer,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowEmptyMappedRanges,
      boolean allowExperimentalMapping) {
    this.proguardMapProducer = proguardMapProducer;
    this.mappingPartitionConsumer = mappingPartitionConsumer;
    this.diagnosticsHandler = diagnosticsHandler;
    this.allowEmptyMappedRanges = allowEmptyMappedRanges;
    this.allowExperimentalMapping = allowExperimentalMapping;
  }

  @Override
  public MappingPartitionMetadata run() throws IOException {
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
            reader, MapVersion.MAP_VERSION_UNKNOWN, diagnosticsHandler, true, true);
    // We can then iterate over all sections.
    reader.forEachClassMapping(
        (classMapping, entries) -> {
          try {
            String payload = StringUtils.join("\n", entries);
            ClassNameMapper classNameMapper =
                ClassNameMapper.mapperFromString(
                    payload, null, allowEmptyMappedRanges, allowExperimentalMapping, false);
            if (classNameMapper.getClassNameMappings().size() != 1) {
              diagnosticsHandler.error(
                  new StringDiagnostic("Multiple class names in payload\n: " + payload));
              return;
            }
            Entry<String, ClassNamingForNameMapper> currentClassMapping =
                classNameMapper.getClassNameMappings().entrySet().iterator().next();
            ClassNamingForNameMapper value = currentClassMapping.getValue();
            Set<String> seenMappings = new HashSet<>();
            StringBuilder payloadWithClassReferences = new StringBuilder();
            value.visitAllFullyQualifiedReferences(
                holder -> {
                  if (seenMappings.add(holder)) {
                    payloadWithClassReferences.append(
                        getSourceFileMapping(holder, classMapper.getSourceFile(holder)));
                  }
                });
            payloadWithClassReferences.append(payload);
            mappingPartitionConsumer.accept(
                new MappingPartitionImpl(
                    currentClassMapping.getKey(),
                    payloadWithClassReferences.toString().getBytes(StandardCharsets.UTF_8)));
          } catch (IOException e) {
            diagnosticsHandler.error(new ExceptionDiagnostic(e));
          }
        });
    MapVersion mapVersion = MapVersion.MAP_VERSION_UNKNOWN;
    MapVersionMappingInformation mapVersionInfo = classMapper.getFirstMapVersionInformation();
    if (mapVersionInfo != null) {
      mapVersion = mapVersionInfo.getMapVersion();
    }
    return MappingPartitionMetadataInternal.obfuscatedTypeNameAsKey(mapVersion);
  }

  private String getSourceFileMapping(String className, String sourceFile) {
    if (sourceFile == null) {
      return "";
    }
    return className
        + " -> "
        + className
        + ":"
        + "\n  # "
        + FileNameInformation.build(sourceFile).serialize()
        + "\n";
  }

  public static class PartitionLineReader implements LineReader {

    private final ProguardMapReaderWithFiltering lineReader;
    private final Map<String, List<String>> readSections = new LinkedHashMap<>();
    private final List<String> preamble;
    private List<String> currentList;

    public PartitionLineReader(ProguardMapReaderWithFiltering lineReader) {
      this.lineReader = lineReader;
      currentList = new ArrayList<>();
      preamble = currentList;
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

    private ProguardMapProducer proguardMapProducer;
    private Consumer<MappingPartition> mappingPartitionConsumer;
    private final DiagnosticsHandler diagnosticsHandler;
    private boolean allowEmptyMappedRanges = false;
    private boolean allowExperimentalMapping = false;

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
          allowExperimentalMapping);
    }
  }
}
