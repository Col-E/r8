// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.AndroidApiDatabaseBuilderGenerator.generatedMainDescriptor;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestState;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.cf.bootstrap.BootstrapCurrentEqualityTest;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidApiDatabaseBuilderGeneratorTest extends TestBase {

  protected final TestParameters parameters;
  private static final Path API_VERSIONS_XML =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "android_jar", "api-versions", "api-versions.xml");
  private static final Path API_DATABASE_JAR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "android_jar", "api-database", "api-database.jar");
  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.R;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidApiDatabaseBuilderGeneratorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private static Path generateJar() throws Exception {
    return generateJar(
        AndroidApiVersionsXmlParser.getParsedApiClasses(API_VERSIONS_XML.toFile(), API_LEVEL));
  }

  private static Path generateJar(List<ParsedApiClass> apiClasses) throws Exception {
    TemporaryFolder temp = new TemporaryFolder();
    temp.create();
    ZipBuilder builder = ZipBuilder.builder(temp.newFile("out.jar").toPath());
    AndroidApiDatabaseBuilderGenerator.generate(
        apiClasses,
        (descriptor, content) -> {
          try {
            String binaryName = DescriptorUtils.getBinaryNameFromDescriptor(descriptor) + ".class";
            builder.addBytes(binaryName, content);
          } catch (IOException exception) {
            throw new RuntimeException(exception);
          }
        });
    return builder.build();
  }

  @Test
  public void testCanParseApiVersionsXml() throws Exception {
    // This tests makes a rudimentary check on the number of classes, fields and methods in
    // api-versions.xml to ensure that the runtime tests do not vacuously succeed.
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.getParsedApiClasses(API_VERSIONS_XML.toFile(), API_LEVEL);
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
    assertEquals(4742, parsedApiClasses.size());
    assertEquals(25144, numberOfFields.get());
    assertEquals(38661, numberOfMethods.get());
  }

  @Test
  public void testDatabaseGenerationUpToDate() throws Exception {
    BootstrapCurrentEqualityTest.filesAreEqual(generateJar(), API_DATABASE_JAR);
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
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.getParsedApiClasses(API_VERSIONS_XML.toFile(), API_LEVEL);
    Path generatedJar = generateJar(parsedApiClasses);
    validateJar(generatedJar, parsedApiClasses);
    Files.move(generatedJar, API_DATABASE_JAR, REPLACE_EXISTING);
  }

  private static void validateJar(Path generated, List<ParsedApiClass> apiClasses) {
    List<BiFunction<Path, List<ParsedApiClass>, Boolean>> tests =
        ImmutableList.of(
            AndroidApiDatabaseBuilderGeneratorTest::testGeneratedOutputForVisitClasses,
            AndroidApiDatabaseBuilderGeneratorTest::testBuildClassesContinue,
            AndroidApiDatabaseBuilderGeneratorTest::testBuildClassesBreak,
            AndroidApiDatabaseBuilderGeneratorTest::testNoPlaceHolder);
    tests.forEach(
        test -> {
          try {
            if (!test.apply(generated, apiClasses)) {
              throw new RuntimeException("Generated jar did not pass tests");
            }
          } catch (Exception e) {
            throw new RuntimeException("Generated jar did not pass tests", e);
          }
        });
  }

  private static boolean testGeneratedOutputForVisitClasses(
      Path generated, List<ParsedApiClass> parsedApiClasses) {
    String expectedOutput =
        StringUtils.lines(
            ListUtils.map(
                parsedApiClasses, apiClass -> apiClass.getClassReference().getDescriptor()));
    return runTest(generated, TestGeneratedMainVisitClasses.class)
        .getStdOut()
        .equals(expectedOutput);
  }

  private static boolean testBuildClassesContinue(
      Path generated, List<ParsedApiClass> parsedApiClasses) {
    return runTest(generated, TestBuildClassesContinue.class)
        .getStdOut()
        .equals(getExpected(parsedApiClasses, false));
  }

  private static boolean testBuildClassesBreak(
      Path generated, List<ParsedApiClass> parsedApiClasses) {
    return runTest(generated, TestBuildClassesBreak.class)
        .getStdOut()
        .equals(getExpected(parsedApiClasses, true));
  }

  private static boolean testNoPlaceHolder(Path generated, List<ParsedApiClass> parsedApiClasses) {
    try {
      CodeInspector inspector = new CodeInspector(generated);
      inspector
          .allClasses()
          .forEach(
              clazz -> {
                clazz.forAllMethods(
                    methods -> {
                      if (methods.getFinalName().startsWith("placeHolder")) {
                        throw new RuntimeException("Found placeHolder method in generated jar");
                      }
                    });
              });
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
    return true;
  }

  private static JvmTestRunResult runTest(Path generated, Class<?> testClass) {
    try {
      TemporaryFolder temporaryFolder = new TemporaryFolder();
      temporaryFolder.create();
      return JvmTestBuilder.create(new TestState(temporaryFolder))
          .addProgramClassFileData(
              transformer(testClass)
                  .replaceClassDescriptorInMethodInstructions(
                      descriptor(AndroidApiDatabaseBuilderTemplate.class),
                      generatedMainDescriptor())
                  .transform())
          .addLibraryFiles(
              generated,
              ToolHelper.R8_WITHOUT_DEPS_JAR,
              getDepsWithoutGeneratedApiModelClasses(),
              ToolHelper.getJava8RuntimeJar())
          .run(TestRuntime.getSystemRuntime(), testClass)
          .apply(
              result -> {
                if (result.getExitCode() != 0) {
                  throw new RuntimeException(result.getStdErr());
                }
              });
    } catch (IOException | ExecutionException | CompilationFailedException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static Path getDepsWithoutGeneratedApiModelClasses() throws IOException {
    Path tempDeps = Files.createTempDirectory("temp_deps");
    ZipUtils.unzip(
        ToolHelper.DEPS.toString(),
        tempDeps.toFile(),
        entry -> !entry.getName().startsWith("com/android/tools/r8/apimodel/"));
    Path modifiedDeps = Files.createTempFile("modified_deps", ".jar");
    ZipUtils.zip(modifiedDeps, tempDeps);
    return modifiedDeps;
  }

  private static String getExpected(List<ParsedApiClass> parsedApiClasses, boolean abort) {
    Map<ClassReference, ParsedApiClass> parsedApiClassMap = new HashMap<>(parsedApiClasses.size());
    parsedApiClasses.forEach(
        parsedClass -> parsedApiClassMap.put(parsedClass.getClassReference(), parsedClass));
    List<String> expected = new ArrayList<>();
    parsedApiClasses.forEach(
        apiClass -> {
          expected.add("CLASS: " + apiClass.getClassReference().getDescriptor());
          expected.add(apiClass.getApiLevel().getName());
          expected.add(apiClass.getTotalMemberCount() + "");
          visitApiClass(expected, parsedApiClassMap, apiClass, apiClass.getApiLevel(), true, abort);
          visitApiClass(
              expected, parsedApiClassMap, apiClass, apiClass.getApiLevel(), false, abort);
        });
    return StringUtils.lines(expected);
  }

  private static boolean visitApiClass(
      List<String> expected,
      Map<ClassReference, ParsedApiClass> parsedApiClassMap,
      ParsedApiClass apiClass,
      AndroidApiLevel apiLevel,
      boolean visitFields,
      boolean abort) {
    BooleanBox added = new BooleanBox(false);
    if (visitFields) {
      added.set(visitFields(expected, apiClass, apiLevel, abort));
    } else {
      added.set(visitMethods(expected, apiClass, apiLevel, abort));
    }
    if (added.isTrue() && abort) {
      return true;
    }
    // Go through super type methods if not interface.
    if (!apiClass.isInterface()) {
      apiClass.visitSuperType(
          (classReference, linkApiLevel) -> {
            if (added.isTrue() && abort) {
              return;
            }
            ParsedApiClass superApiClass = parsedApiClassMap.get(classReference);
            assert superApiClass != null;
            added.set(
                visitApiClass(
                    expected,
                    parsedApiClassMap,
                    superApiClass,
                    linkApiLevel.max(apiLevel),
                    visitFields,
                    abort));
          });
    }
    if (!visitFields) {
      apiClass.visitInterface(
          (classReference, linkApiLevel) -> {
            if (added.isTrue() && abort) {
              return;
            }
            ParsedApiClass ifaceApiClass = parsedApiClassMap.get(classReference);
            assert ifaceApiClass != null;
            added.set(
                visitApiClass(
                    expected,
                    parsedApiClassMap,
                    ifaceApiClass,
                    linkApiLevel.max(apiLevel),
                    visitFields,
                    abort));
          });
    }
    return added.get();
  }

  private static boolean visitFields(
      List<String> expected, ParsedApiClass apiClass, AndroidApiLevel minApiLevel, boolean abort) {
    BooleanBox added = new BooleanBox(false);
    apiClass.visitFieldReferences(
        (apiLevel, fieldReferences) -> {
          fieldReferences.forEach(
              fieldReference -> {
                if (added.isTrue() && abort) {
                  return;
                }
                added.set();
                expected.add(fieldReference.getFieldType().getDescriptor());
                expected.add(fieldReference.getFieldName());
                expected.add(apiLevel.max(minApiLevel).getName());
              });
        });
    return added.get();
  }

  private static boolean visitMethods(
      List<String> expected, ParsedApiClass apiClass, AndroidApiLevel minApiLevel, boolean abort) {
    BooleanBox added = new BooleanBox(false);
    apiClass.visitMethodReferences(
        (apiLevel, methodReferences) -> {
          methodReferences.forEach(
              methodReference -> {
                if (added.isTrue() && abort) {
                  return;
                }
                added.set();
                expected.add(methodReference.getMethodDescriptor());
                expected.add(methodReference.getMethodName());
                expected.add(apiLevel.max(minApiLevel).getName());
              });
        });
    return added.get();
  }

  public static class TestGeneratedMainVisitClasses {

    public static void main(String[] args) {
      AndroidApiDatabaseBuilderTemplate.visitApiClasses(System.out::println);
    }
  }

  public static class TestBuildClassesContinue {

    public static void main(String[] args) {
      AndroidApiDatabaseBuilderTemplate.visitApiClasses(
          descriptor -> {
            com.android.tools.r8.androidapi.AndroidApiClass apiClass =
                AndroidApiDatabaseBuilderTemplate.buildClass(
                    Reference.classFromDescriptor(descriptor));
            if (apiClass != null) {
              System.out.println("CLASS: " + descriptor);
              System.out.println(apiClass.getApiLevel().getName());
              System.out.println(apiClass.getMemberCount());
              apiClass.visitFields(
                  (reference, apiLevel) -> {
                    System.out.println(reference.getFieldType().getDescriptor());
                    System.out.println(reference.getFieldName());
                    System.out.println(apiLevel.getName());
                    return TraversalContinuation.CONTINUE;
                  });
              apiClass.visitMethods(
                  (reference, apiLevel) -> {
                    System.out.println(reference.getMethodDescriptor());
                    System.out.println(reference.getMethodName());
                    System.out.println(apiLevel.getName());
                    return TraversalContinuation.CONTINUE;
                  });
            }
          });
    }
  }

  public static class TestBuildClassesBreak {

    public static void main(String[] args) {
      AndroidApiDatabaseBuilderTemplate.visitApiClasses(
          descriptor -> {
            com.android.tools.r8.androidapi.AndroidApiClass apiClass =
                AndroidApiDatabaseBuilderTemplate.buildClass(
                    Reference.classFromDescriptor(descriptor));
            if (apiClass != null) {
              System.out.println("CLASS: " + descriptor);
              System.out.println(apiClass.getApiLevel().getName());
              System.out.println(apiClass.getMemberCount());
              apiClass.visitFields(
                  (reference, apiLevel) -> {
                    System.out.println(reference.getFieldType().getDescriptor());
                    System.out.println(reference.getFieldName());
                    System.out.println(apiLevel.getName());
                    return TraversalContinuation.BREAK;
                  });
              apiClass.visitMethods(
                  (reference, apiLevel) -> {
                    System.out.println(reference.getMethodDescriptor());
                    System.out.println(reference.getMethodName());
                    System.out.println(apiLevel.getName());
                    return TraversalContinuation.BREAK;
                  });
            }
          });
    }
  }
}
