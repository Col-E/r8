// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.ClassNameMapper.MissingFileAction.MISSING_FILE_IS_ERROR;
import static com.android.tools.r8.utils.DescriptorUtils.descriptorToJavaType;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.BiMapContainer;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharSource;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ClassNameMapper implements ProguardMap {

  public enum MissingFileAction {
    MISSING_FILE_IS_EMPTY_MAP,
    MISSING_FILE_IS_ERROR
  }

  public static class Builder extends ProguardMap.Builder {

    private boolean buildPreamble = false;
    private boolean addVersionAsPreamble = false;
    private final List<String> preamble = new ArrayList<>();
    private final Map<String, ClassNamingForNameMapper.Builder> mapping = new HashMap<>();
    private final LinkedHashSet<MapVersionMappingInformation> mapVersions = new LinkedHashSet<>();
    private final Map<String, String> originalSourceFiles = new HashMap<>();

    @Override
    public ClassNamingForNameMapper.Builder classNamingBuilder(
        String renamedName, String originalName, Position position) {
      ClassNamingForNameMapper.Builder classNamingBuilder =
          ClassNamingForNameMapper.builder(renamedName, originalName, originalSourceFiles::put);
      mapping.put(renamedName, classNamingBuilder);
      return classNamingBuilder;
    }

    public Builder setBuildPreamble(boolean buildPreamble) {
      this.buildPreamble = buildPreamble;
      return this;
    }

    public Builder setAddVersionAsPreamble(boolean addVersionAsPreamble) {
      this.addVersionAsPreamble = addVersionAsPreamble;
      return this;
    }

    @Override
    public void addPreambleLine(String line) {
      if (buildPreamble) {
        preamble.add(line);
      }
    }

    public boolean hasMapping(String obfuscatedName) {
      return mapping.containsKey(obfuscatedName);
    }

    @Override
    public ClassNameMapper build() {
      return new ClassNameMapper(
          buildClassNameMappings(), mapVersions, originalSourceFiles, preamble);
    }

    private ImmutableMap<String, ClassNamingForNameMapper> buildClassNameMappings() {
      ImmutableMap.Builder<String, ClassNamingForNameMapper> builder = ImmutableMap.builder();
      mapping.forEach(
          (renamedName, valueBuilder) -> builder.put(renamedName, valueBuilder.build()));
      return builder.build();
    }

    @Override
    public ProguardMap.Builder setCurrentMapVersion(MapVersionMappingInformation mapVersion) {
      mapVersions.add(mapVersion);
      if (addVersionAsPreamble) {
        addPreambleLine("# " + mapVersion.serialize());
      }
      return this;
    }

    @Override
    ProguardMap.Builder addFileName(String originalName, String fileName) {
      originalSourceFiles.put(originalName, fileName);
      return this;
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ClassNameMapper mapperFromFile(Path path) throws IOException {
    return mapperFromFile(path, MISSING_FILE_IS_ERROR);
  }

  public static ClassNameMapper mapperFromFile(Path path, MissingFileAction missingFileAction)
      throws IOException {
    assert missingFileAction == MissingFileAction.MISSING_FILE_IS_EMPTY_MAP
        || missingFileAction == MISSING_FILE_IS_ERROR;
    if (missingFileAction == MissingFileAction.MISSING_FILE_IS_EMPTY_MAP
        && !path.toFile().exists()) {
      return mapperFromString("");
    }
    return mapperFromBufferedReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), null);
  }

  public static ClassNameMapper mapperFromString(String contents) throws IOException {
    return mapperFromBufferedReader(CharSource.wrap(contents).openBufferedStream(), null);
  }

  public static ClassNameMapper mapperFromStringWithPreamble(String contents) throws IOException {
    return mapperFromBufferedReader(
        CharSource.wrap(contents).openBufferedStream(), null, false, false, true);
  }

  public static ClassNameMapper mapperFromFileWithPreamble(
      Path path, MissingFileAction missingFileAction) throws Exception {
    assert missingFileAction == MissingFileAction.MISSING_FILE_IS_EMPTY_MAP
        || missingFileAction == MISSING_FILE_IS_ERROR;
    if (missingFileAction == MissingFileAction.MISSING_FILE_IS_EMPTY_MAP
        && !path.toFile().exists()) {
      return mapperFromString("");
    }
    return mapperFromBufferedReader(
        Files.newBufferedReader(path, StandardCharsets.UTF_8), null, false, false, true);
  }

  public static ClassNameMapper mapperFromString(
      String contents, DiagnosticsHandler diagnosticsHandler) throws IOException {
    return mapperFromBufferedReader(
        CharSource.wrap(contents).openBufferedStream(), diagnosticsHandler);
  }

  public static ClassNameMapper mapperFromString(
      String contents,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowEmptyMappedRanges,
      boolean allowExperimentalMapping,
      boolean buildPreamble)
      throws IOException {
    return mapperFromLineReaderWithFiltering(
        LineReader.fromBufferedReader(CharSource.wrap(contents).openBufferedStream()),
        MapVersion.MAP_VERSION_NONE,
        diagnosticsHandler,
        allowEmptyMappedRanges,
        allowExperimentalMapping,
        builder -> builder.setBuildPreamble(buildPreamble));
  }

  private static ClassNameMapper mapperFromBufferedReader(
      BufferedReader reader, DiagnosticsHandler diagnosticsHandler) throws IOException {
    return mapperFromBufferedReader(reader, diagnosticsHandler, false, false, false);
  }

  public static ClassNameMapper mapperFromBufferedReader(
      BufferedReader reader,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowEmptyMappedRanges,
      boolean allowExperimentalMapping,
      boolean buildPreamble)
      throws IOException {
    return mapperFromLineReaderWithFiltering(
        LineReader.fromBufferedReader(reader),
        MapVersion.MAP_VERSION_NONE,
        diagnosticsHandler,
        allowEmptyMappedRanges,
        allowExperimentalMapping,
        builder -> builder.setBuildPreamble(buildPreamble));
  }

  public static ClassNameMapper mapperFromLineReaderWithFiltering(
      LineReader reader,
      MapVersion mapVersion,
      DiagnosticsHandler diagnosticsHandler,
      boolean allowEmptyMappedRanges,
      boolean allowExperimentalMapping,
      Consumer<ClassNameMapper.Builder> builderConsumer)
      throws IOException {
    try (ProguardMapReader proguardReader =
        new ProguardMapReader(
            reader,
            diagnosticsHandler != null ? diagnosticsHandler : new Reporter(),
            allowEmptyMappedRanges,
            allowExperimentalMapping,
            mapVersion)) {
      ClassNameMapper.Builder builder = ClassNameMapper.builder();
      builderConsumer.accept(builder);
      proguardReader.parse(builder);
      return builder.build();
    }
  }

  private final ImmutableMap<String, ClassNamingForNameMapper> classNameMappings;
  private BiMapContainer<String, String> nameMapping;
  private final Map<Signature, Signature> signatureMap = new ConcurrentHashMap<>();
  private final LinkedHashSet<MapVersionMappingInformation> mapVersions;
  private final Map<String, String> originalSourceFiles;
  private List<String> preamble;

  private ClassNameMapper(
      ImmutableMap<String, ClassNamingForNameMapper> classNameMappings,
      LinkedHashSet<MapVersionMappingInformation> mapVersions,
      Map<String, String> originalSourceFiles,
      List<String> preamble) {
    this.classNameMappings = classNameMappings;
    this.mapVersions = mapVersions;
    this.originalSourceFiles = originalSourceFiles;
    this.preamble = preamble;
  }

  public Map<String, ClassNamingForNameMapper> getClassNameMappings() {
    return classNameMappings;
  }

  public List<String> getPreamble() {
    return preamble;
  }

  public Set<String> getObfuscatedPackages() {
    Set<String> packages = new HashSet<>();
    classNameMappings.forEach(
        (s, classNamingForNameMapper) ->
            packages.add(DescriptorUtils.getPackageNameFromTypeName(s)));
    return packages;
  }

  public void setPreamble(List<String> preamble) {
    this.preamble = preamble;
  }

  private Signature canonicalizeSignature(Signature signature) {
    Signature result = signatureMap.get(signature);
    if (result != null) {
      return result;
    }
    signatureMap.put(signature, signature);
    return signature;
  }

  public MethodSignature getRenamedMethodSignature(DexMethod method) {
    DexType[] parameters = method.proto.parameters.values;
    String[] parameterTypes = new String[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      parameterTypes[i] = deobfuscateType(parameters[i].toDescriptorString());
    }
    String returnType = deobfuscateType(method.proto.returnType.toDescriptorString());

    MethodSignature signature = new MethodSignature(method.name.toString(), returnType,
        parameterTypes);
    return (MethodSignature) canonicalizeSignature(signature);
  }

  public FieldSignature getRenamedFieldSignature(DexField field) {
    String type = deobfuscateType(field.type.toDescriptorString());
    return (FieldSignature) canonicalizeSignature(new FieldSignature(field.name.toString(), type));
  }

  /**
   * Deobfuscate a class name.
   *
   * <p>Returns the deobfuscated name if a mapping was found. Otherwise it returns the passed in
   * name.
   */
  public String deobfuscateClassName(String obfuscatedName) {
    ClassNamingForNameMapper classNaming = classNameMappings.get(obfuscatedName);
    if (classNaming == null) {
      return obfuscatedName;
    }
    return classNaming.originalName;
  }

  private String deobfuscateType(String asString) {
    return descriptorToJavaType(asString, this);
  }

  public String getSourceFile(String typeName) {
    return originalSourceFiles.get(typeName);
  }

  public ClassNameMapper combine(ClassNameMapper other) {
    if (other == null || other.isEmpty()) {
      return this;
    }
    if (this.isEmpty()) {
      return other;
    }
    ImmutableMap.Builder<String, ClassNamingForNameMapper> builder = ImmutableMap.builder();
    Map<String, ClassNamingForNameMapper> otherClassMappings = other.getClassNameMappings();
    for (Entry<String, ClassNamingForNameMapper> mappingEntry : classNameMappings.entrySet()) {
      ClassNamingForNameMapper otherMapping = otherClassMappings.get(mappingEntry.getKey());
      if (otherMapping == null) {
        builder.put(mappingEntry);
      } else {
        builder.put(mappingEntry.getKey(), mappingEntry.getValue().combine(otherMapping));
      }
    }
    otherClassMappings.forEach(
        (otherMappingClass, otherMapping) -> {
          // Collisions are handled above so only take non-existing mappings.
          if (!classNameMappings.containsKey(otherMappingClass)) {
            builder.put(otherMappingClass, otherMapping);
          }
        });
    LinkedHashSet<MapVersionMappingInformation> newMapVersions =
        new LinkedHashSet<>(getMapVersions());
    newMapVersions.addAll(other.getMapVersions());
    Map<String, String> newSourcesFiles = new HashMap<>(originalSourceFiles);
    // This will overwrite existing source files but the chance of that happening should be very
    // slim.
    newSourcesFiles.putAll(other.originalSourceFiles);

    List<String> newPreamble = Collections.emptyList();
    if (!this.preamble.isEmpty() || !other.preamble.isEmpty()) {
      newPreamble = new ArrayList<>();
      newPreamble.addAll(this.preamble);
      newPreamble.addAll(other.preamble);
    }
    return new ClassNameMapper(builder.build(), newMapVersions, newSourcesFiles, newPreamble);
  }

  @Override
  public boolean hasMapping(DexType type) {
    String decoded = descriptorToJavaType(type.descriptor.toString());
    return classNameMappings.containsKey(decoded);
  }

  @Override
  public ClassNamingForNameMapper getClassNaming(DexType type) {
    String decoded = descriptorToJavaType(type.descriptor.toString());
    return classNameMappings.get(decoded);
  }

  public ClassNamingForNameMapper getClassNaming(String obfuscatedName) {
    return classNameMappings.get(obfuscatedName);
  }

  public boolean isEmpty() {
    return classNameMappings.isEmpty() && preamble.isEmpty();
  }

  public ClassNameMapper sorted() {
    ImmutableMap.Builder<String, ClassNamingForNameMapper> builder = ImmutableMap.builder();
    builder.orderEntriesByValue(Comparator.comparing(x -> x.originalName));
    classNameMappings.forEach(builder::put);
    return new ClassNameMapper(builder.build(), mapVersions, originalSourceFiles, preamble);
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean verifyIsSorted() {
    Iterator<Entry<String, ClassNamingForNameMapper>> iterator =
        getClassNameMappings().entrySet().iterator();
    Iterator<Entry<String, ClassNamingForNameMapper>> sortedIterator =
        sorted().getClassNameMappings().entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, ClassNamingForNameMapper> entry = iterator.next();
      Entry<String, ClassNamingForNameMapper> otherEntry = sortedIterator.next();
      assert entry.getKey().equals(otherEntry.getKey());
      assert entry.getValue() == otherEntry.getValue();
    }
    return true;
  }

  public void write(ChainableStringConsumer consumer) {
    // Classes should be sorted by their original name such that the generated Proguard map is
    // deterministic (and easy to navigate manually).
    assert verifyIsSorted();
    for (ClassNamingForNameMapper naming : getClassNameMappings().values()) {
      naming.write(consumer);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    write(ChainableStringConsumer.wrap(builder::append));
    return builder.toString();
  }

  public BiMapContainer<String, String> getObfuscatedToOriginalMapping() {
    if (nameMapping == null) {
      ImmutableBiMap.Builder<String, String> builder = ImmutableBiMap.builder();
      for (String name : classNameMappings.keySet()) {
        builder.put(name, classNameMappings.get(name).originalName);
      }
      BiMap<String, String> classNameMappings = builder.build();
      nameMapping = new BiMapContainer<>(classNameMappings, classNameMappings.inverse());
    }
    return nameMapping;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof ClassNameMapper
        && classNameMappings.equals(((ClassNameMapper) o).classNameMappings);
  }

  @Override
  public int hashCode() {
    return 31 * classNameMappings.hashCode();
  }

  public String originalNameOf(IndexedDexItem item) {
    if (item instanceof DexField) {
      return lookupName(getRenamedFieldSignature((DexField) item), ((DexField) item).holder);
    } else if (item instanceof DexMethod) {
      return lookupName(getRenamedMethodSignature((DexMethod) item), ((DexMethod) item).holder);
    } else if (item instanceof DexType) {
      return descriptorToJavaType(((DexType) item).toDescriptorString(), this);
    } else {
      return item.toString();
    }
  }

  private String lookupName(Signature signature, DexType clazz) {
    String decoded = descriptorToJavaType(clazz.descriptor.toString());
    ClassNamingForNameMapper classNaming = getClassNaming(decoded);
    if (classNaming == null) {
      return decoded + " " + signature.toString();
    }
    MemberNaming memberNaming = classNaming.lookup(signature);
    if (memberNaming == null) {
      return classNaming.originalName + " " + signature;
    }
    return classNaming.originalName + " " + memberNaming.getOriginalSignature().toString();
  }

  public MethodSignature originalSignatureOf(DexMethod method) {
    String decoded = descriptorToJavaType(method.holder.descriptor.toString());
    MethodSignature memberSignature = getRenamedMethodSignature(method);
    ClassNaming classNaming = getClassNaming(decoded);
    if (classNaming == null) {
      return memberSignature;
    }
    MemberNaming memberNaming = classNaming.lookup(memberSignature);
    if (memberNaming == null) {
      return memberSignature;
    }
    return memberNaming.getOriginalSignature().asMethodSignature();
  }

  public FieldSignature originalSignatureOf(DexField field) {
    String decoded = descriptorToJavaType(field.holder.descriptor.toString());
    FieldSignature memberSignature = getRenamedFieldSignature(field);
    ClassNaming classNaming = getClassNaming(decoded);
    if (classNaming == null) {
      return memberSignature;
    }
    MemberNaming memberNaming = classNaming.lookup(memberSignature);
    if (memberNaming == null) {
      return memberSignature;
    }
    return memberNaming.getOriginalSignature().asFieldSignature();
  }

  public String originalNameOf(DexType clazz) {
    return deobfuscateType(clazz.descriptor.toString());
  }

  public Set<MapVersionMappingInformation> getMapVersions() {
    return mapVersions;
  }

  public MapVersionMappingInformation getFirstMapVersionInformation() {
    return mapVersions.isEmpty() ? null : mapVersions.iterator().next();
  }
}
