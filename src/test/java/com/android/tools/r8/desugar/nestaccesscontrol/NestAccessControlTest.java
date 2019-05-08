// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAccessControlTest extends TestBase {

  private static final Path JAR =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_JAR_DIR).resolve("nestHostExample" + JAR_EXTENSION);
  private static final Path CLASSES_PATH =
      Paths.get(ToolHelper.EXAMPLES_JAVA11_BUILD_DIR).resolve("nestHostExample/");
  private static final String PACKAGE_NAME = "nestHostExample.";
  private static final int NUMBER_OF_TEST_CLASSES = 15;

  private static final List<String> CLASS_NAMES =
      ImmutableList.of(
          "BasicNestHostWithInnerClassFields",
          "BasicNestHostWithInnerClassFields$BasicNestedClass",
          "BasicNestHostWithInnerClassMethods",
          "BasicNestHostWithInnerClassMethods$BasicNestedClass",
          "BasicNestHostWithInnerClassConstructors",
          "BasicNestHostWithInnerClassConstructors$BasicNestedClass",
          "BasicNestHostWithAnonymousInnerClass",
          "BasicNestHostWithAnonymousInnerClass$1",
          "BasicNestHostWithAnonymousInnerClass$InterfaceForAnonymousClass",
          "NestHostExample",
          "NestHostExample$NestMemberInner",
          "NestHostExample$NestMemberInner$NestMemberInnerInner",
          "NestHostExample$StaticNestMemberInner",
          "NestHostExample$StaticNestMemberInner$StaticNestMemberInnerInner",
          "NestHostExample$StaticNestInterfaceInner");

  private static final ImmutableMap<String, String> MAIN_CLASSES =
      ImmutableMap.of(
          "fields", "BasicNestHostWithInnerClassFields",
          "methods", "BasicNestHostWithInnerClassMethods",
          "constructors", "BasicNestHostWithInnerClassConstructors",
          "anonymous", "BasicNestHostWithAnonymousInnerClass",
          "all", "NestHostExample");
  private static final String ALL_EXPECTED_RESULT =
      StringUtils.lines(
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethodnest1SFieldstaticNest1SFieldstaticNest1SFieldnest1SMethodstaticNest1SMethodstaticNest1SMethodnest2SFieldstaticNest2SFieldstaticNest2SFieldnest2SMethodstaticNest2SMethodstaticNest2SMethodnest1Fieldnest1Methodnest2Fieldnest2Method",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod",
          "staticInterfaceMethodstaticStaticInterfaceMethod");
  private static final ImmutableMap<String, String> EXPECTED_RESULTS =
      ImmutableMap.of(
          "fields",
              StringUtils.lines(
                  "RWnestFieldRWRWnestFieldRWRWnestField", "RWfieldRWRWfieldRWRWnestField"),
          "methods",
              StringUtils.lines(
                  "nestMethodstaticNestMethodstaticNestMethod",
                  "hostMethodstaticHostMethodstaticNestMethod"),
          "constructors", StringUtils.lines("field", "nest1SField"),
          "anonymous",
              StringUtils.lines(
                  "fieldstaticFieldstaticFieldhostMethodstaticHostMethodstaticHostMethod"),
          "all", ALL_EXPECTED_RESULT);

  private static Function<AndroidApiLevel, D8TestCompileResult> d8CompilationResult =
      memoizeFunction(NestAccessControlTest::compileD8);

  private static BiFunction<Backend, AndroidApiLevel, R8TestCompileResult> r8CompilationResult =
      memoizeBiFunction(NestAccessControlTest::compileR8);

  private static D8TestCompileResult compileD8(AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForD8(getStaticTemp())
        .addProgramFiles(JAR)
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
            })
        .setMinApi(minApi)
        .compile();
  }

  private static R8TestCompileResult compileR8(Backend backend, AndroidApiLevel minApi)
      throws CompilationFailedException {
    return testForR8(getStaticTemp(), backend)
        .noTreeShaking()
        .noMinification()
        .addKeepAllAttributes()
        .addOptionsModification(
            options -> {
              options.enableNestBasedAccessDesugaring = true;
            })
        .addProgramFiles(JAR)
        .setMinApi(minApi)
        .compile();
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  private static String getMainClass(String id) {
    return PACKAGE_NAME + MAIN_CLASSES.get(id);
  }

  private static String getExpectedResult(String id) {
    return EXPECTED_RESULTS.get(id);
  }


  public NestAccessControlTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public void testJavaAndD8(String id) throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addProgramFiles(JAR)
          .run(parameters.getRuntime(), getMainClass(id))
          .assertSuccessWithOutput(getExpectedResult(id));
    } else {
      assert parameters.isDexRuntime();
      d8CompilationResult
          .apply(parameters.getApiLevel())
          .run(parameters.getRuntime(), getMainClass(id))
          .assertSuccessWithOutput(getExpectedResult(id));
    }
  }

  @Test
  public void testJavaAndD8() throws Exception {
    testJavaAndD8("methods");
    testJavaAndD8("fields");
    testJavaAndD8("constructors");
    testJavaAndD8("anonymous");
    testJavaAndD8("all");
  }

  public void testR8(String id) throws Exception {
    R8TestRunResult result =
        r8CompilationResult
            .apply(parameters.getBackend(), parameters.getApiLevel())
            .run(parameters.getRuntime(), getMainClass(id));
    result.assertSuccessWithOutput(getExpectedResult(id));
    if (parameters.isCfRuntime()) {
      result.inspect(NestAccessControlTest::checkNestMateAttributes);
    }
  }

  @Test
  public void testMethodsAccessR8() throws Exception {
    // TODO(b/130529390): As features are implemented, set success to true in each line.
    testR8("methods");
    testR8("fields");
    testR8("constructors");
    testR8("anonymous");
    testR8("all");
  }

  private void assertOnlyRequiredBridges(CodeInspector inspector) {
    // The following 2 classes have an extra private member which does not require a bridge.

    // Two bridges for method and staticMethod.
    int methodNumBridges = parameters.isCfRuntime() ? 0 : 2;
    ClassSubject methodMainClass = inspector.clazz(getMainClass("methods"));
    assertEquals(
        methodNumBridges, methodMainClass.allMethods(FoundMethodSubject::isSynthetic).size());

    // Four bridges for field and staticField, both get & set.
    int fieldNumBridges = parameters.isCfRuntime() ? 0 : 4;
    ClassSubject fieldMainClass = inspector.clazz(getMainClass("fields"));
    assertEquals(
        fieldNumBridges, fieldMainClass.allMethods(FoundMethodSubject::isSynthetic).size());
  }

  @Test
  public void testOnlyRequiredBridges() throws Exception {
    if (parameters.isDexRuntime()) {
      d8CompilationResult.apply(parameters.getApiLevel()).inspect(this::assertOnlyRequiredBridges);
    }
    r8CompilationResult
        .apply(parameters.getBackend(), parameters.getApiLevel())
        .inspect(this::assertOnlyRequiredBridges);
  }

  private static void checkNestMateAttributes(CodeInspector inspector) {
    // Interface method desugaring may add extra classes
    assertTrue(NUMBER_OF_TEST_CLASSES <= inspector.allClasses().size());
    ImmutableList<String> outerClassNames = MAIN_CLASSES.values().asList();
    inspector.forAllClasses(
        classSubject -> {
          DexClass dexClass = classSubject.getDexClass();
          assertTrue(dexClass.isInANest());
          if (outerClassNames.contains(dexClass.type.getName())) {
            assertNull(dexClass.getNestHostClassAttribute());
            assertFalse(dexClass.getNestMembersClassAttributes().isEmpty());
          } else {
            assertTrue(dexClass.getNestMembersClassAttributes().isEmpty());
            assertTrue(
                outerClassNames.contains(
                    dexClass.getNestHostClassAttribute().getNestHost().getName()));
          }
        });
  }

  private void compileOnlyClassesMatching(Matcher<String> matcher, boolean d8) throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    if (d8) {
      testForD8()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile();
    } else {
      testForR8(parameters.getBackend())
          .noTreeShaking()
          .noMinification()
          .addKeepAllAttributes()
          .setMinApi(parameters.getApiLevel())
          .addProgramFiles(matchingClasses)
          .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
          .compile();
    }
  }

  private void testMissingNestHostError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher =
          containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass");
      compileOnlyClassesMatching(innerClassMatcher, d8);
      fail("Should have raised an exception for missing nest host");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest host"));
    }
  }

  private void testIncompleteNestError(boolean d8) {
    try {
      Matcher<String> innerClassMatcher = endsWith("BasicNestHostWithInnerClassMethods");
      compileOnlyClassesMatching(innerClassMatcher, d8);
      fail("Should have raised an exception for incomplete nest");
    } catch (Exception e) {
      assertTrue(e.getCause().getMessage().contains("requires its nest mates"));
    }
  }

  @Test
  public void testErrorD8() {
    // TODO (b/132147492): use diagnosis handler
    Assume.assumeTrue(parameters.isDexRuntime());
    testMissingNestHostError(true);
    testIncompleteNestError(true);
  }

  @Test
  public void testErrorR8() {
    // TODO (b/132147492): use diagnosis handler
    Assume.assumeTrue(parameters.isDexRuntime());
    testMissingNestHostError(false);
    testIncompleteNestError(false);
  }

  private D8TestCompileResult compileClassesWithD8ProgramClassesMatching(Matcher<String> matcher)
      throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    return testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramFiles(matchingClasses)
        .addClasspathFiles(JAR)
        .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
        .compile();
  }

  private static void assertBridges(CodeInspector inspector, int numBridges) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (!clazz.isSynthetic()) {
        assertEquals(numBridges, clazz.allMethods(FoundMethodSubject::isSynthetic).size());
      }
    }
  }

  @Test
  public void testD8NestPartiallyOnClassPath() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    // 1 inner class.
    D8TestCompileResult singleInner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass"));
    singleInner.inspect(inspector -> assertBridges(inspector, 2));
    // Outer class.
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(endsWith("BasicNestHostWithInnerClassMethods"));
    host.inspect(inspector -> assertBridges(inspector, 2));
    // 2 inner classes.
    D8TestCompileResult multipleInner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("NestHostExample$StaticNestMemberInner"));
    multipleInner.inspect(inspector -> assertBridges(inspector, 5));
  }

  private static void assertNestConstructor(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic));
  }

  @Test
  public void testD8NestPartiallyOnClassPathConstructor() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassConstructors$BasicNestedClass"));
    inner.inspect(
        inspector -> {
          assertBridges(inspector, 1);
          assertNestConstructor(inspector);
        });
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(
            endsWith("BasicNestHostWithInnerClassConstructors"));
    host.inspect(
        inspector -> {
          assertBridges(inspector, 1);
          assertNestConstructor(inspector);
        });
  }

  @Test
  public void testD8NestPartiallyOnClassPathMerge() throws Exception {
    // Multiple Nest Constructor classes have to be merged here.
    Assume.assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassConstructors$BasicNestedClass"));
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(
            endsWith("BasicNestHostWithInnerClassConstructors"));
    testForD8()
        .addProgramFiles(inner.writeToZip(), host.writeToZip())
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(options -> options.enableNestBasedAccessDesugaring = true)
        .compile()
        .inspect(inspector -> assertEquals(3, inspector.allClasses().size()))
        .run(parameters.getRuntime(), getMainClass("constructors"))
        .assertSuccessWithOutput(getExpectedResult("constructors"));
  }
}
