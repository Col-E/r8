// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.structural.DefaultHashingVisitor;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.StructuralItem;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

public class AndroidApiHashingDatabaseBuilderGenerator extends TestBase {

  /**
   * Generate the information needed for looking up api level of references in the android.jar. This
   * method will generate three different files and store them in the passed paths. pathToIndices
   * will be an int array with hashcode entries for each DexReference. The pathToApiLevels is a byte
   * array with a byte describing the api level that the index in pathToIndices has. To ensure that
   * this lookup work the generate algorithm tracks all colliding hash codes such and stores them in
   * another format. The indices map is populated with all colliding entries and a -1 is inserted
   * for the api level.
   */
  public static void generate(
      List<ParsedApiClass> apiClasses,
      Path pathToIndices,
      Path pathToApiLevels,
      Path ambiguousDefinitions)
      throws Exception {
    Map<ClassReference, Map<DexMethod, AndroidApiLevel>> methodMap = new HashMap<>();
    Map<ClassReference, Map<DexField, AndroidApiLevel>> fieldMap = new HashMap<>();
    Map<ClassReference, ParsedApiClass> lookupMap = new HashMap<>();
    DexItemFactory factory = new DexItemFactory();

    Map<Integer, AndroidApiLevel> apiLevelMap = new HashMap<>();
    Map<Integer, Pair<DexReference, AndroidApiLevel>> reverseMap = new HashMap<>();
    Map<AndroidApiLevel, Set<DexReference>> ambiguousMap = new HashMap<>();
    // Populate maps for faster lookup.
    for (ParsedApiClass apiClass : apiClasses) {
      DexType type = factory.createType(apiClass.getClassReference().getDescriptor());
      AndroidApiLevel existing = apiLevelMap.put(type.hashCode(), apiClass.getApiLevel());
      assert existing == null;
      reverseMap.put(type.hashCode(), Pair.create(type, apiClass.getApiLevel()));
    }

    for (ParsedApiClass apiClass : apiClasses) {
      Map<DexMethod, AndroidApiLevel> methodsForApiClass = new HashMap<>();
      apiClass.visitMethodReferences(
          (apiLevel, methods) -> {
            methods.forEach(
                method -> methodsForApiClass.put(factory.createMethod(method), apiLevel));
          });
      Map<DexField, AndroidApiLevel> fieldsForApiClass = new HashMap<>();
      apiClass.visitFieldReferences(
          (apiLevel, fields) -> {
            fields.forEach(field -> fieldsForApiClass.put(factory.createField(field), apiLevel));
          });
      methodMap.put(apiClass.getClassReference(), methodsForApiClass);
      fieldMap.put(apiClass.getClassReference(), fieldsForApiClass);
      lookupMap.put(apiClass.getClassReference(), apiClass);
    }

    BiConsumer<DexReference, AndroidApiLevel> addConsumer =
        addReferenceToMaps(apiLevelMap, reverseMap, ambiguousMap);

    for (ParsedApiClass apiClass : apiClasses) {
      computeAllReferencesInHierarchy(
              lookupMap,
              factory,
              factory.createType(apiClass.getClassReference().getDescriptor()),
              apiClass,
              AndroidApiLevel.B,
              new IdentityHashMap<>())
          .forEach(addConsumer);
    }

    int[] indices = new int[apiLevelMap.size()];
    byte[] apiLevel = new byte[apiLevelMap.size()];
    ArrayList<Integer> integers = new ArrayList<>(apiLevelMap.keySet());
    for (int i = 0; i < integers.size(); i++) {
      indices[i] = integers.get(i);
      AndroidApiLevel androidApiLevel = apiLevelMap.get(integers.get(i));
      apiLevel[i] =
          (byte) (androidApiLevel == AndroidApiLevel.NOT_SET ? -1 : androidApiLevel.getLevel());
    }

    try (FileOutputStream fileOutputStream = new FileOutputStream(pathToIndices.toFile());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
      objectOutputStream.writeObject(indices);
    }

    try (FileOutputStream fileOutputStream = new FileOutputStream(pathToApiLevels.toFile());
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream)) {
      objectOutputStream.writeObject(apiLevel);
    }

