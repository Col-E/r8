// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.androidapi.AndroidApiDataAccess.constantPoolHash;
import static com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl.getNonExistingDescriptor;
import static com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl.getUniqueDescriptorForReference;
import static com.android.tools.r8.lightir.ByteUtils.isU2;
import static com.android.tools.r8.lightir.ByteUtils.setBitAtIndex;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import com.android.tools.r8.androidapi.GenerateCovariantReturnTypeMethodsTest.CovariantMethodsInJarResult;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class AndroidApiHashingDatabaseBuilderGenerator extends TestBase {

  /**
   * Generate the information needed for looking up api level of references in the android.jar. This
   * method will generate one single database file where the format is as follows (uX is X number of
   * unsigned bytes):
   *
   * <pre>
   * constant_pool_size: u4
   * constant_pool:      [constant_pool_size * payload_entry]
   * constant_pool_map:  [0..max_hash(DexString) * payload_entry]
   * api_map:            [0..max_hash(DexReference) * payload_entry]
   * payload             raw data.
   *
   * payload_entry: u4:relative_offset_from_payload_start + u2:length
   * </pre>
   *
   * For hash_definitions and entries see {@code AndroidApiDataAccess}.
   */
  public static void generate(
      List<ParsedApiClass> apiClasses, Path pathToApiLevels, AndroidApiLevel androidJarApiLevel)
      throws Exception {
    Map<ClassReference, Map<DexMethod, AndroidApiLevel>> methodMap = new HashMap<>();
    Map<ClassReference, Map<DexField, AndroidApiLevel>> fieldMap = new HashMap<>();
    Map<ClassReference, ParsedApiClass> lookupMap = new HashMap<>();

    Map<DexReference, AndroidApiLevel> referenceMap = new HashMap<>();

    Path androidJar = ToolHelper.getAndroidJar(androidJarApiLevel);
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(AndroidApp.builder().addLibraryFile(androidJar).build());
    DexItemFactory factory = appView.dexItemFactory();

    CovariantMethodsInJarResult covariantMethodsInJar = CovariantMethodsInJarResult.create();

    for (ParsedApiClass apiClass : apiClasses) {
      Map<DexMethod, AndroidApiLevel> methodsForApiClass = new HashMap<>();
      apiClass.visitMethodReferences(
          (apiLevel, methods) ->
              methods.forEach(
                  method -> methodsForApiClass.put(factory.createMethod(method), apiLevel)));
      covariantMethodsInJar.visitCovariantMethodsForHolder(
          apiClass.getClassReference(),
          methodReferenceWithApiLevel -> {
            DexMethod method =
                factory.createMethod(methodReferenceWithApiLevel.getMethodReference());
            if (!methodsForApiClass.containsKey(method)) {
              apiClass.amendCovariantMethod(
                  methodReferenceWithApiLevel.getMethodReference(),
                  methodReferenceWithApiLevel.getApiLevel());
              methodsForApiClass.put(method, methodReferenceWithApiLevel.getApiLevel());
            }
          });
      Map<DexField, AndroidApiLevel> fieldsForApiClass = new HashMap<>();
      apiClass.visitFieldReferences(
          (apiLevel, fields) ->
              fields.forEach(field -> fieldsForApiClass.put(factory.createField(field), apiLevel)));
      methodMap.put(apiClass.getClassReference(), methodsForApiClass);
      fieldMap.put(apiClass.getClassReference(), fieldsForApiClass);
      lookupMap.put(apiClass.getClassReference(), apiClass);

      referenceMap.put(
          factory.createType(apiClass.getClassReference().getDescriptor()), apiClass.getApiLevel());
    }

    for (ParsedApiClass apiClass : apiClasses) {
      computeAllReferencesInHierarchy(
          lookupMap,
          factory,
          factory.createType(apiClass.getClassReference().getDescriptor()),
          apiClass,
          AndroidApiLevel.B,
          referenceMap);
    }

    assert ensureAllPublicMethodsAreMapped(
        appView, lookupMap, methodMap, fieldMap, referenceMap, androidJar);

    try (FileOutputStream fileOutputStream = new FileOutputStream(pathToApiLevels.toFile())) {
      DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
      generateDatabase(referenceMap, dataOutputStream);
    }
  }

  private static boolean ensureAllPublicMethodsAreMapped(
      AppView<AppInfoWithClassHierarchy> appView,
      Map<ClassReference, ParsedApiClass> lookupMap,
      Map<ClassReference, Map<DexMethod, AndroidApiLevel>> methodMap,
      Map<ClassReference, Map<DexField, AndroidApiLevel>> fieldMap,
      Map<DexReference, AndroidApiLevel> referenceMap,
      Path androidJar) {
    Map<DexType, String> missingMemberInformation = new IdentityHashMap<>();
    DexItemFactory factory = appView.dexItemFactory();
    for (DexLibraryClass clazz : appView.app().asDirect().libraryClasses()) {
      ParsedApiClass parsedApiClass = lookupMap.get(clazz.getClassReference());
      if (parsedApiClass == null) {
        if (clazz.isPublic()) {
          missingMemberInformation.put(clazz.getType(), "Could not be found in " + androidJar);
        }
        continue;
      }
      StringBuilder classBuilder = new StringBuilder();
      Map<DexField, AndroidApiLevel> fieldMapForClass = fieldMap.get(clazz.getClassReference());
      assert fieldMapForClass != null;
      clazz.forEachClassField(
          field -> {
            if (field.getAccessFlags().isPublic()
                && referenceMap.get(field.getReference()) == null
                && !field.toSourceString().contains("this$0")) {
              classBuilder.append("  ").append(field).append(" is missing\n");
            }
          });
      Map<DexMethod, AndroidApiLevel> methodMapForClass = methodMap.get(clazz.getClassReference());
      assert methodMapForClass != null;
      clazz.forEachClassMethod(
          method -> {
            if (method.getAccessFlags().isPublic()
                && referenceMap.get(method.getReference()) == null
                && !factory.objectMembers.isObjectMember(method.getReference())) {
              classBuilder.append("  ").append(method).append(" is missing\n");
            }
          });
      if (classBuilder.length() > 0) {
        missingMemberInformation.put(clazz.getType(), classBuilder.toString());
      }
    }

    Set<DexType> expectedMissingMembers = new HashSet<>();
    // api-versions.xml do not encode all members of StringBuffers and StringBuilders, check that we
    // only have missing definitions for those two classes.
    expectedMissingMembers.add(factory.stringBufferType);
    expectedMissingMembers.add(factory.stringBuilderType);
    // TODO(b/231126636): api-versions.xml has missing definitions for the below classes.
    expectedMissingMembers.add(
        factory.createType("Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"));
    expectedMissingMembers.add(factory.createType("Ljava/time/chrono/ThaiBuddhistDate;"));
    expectedMissingMembers.add(factory.createType("Ljava/time/chrono/HijrahDate;"));
    expectedMissingMembers.add(factory.createType("Ljava/time/chrono/JapaneseDate;"));
    expectedMissingMembers.add(factory.createType("Ljava/time/chrono/MinguoDate;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NfcV;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/IsoDep;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/MifareUltralight;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/MifareClassic;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NdefFormatable;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NfcA;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NfcBarcode;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NfcF;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/NfcB;"));
    expectedMissingMembers.add(factory.createType("Landroid/nfc/tech/Ndef;"));
    expectedMissingMembers.add(factory.createType("Landroid/webkit/CookieSyncManager;"));
    assertEquals(expectedMissingMembers, missingMemberInformation.keySet());
    return true;
  }

  private static class ConstantPool {

    private final IntBox intBox = new IntBox(0);
    private final Map<DexString, Integer> pool = new LinkedHashMap<>();

    public int getOrAdd(DexString string) {
      return pool.computeIfAbsent(string, ignored -> intBox.getAndIncrement());
    }

    public void forEach(ThrowingBiConsumer<DexString, Integer, IOException> consumer)
        throws IOException {
      for (Entry<DexString, Integer> entry : pool.entrySet()) {
        consumer.accept(entry.getKey(), entry.getValue());
      }
    }

    public int size() {
      return pool.size();
    }
  }

  private static int setUniqueConstantPoolEntry(int id) {
    return setBitAtIndex(id, 32);
  }

  public static void generateDatabase(
      Map<DexReference, AndroidApiLevel> referenceMap, DataOutputStream outputStream)
      throws Exception {
    Map<Integer, List<Pair<DexReference, AndroidApiLevel>>> generationMap = new HashMap<>();
    ConstantPool constantPool = new ConstantPool();

    int constantPoolHashMapSize = 1 << AndroidApiDataAccess.entrySizeInBitsForConstantPoolMap();
    int apiHashMapSize = 1 << AndroidApiDataAccess.entrySizeInBitsForApiLevelMap();

    for (Entry<DexReference, AndroidApiLevel> entry : referenceMap.entrySet()) {
      int newCode = AndroidApiDataAccess.apiLevelHash(entry.getKey());
      assert newCode >= 0 && newCode <= apiHashMapSize;
      generationMap
          .computeIfAbsent(newCode, ignoreKey(ArrayList::new))
          .add(Pair.create(entry.getKey(), entry.getValue()));
    }

    Set<String> uniqueHashes = new HashSet<>();
    Map<Integer, Pair<Integer, Integer>> offsetMap = new HashMap<>();
    ByteArrayOutputStream payload = new ByteArrayOutputStream();

    // Serialize api map into payload. This will also generate the entire needed constant pool.
    for (Entry<Integer, List<Pair<DexReference, AndroidApiLevel>>> entry :
        generationMap.entrySet()) {
      int startingOffset = payload.size();
      int length = serializeIntoPayload(entry.getValue(), payload, constantPool, uniqueHashes);
      offsetMap.put(entry.getKey(), Pair.create(startingOffset, length));
    }

    // Write constant pool size <u4:size>.
    outputStream.writeInt(constantPool.size());

    // Write constant pool consisting of <u4:payload_offset><u2:length>.
    assertEquals(AndroidApiDataAccess.constantPoolOffset(), outputStream.size());
    IntBox lastReadIndex = new IntBox(-1);
    constantPool.forEach(
        (string, id) -> {
          assert id > lastReadIndex.getAndIncrement();
          outputStream.writeInt(payload.size());
          outputStream.writeShort(string.content.length);
          payload.write(string.content);
        });

    // Serialize hash lookup table for constant pool.
    Map<Integer, List<Integer>> constantPoolLookupTable = new HashMap<>();
    constantPool.forEach(
        (string, id) -> {
          int constantPoolHash = constantPoolHash(string);
          assert constantPoolHash >= 0 && constantPoolHash <= constantPoolHashMapSize;
          constantPoolLookupTable
              .computeIfAbsent(constantPoolHash, ignoreKey(ArrayList::new))
              .add(id);
        });

    int[] constantPoolEntries = new int[constantPoolHashMapSize];
    int[] constantPoolEntryLengths = new int[constantPoolHashMapSize];
    for (Entry<Integer, List<Integer>> entry : constantPoolLookupTable.entrySet()) {
      // Tag if we have a unique value
      if (entry.getValue().size() == 1) {
        int id = entry.getValue().get(0);
        constantPoolEntries[entry.getKey()] = setUniqueConstantPoolEntry(id);
      } else {
        constantPoolEntries[entry.getKey()] = payload.size();
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        for (Integer id : entry.getValue()) {
          temp.write(intToShortEncodedByteArray(id));
        }
        payload.write(temp.toByteArray());
        constantPoolEntryLengths[entry.getKey()] = temp.size();
      }
    }
    // Write constant pool lookup entries consisting of <u4:payload_offset><u2:length>
    assertEquals(
        AndroidApiDataAccess.constantPoolHashMapOffset(constantPool.size()), outputStream.size());
    for (int i = 0; i < constantPoolEntries.length; i++) {
      outputStream.writeInt(constantPoolEntries[i]);
      outputStream.writeShort(constantPoolEntryLengths[i]);
    }

    int[] apiOffsets = new int[apiHashMapSize];
    int[] apiOffsetLengths = new int[apiHashMapSize];
    for (Entry<Integer, Pair<Integer, Integer>> hashIndexAndOffset : offsetMap.entrySet()) {
      assert apiOffsets[hashIndexAndOffset.getKey()] == 0;
      Pair<Integer, Integer> value = hashIndexAndOffset.getValue();
      int offset = value.getFirst();
      int length = value.getSecond();
      apiOffsets[hashIndexAndOffset.getKey()] = offset;
      apiOffsetLengths[hashIndexAndOffset.getKey()] = length;
    }

    // Write api lookup entries consisting of <u4:payload_offset><u2:length>
    assertEquals(
        AndroidApiDataAccess.apiLevelHashMapOffset(constantPool.size()), outputStream.size());
    for (int i = 0; i < apiOffsets.length; i++) {
      outputStream.writeInt(apiOffsets[i]);
      outputStream.writeShort(apiOffsetLengths[i]);
    }

    // Write the payload.
    outputStream.write(payload.toByteArray());
  }

  /** This will serialize a collection of DexReferences and apis into a byte stream. */
  private static int serializeIntoPayload(
      List<Pair<DexReference, AndroidApiLevel>> pairs,
      ByteArrayOutputStream payload,
      ConstantPool constantPool,
      Set<String> seen)
      throws IOException {
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    for (Pair<DexReference, AndroidApiLevel> pair : pairs) {
      byte[] uniqueDescriptorForReference =
          getUniqueDescriptorForReference(pair.getFirst(), constantPool::getOrAdd);
      assert uniqueDescriptorForReference != getNonExistingDescriptor();
      if (!seen.add(Arrays.toString(uniqueDescriptorForReference))) {
        throw new Unreachable("Hash is not unique");
      }
      temp.write(intToShortEncodedByteArray(uniqueDescriptorForReference.length));
      temp.write(uniqueDescriptorForReference);
      temp.write((byte) pair.getSecond().getLevel());
    }
    byte[] tempArray = temp.toByteArray();
    payload.write(tempArray);
    return tempArray.length;
  }

  public static byte[] intToShortEncodedByteArray(int value) {
    assert isU2(value);
    byte[] bytes = new byte[2];
    bytes[0] = (byte) (value >> 8);
    bytes[1] = (byte) value;
    return bytes;
  }

  private static void computeAllReferencesInHierarchy(
      Map<ClassReference, ParsedApiClass> lookupMap,
      DexItemFactory factory,
      DexType holder,
      ParsedApiClass apiClass,
      AndroidApiLevel linkLevel,
      Map<DexReference, AndroidApiLevel> additionMap) {
    if (!apiClass.getClassReference().getDescriptor().equals(factory.objectDescriptor.toString())) {
      apiClass.visitMethodReferences(
          (apiLevel, methodReferences) -> {
            methodReferences.forEach(
                methodReference -> {
                  addIfNewOrApiLevelIsLower(
                      linkLevel,
                      additionMap,
                      apiLevel,
                      factory.createMethod(methodReference).withHolder(holder, factory));
                });
          });
      apiClass.visitFieldReferences(
          (apiLevel, fieldReferences) -> {
            fieldReferences.forEach(
                fieldReference -> {
                  addIfNewOrApiLevelIsLower(
                      linkLevel,
                      additionMap,
                      apiLevel,
                      factory.createField(fieldReference).withHolder(holder, factory));
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
  }

  private static void addIfNewOrApiLevelIsLower(
      AndroidApiLevel linkLevel,
      Map<DexReference, AndroidApiLevel> additionMap,
      AndroidApiLevel apiLevel,
      DexReference member) {
    AndroidApiLevel currentApiLevel = apiLevel.max(linkLevel);
    AndroidApiLevel existingApiLevel = additionMap.get(member);
    if (existingApiLevel == null || currentApiLevel.isLessThanOrEqualTo(existingApiLevel)) {
      additionMap.put(member, currentApiLevel);
    }
  }
}
