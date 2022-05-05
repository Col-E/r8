// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.androidapi.AndroidApiLevelHashingDatabaseImpl;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.ImmutableList;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidApiHashingDatabaseBuilderGeneratorTest extends TestBase {

  protected final TestParameters parameters;
  private static final Path API_DATABASE_HASH_LOOKUP =
      Paths.get(ToolHelper.RESOURCES_DIR, "api_database", "api_database_hash_lookup.ser");
  private static final Path API_DATABASE_API_LEVEL =
      Paths.get(ToolHelper.RESOURCES_DIR, "api_database", "api_database_api_level.ser");
  private static final Path API_DATABASE_AMBIGUOUS =
      Paths.get(ToolHelper.RESOURCES_DIR, "api_database", "api_database_ambiguous.txt");

  // Update the API_LEVEL below to have the database generated for a new api level.
  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.S;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiHashingDatabaseBuilderGeneratorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static class GenerateDatabaseResourceFilesResult {

    private final Path indices;
    private final Path apiLevels;
    private final Path ambiguous;

    public GenerateDatabaseResourceFilesResult(Path indices, Path apiLevels, Path ambiguous) {
      this.indices = indices;
      this.apiLevels = apiLevels;
      this.ambiguous = ambiguous;
    }
  }

  private static GenerateDatabaseResourceFilesResult generateResourcesFiles() throws Exception {
    return generateResourcesFiles(
        AndroidApiVersionsXmlParser.getParsedApiClasses(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL).toFile(), API_LEVEL),
        API_LEVEL);
  }

  private static GenerateDatabaseResourceFilesResult generateResourcesFiles(
      List<ParsedApiClass> apiClasses, AndroidApiLevel androidJarApiLevel) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    Path indices = temp.newFile("indices.ser").toPath();
    Path apiLevels = temp.newFile("apiLevels.ser").toPath();
    Path ambiguous = temp.newFile("ambiguous.ser").toPath();
    AndroidApiHashingDatabaseBuilderGenerator.generate(
        apiClasses, indices, apiLevels, ambiguous, androidJarApiLevel);
    return new GenerateDatabaseResourceFilesResult(indices, apiLevels, ambiguous);
  }

  @Test
  public void testCanParseApiVersionsXml() throws Exception {
    // This tests makes a rudimentary check on the number of classes, fields and methods in
    // api-versions.xml to ensure that the runtime tests do not vacuously succeed.
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.getParsedApiClasses(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL).toFile(), API_LEVEL);
    IntBox numberOfFields = new IntBox(0);
    IntBox numberOfMethods = new IntBox(0);
    parsedApiClasses.forEach(
        apiClass -> {
          apiClass.visitFieldReferences(
              ((apiLevel, fieldReferences) -> {
                fieldReferences.forEach(field -> numberOfFields.increment());
              }));
          apiClass.visitMethodReferences(
              ((apiLevel, methodReferences) -> {
                methodReferences.forEach(field -> numberOfMethods.increment());
              }));
        });
    // These numbers will change when updating api-versions.xml
    assertEquals(5065, parsedApiClasses.size());
    assertEquals(26492, numberOfFields.get());
    assertEquals(40475, numberOfMethods.get());
  }

  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles();
    TestBase.filesAreEqual(result.indices, API_DATABASE_HASH_LOOKUP);
    TestBase.filesAreEqual(result.apiLevels, API_DATABASE_API_LEVEL);
    TestBase.filesAreEqual(result.ambiguous, API_DATABASE_AMBIGUOUS);
  }

  @Test
  public void testCanLookUpAllParsedApiClassesAndMembers() throws Exception {
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.getParsedApiClasses(
            ToolHelper.getApiVersionsXmlFile(API_LEVEL).toFile(), API_LEVEL);
    DexItemFactory factory = new DexItemFactory();
    AndroidApiLevelHashingDatabaseImpl androidApiLevelDatabase =
        new AndroidApiLevelHashingDatabaseImpl(ImmutableList.of());
    parsedApiClasses.forEach(
        parsedApiClass -> {
          DexType type = factory.createType(parsedApiClass.getClassReference().getDescriptor());
          AndroidApiLevel apiLevel = androidApiLevelDatabase.getTypeApiLevel(type);
          assertEquals(parsedApiClass.getApiLevel(), apiLevel);
          parsedApiClass.visitMethodReferences(
              (methodApiLevel, methodReferences) ->
                  methodReferences.forEach(
                      methodReference -> {
                        DexMethod method = factory.createMethod(methodReference);
                        AndroidApiLevel androidApiLevel;
                        if (factory.objectMembers.isObjectMember(method)) {
                          androidApiLevel = AndroidApiLevel.B;
                        } else {
                          androidApiLevel = androidApiLevelDatabase.getMethodApiLevel(method);
                        }
                        androidApiLevel.isLessThanOrEqualTo(methodApiLevel);
                      }));
          parsedApiClass.visitFieldReferences(
              (fieldApiLevel, fieldReferences) ->
                  fieldReferences.forEach(
                      fieldReference -> {
                        DexField field = factory.createField(fieldReference);
                        androidApiLevelDatabase
                            .getFieldApiLevel(field)
                            .isLessThanOrEqualTo(fieldApiLevel);
                      }));
        });
  }

  /**
   * Main entry point for building a database over references in framework to the api level they
   * were introduced. Running main will generate a new jar and run tests on it to ensure it is
   * compatible with R8 sources and works as expected.
   *
   * <p>The generated jar depends on r8NoManifestWithoutDeps.
   *
   * <p>If the generated jar passes tests it will be moved to third_party/android_jar/api-database/
   * and override the current file in there.
   */
  public static void main(String[] args) throws Exception {
    GenerateDatabaseResourceFilesResult result = generateResourcesFiles();
    verifyNoDuplicateHashes(result.indices);
    Files.move(result.indices, API_DATABASE_HASH_LOOKUP, REPLACE_EXISTING);
    Files.move(result.apiLevels, API_DATABASE_API_LEVEL, REPLACE_EXISTING);
    Files.move(result.ambiguous, API_DATABASE_AMBIGUOUS, REPLACE_EXISTING);
  }

  private static void verifyNoDuplicateHashes(Path indicesPath) throws Exception {
    Set<Integer> elements = new HashSet<>();
    int[] indices;
    try (FileInputStream fileInputStream = new FileInputStream(indicesPath.toFile());
        ObjectInputStream indicesObjectStream = new ObjectInputStream(fileInputStream)) {
      indices = (int[]) indicesObjectStream.readObject();
      for (int index : indices) {
        assertTrue(elements.add(index));
      }
    }
    assertEquals(elements.size(), indices.length);
  }
}