    String ambiguousMapSerialized = serializeAmbiguousMap(ambiguousMap);
    Files.write(ambiguousDefinitions, ambiguousMapSerialized.getBytes(StandardCharsets.UTF_8));
  }

  /** This will serialize a collection of DexReferences with the api level they correspond to. */
  private static String serializeAmbiguousMap(
      Map<AndroidApiLevel, Set<DexReference>> ambiguousMap) {
    Set<String> seen = new HashSet<>();
    StringBuilder sb = new StringBuilder();
    ambiguousMap.forEach(
        (apiLevel, references) -> {
          references.forEach(
              reference -> {
                HasherWrapper defaultHasher = AndroidApiLevelHashingDatabaseImpl.getDefaultHasher();
                reference.accept(
                    type -> DefaultHashingVisitor.run(type, defaultHasher, DexType::acceptHashing),
                    field ->
                        DefaultHashingVisitor.run(
                            field, defaultHasher, StructuralItem::acceptHashing),
                    method ->
                        DefaultHashingVisitor.run(
                            method, defaultHasher, StructuralItem::acceptHashing));
                String referenceHash = defaultHasher.hash().toString();
                if (!seen.add(referenceHash)) {
                  throw new RuntimeException(
                      "More than one item with key: <"
                          + referenceHash
                          + ">. The choice of key encoding will need to change to generate a valid"
                          + " api database");
                }
                sb.append(referenceHash).append(":").append(apiLevel.getLevel()).append("\n");
              });
        });
    return sb.toString();
  }

  private static BiConsumer<DexReference, AndroidApiLevel> addReferenceToMaps(
      Map<Integer, AndroidApiLevel> apiLevelMap,
      Map<Integer, Pair<DexReference, AndroidApiLevel>> reverseMap,
      Map<AndroidApiLevel, Set<DexReference>> ambiguousMap) {
    return ((reference, apiLevel) -> {
      AndroidApiLevel existingMethod = apiLevelMap.put(reference.hashCode(), apiLevel);
      if (existingMethod != null) {
        apiLevelMap.put(reference.hashCode(), AndroidApiLevel.NOT_SET);
        Pair<DexReference, AndroidApiLevel> existingPair = reverseMap.get(reference.hashCode());
        addAmbiguousEntry(existingPair.getSecond(), existingPair.getFirst(), ambiguousMap);
        addAmbiguousEntry(apiLevel, reference, ambiguousMap);
      } else {
        reverseMap.put(reference.hashCode(), Pair.create(reference, apiLevel));
      }
    });
  }

  private static void addAmbiguousEntry(
      AndroidApiLevel apiLevel,
      DexReference reference,
      Map<AndroidApiLevel, Set<DexReference>> ambiguousMap) {
    ambiguousMap.computeIfAbsent(apiLevel, ignored -> new HashSet<>()).add(reference);
  }

  @SuppressWarnings("unchecked")
  private static <T extends DexMember<?, ?>>
      Map<T, AndroidApiLevel> computeAllReferencesInHierarchy(
          Map<ClassReference, ParsedApiClass> lookupMap,
          DexItemFactory factory,
          DexType holder,
          ParsedApiClass apiClass,
          AndroidApiLevel linkLevel,
          Map<T, AndroidApiLevel> additionMap) {
    if (!apiClass.getClassReference().getDescriptor().equals(factory.objectDescriptor.toString())) {
      apiClass.visitMethodReferences(
          (apiLevel, methodReferences) -> {
            methodReferences.forEach(
                methodReference -> {
                  T member = (T) factory.createMethod(methodReference).withHolder(holder, factory);
                  addIfNewOrApiLevelIsLower(linkLevel, additionMap, apiLevel, member);
                });
          });
      apiClass.visitFieldReferences(
          (apiLevel, fieldReferences) -> {
            fieldReferences.forEach(
                fieldReference -> {
                  T member = (T) factory.createField(fieldReference).withHolder(holder, factory);
                  addIfNewOrApiLevelIsLower(linkLevel, additionMap, apiLevel, member);
                });
          });
      apiClass.visitSuperType(
          (superType, apiLevel) -> {
            computeAllReferencesInHierarchy(
                lookupMap,
                factory,
                holder,
                lookupMap.get(superType),
                linkLevel.max(apiLevel),
                additionMap);
          });
      apiClass.visitInterface(
          (iFace, apiLevel) -> {
            computeAllReferencesInHierarchy(
                lookupMap,
                factory,
                holder,
                lookupMap.get(iFace),
                linkLevel.max(apiLevel),
                additionMap);
          });
    }
    return additionMap;
  }

  private static <T extends DexMember<?, ?>> void addIfNewOrApiLevelIsLower(
      AndroidApiLevel linkLevel,
      Map<T, AndroidApiLevel> additionMap,
      AndroidApiLevel apiLevel,
      T member) {
    AndroidApiLevel currentApiLevel = apiLevel.max(linkLevel);
    AndroidApiLevel existingApiLevel = additionMap.get(member);
    if (existingApiLevel == null || currentApiLevel.isLessThanOrEqualTo(existingApiLevel)) {
      additionMap.put(member, currentApiLevel);
    }
  }
}
