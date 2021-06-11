// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.AndroidApiDatabaseBuilderGenerator.generatedMainDescriptor;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.JvmTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsedApiClass;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
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
  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.R;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
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

  public static void main(String[] args) throws Exception {
    generateJar();
  }

  @Test
  public void testDatabaseGenerationUpToDate() {
    assumeTrue("b/190368382", false);
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

  @Test()
  public void testGeneratedOutputForVisitClasses() throws Exception {
    runTest(
        TestGeneratedMainVisitClasses.class,
        (parsedApiClasses, runResult) -> {
          String expectedOutput =
              StringUtils.lines(
                  ListUtils.map(
                      parsedApiClasses, apiClass -> apiClass.getClassReference().getDescriptor()));
          runResult.assertSuccessWithOutput(expectedOutput);
        });
  }

  @Test()
  public void testBuildClassesContinue() throws Exception {
    runTest(
        TestBuildClassesContinue.class,
        (parsedApiClasses, runResult) -> {
          runResult.assertSuccessWithOutputLines(getExpected(parsedApiClasses, false));
        });
  }

  @Test()
  public void testBuildClassesBreak() throws Exception {
    runTest(
        TestBuildClassesBreak.class,
        (parsedApiClasses, runResult) -> {
          runResult.assertSuccessWithOutputLines(getExpected(parsedApiClasses, true));
        });
  }

  private void runTest(
      Class<?> testClass, BiConsumer<List<ParsedApiClass>, JvmTestRunResult> resultConsumer)
      throws Exception {
    List<ParsedApiClass> parsedApiClasses =
        AndroidApiVersionsXmlParser.getParsedApiClasses(API_VERSIONS_XML.toFile(), API_LEVEL);
    testForJvm()
        .addProgramClassFileData(
            transformer(testClass)
                .replaceClassDescriptorInMethodInstructions(
                    descriptor(AndroidApiDatabaseBuilderTemplate.class), generatedMainDescriptor())
                .transform())
        .addLibraryFiles(generateJar(parsedApiClasses))
        // TODO(b/190368382): This will change when databasebuilder is included in deps.
        .addLibraryFiles(ToolHelper.R8_WITHOUT_DEPS_JAR, ToolHelper.DEPS)
        .addDefaultRuntimeLibrary(parameters)
        .run(parameters.getRuntime(), testClass)
        .apply(
            result -> {
              if (result.getExitCode() != 0) {
                System.out.println(result.getStdErr());
              }
            })
        .assertSuccess()
        .apply(result -> resultConsumer.accept(parsedApiClasses, result));
  }

  private List<String> getExpected(List<ParsedApiClass> parsedApiClasses, boolean abort) {
    List<String> expected = new ArrayList<>();
    parsedApiClasses.forEach(
        apiClass -> {
          expected.add(apiClass.getClassReference().getDescriptor());
          expected.add(apiClass.getApiLevel().getName());
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
                      expected.add(apiLevel.getName());
                    });
              });
          added.set(false);
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
                      expected.add(apiLevel.getName());
                    });
              });
        });
    return expected;
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
              System.out.println(descriptor);
              System.out.println(apiClass.getApiLevel().getName());
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
              System.out.println(descriptor);
              System.out.println(apiClass.getApiLevel().getName());
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
