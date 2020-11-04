// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.synthesis.SyntheticItems.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GenerateMainDexListRunResult;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.backports.AbstractBackportTest.MiniAssert;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackportMainDexTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User1.class, User2.class);

  static final List<Class<?>> MAIN_DEX_LIST_CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User2.class);

  static final String SyntheticUnderUser1 =
      User1.class.getTypeName() + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
  static final String SyntheticUnderUser2 =
      User2.class.getTypeName() + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.J).build();
  }

  public BackportMainDexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String[] getRunArgs() {
    // Only call User1 methods on runtimes with native multidex.
    if (parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .asDex()
            .getMinApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithNativeMultiDexSupport())) {
      return new String[] {User1.class.getTypeName()};
    }
    return new String[0];
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
  }

  private GenerateMainDexListRunResult traceMainDex(
      Collection<Class<?>> classes, Collection<Path> files) throws Exception {
    return testForMainDexListGenerator()
        .addProgramClasses(classes)
        .addProgramFiles(files)
        .addLibraryFiles(ToolHelper.getFirstSupportedAndroidJar(parameters.getApiLevel()))
        .addMainDexRules(keepMainProguardConfiguration(TestClass.class))
        .run();
  }

  @Test
  public void testMainDexTracingCf() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    GenerateMainDexListRunResult mainDexListFromCf = traceMainDex(CLASSES, Collections.emptyList());
    assertEquals(
        ListUtils.map(MAIN_DEX_LIST_CLASSES, Reference::classFromClass),
        mainDexListFromCf.getMainDexList());
  }

  @Test
  public void testMainDexTracingDex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path out =
        testForD8()
            .addProgramClasses(CLASSES)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    GenerateMainDexListRunResult mainDexListFromDex =
        traceMainDex(Collections.emptyList(), Collections.singleton(out));
    assertEquals(
        Streams.concat(
                MAIN_DEX_LIST_CLASSES.stream().map(Reference::classFromClass),
                getMainDexExpectedSynthetics().stream().map(MethodReference::getHolderClass))
            .collect(Collectors.toSet()),
        ImmutableSet.copyOf(mainDexListFromDex.getMainDexList()));
  }

  @Test
  public void testMainDexTracingDexIntermediates() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path out =
        testForD8()
            .addProgramClasses(CLASSES)
            // Setting intermediate will annotate synthetics, which should not cause types in those
            // to become main-dex included.
            .setIntermediate(true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    GenerateMainDexListRunResult mainDexListFromDex =
        traceMainDex(Collections.emptyList(), Collections.singleton(out));
    // Compiling in intermediate will share the synthetics within the context types so there is one
    // synthetic class per backport in User2: Character.compare and Integer.compare.
    assertEquals(MAIN_DEX_LIST_CLASSES.size() + 2, mainDexListFromDex.getMainDexList().size());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    MainDexConsumer mainDexConsumer = new MainDexConsumer();
    testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters.getApiLevel())
        .addMainDexListClasses(MiniAssert.class, TestClass.class, User2.class)
        .setProgramConsumer(mainDexConsumer)
        .compile()
        .inspect(this::checkExpectedSynthetics)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    checkMainDex(mainDexConsumer);
  }

  @Test
  public void testD8FilePerClassFile() throws Exception {
    runD8FilePerMode(OutputMode.DexFilePerClassFile);
  }

  @Test
  public void testD8FilePerClass() throws Exception {
    runD8FilePerMode(OutputMode.DexFilePerClass);
  }

  private void runD8FilePerMode(OutputMode outputMode) throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path perClassOutput =
        testForD8(parameters.getBackend())
            .setOutputMode(outputMode)
            .addProgramClasses(CLASSES)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    MainDexConsumer mainDexConsumer = new MainDexConsumer();
    testForD8()
        .addProgramFiles(perClassOutput)
        .setMinApi(parameters.getApiLevel())
        .addMainDexListClasses(MiniAssert.class, TestClass.class, User2.class)
        .setProgramConsumer(mainDexConsumer)
        .compile()
        .inspect(this::checkExpectedSynthetics)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    checkMainDex(mainDexConsumer);
  }

  // TODO(b/168584485): This test should be removed once support is dropped.
  @Test
  public void testD8MergingWithTraceCf() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path out1 =
        testForD8()
            .addProgramClasses(User1.class)
            .addClasspathClasses(CLASSES)
            .setIntermediate(true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    Path out2 =
        testForD8()
            .addProgramClasses(User2.class)
            .addClasspathClasses(CLASSES)
            .setIntermediate(true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    MainDexConsumer mainDexConsumer = new MainDexConsumer();
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class, MiniAssert.class)
        .addProgramFiles(out1, out2)
        .setMinApi(parameters.getApiLevel())
        .addMainDexListClassReferences(
            traceMainDex(CLASSES, Collections.emptyList()).getMainDexList())
        .setProgramConsumer(mainDexConsumer)
        .compile()
        .inspect(this::checkExpectedSynthetics)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    checkMainDex(mainDexConsumer);
  }

  @Test
  public void testD8MergingWithTraceDex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Path out1 =
        testForD8()
            .addProgramClasses(User1.class)
            .addClasspathClasses(CLASSES)
            .setIntermediate(true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    Path out2 =
        testForD8()
            .addProgramClasses(User2.class)
            .addClasspathClasses(CLASSES)
            .setIntermediate(true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    MainDexConsumer mainDexConsumer = new MainDexConsumer();
    List<Class<?>> classes = ImmutableList.of(TestClass.class, MiniAssert.class);
    List<Path> files = ImmutableList.of(out1, out2);
    GenerateMainDexListRunResult traceResult = traceMainDex(classes, files);
    testForD8(parameters.getBackend())
        .addProgramClasses(classes)
        .addProgramFiles(files)
        .setMinApi(parameters.getApiLevel())
        .addMainDexListClassReferences(traceResult.getMainDexList())
        .setProgramConsumer(mainDexConsumer)
        .compile()
        .inspect(this::checkExpectedSynthetics)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    checkMainDex(mainDexConsumer);
  }

  @Test
  public void testR8() throws Exception {
    MainDexConsumer mainDexConsumer = parameters.isDexRuntime() ? new MainDexConsumer() : null;
    testForR8(parameters.getBackend())
        .debug() // Use debug mode to force a minimal main dex.
        .noMinification() // Disable minification so we can inspect the synthetic names.
        .applyIf(mainDexConsumer != null, b -> b.setProgramConsumer(mainDexConsumer))
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(User1.class.getMethod("testBooleanCompare")),
            Reference.methodFromMethod(User1.class.getMethod("testCharacterCompare")))
        .addMainDexRules(keepMainProguardConfiguration(TestClass.class))
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkExpectedSynthetics);
    if (mainDexConsumer != null) {
      checkMainDex(mainDexConsumer);
    }
  }

  private void checkMainDex(MainDexConsumer mainDexConsumer) throws Exception {
    AndroidApp mainDexApp =
        AndroidApp.builder()
            .addDexProgramData(mainDexConsumer.mainDexBytes, Origin.unknown())
            .build();
    CodeInspector mainDexInspector = new CodeInspector(mainDexApp);

    // The program classes in the main-dex list must be in main-dex.
    assertThat(mainDexInspector.clazz(MiniAssert.class), isPresent());
    assertThat(mainDexInspector.clazz(TestClass.class), isPresent());
    assertThat(mainDexInspector.clazz(User2.class), isPresent());
    assertEquals(getMainDexExpectedSynthetics(), getSyntheticMethods(mainDexInspector));
  }

  private Set<MethodReference> getSyntheticMethods(CodeInspector inspector) {
    Set<ClassReference> nonSyntheticCLasses =
        CLASSES.stream().map(Reference::classFromClass).collect(Collectors.toSet());
    Set<MethodReference> methods = new HashSet<>();
    inspector.allClasses().stream()
        .filter(c -> !nonSyntheticCLasses.contains(c.getFinalReference()))
        .forEach(c -> c.allMethods().forEach(m -> methods.add(m.asMethodReference())));
    return methods;
  }

  private void checkExpectedSynthetics(CodeInspector inspector) throws Exception {
    if (parameters.getApiLevel() == null) {
      assertEquals(Collections.emptySet(), getSyntheticMethods(inspector));
    } else {
      assertEquals(
          Sets.union(getMainDexExpectedSynthetics(), getNonMainDexExpectedSynthetics()),
          getSyntheticMethods(inspector));
    }
  }

  // Hardcoded set of expected synthetics in a "final" build. This set could change if the
  // compiler makes any changes to the naming, sorting or grouping of synthetics. It is hard-coded
  // here to
  // check that the compiler generates this deterministically for any single run or merge of
  // intermediates.

  private ImmutableSet<MethodReference> getNonMainDexExpectedSynthetics()
      throws NoSuchMethodException {
    return ImmutableSet.of(
        SyntheticItemsTestUtils.syntheticMethod(
            User1.class, 1, Boolean.class.getMethod("compare", boolean.class, boolean.class)));
  }

  private ImmutableSet<MethodReference> getMainDexExpectedSynthetics()
      throws NoSuchMethodException {
    return ImmutableSet.of(
        SyntheticItemsTestUtils.syntheticMethod(
            User1.class, 0, Character.class.getMethod("compare", char.class, char.class)),
        SyntheticItemsTestUtils.syntheticMethod(
            User2.class, 0, Integer.class.getMethod("compare", int.class, int.class)));
  }

  static class User1 {

    public static void testBooleanCompare() {
      // These 4 calls should share the same synthetic method.
      MiniAssert.assertTrue(Boolean.compare(true, false) > 0);
      MiniAssert.assertTrue(Boolean.compare(true, true) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, false) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, true) < 0);
    }

    public static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('b', 'a') > 0);
      MiniAssert.assertTrue(Character.compare('a', 'a') == 0);
      MiniAssert.assertTrue(Character.compare('a', 'b') < 0);
    }
  }

  static class User2 {

    public static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('y', 'x') > 0);
      MiniAssert.assertTrue(Character.compare('x', 'x') == 0);
      MiniAssert.assertTrue(Character.compare('x', 'y') < 0);
    }

    public static void testIntegerCompare() {
      // These 3 calls should share the same synthetic method.
      MiniAssert.assertTrue(Integer.compare(2, 0) > 0);
      MiniAssert.assertTrue(Integer.compare(0, 0) == 0);
      MiniAssert.assertTrue(Integer.compare(0, 2) < 0);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      if (args.length == 1) {
        // Reflectively call the backports on User1 which is not in the main-dex list.
        Class<?> user1 = Class.forName(args[0]);
        user1.getMethod("testBooleanCompare").invoke(user1);
        user1.getMethod("testCharacterCompare").invoke(user1);
      }
      User2.testCharacterCompare();
      User2.testIntegerCompare();
      System.out.println("Hello, world");
    }
  }

  private static class MainDexConsumer implements DexIndexedConsumer {

    byte[] mainDexBytes;
    Set<String> mainDexDescriptors;

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (fileIndex == 0) {
        assertNull(mainDexBytes);
        assertNull(mainDexDescriptors);
        mainDexBytes = data.copyByteData();
        mainDexDescriptors = descriptors;
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      assertNotNull(mainDexBytes);
      assertNotNull(mainDexDescriptors);
    }
  }
}
