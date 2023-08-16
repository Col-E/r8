// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.backports;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.desugar.backports.AbstractBackportTest.MiniAssert;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackportDuplicationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User1.class, User2.class);

  static final List<String> CLASS_TYPE_NAMES =
      CLASSES.stream().map(Class::getTypeName).collect(Collectors.toList());

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withAllRuntimes()
        .withApiLevel(AndroidApiLevel.J)
        .enableApiLevelsForCf()
        .build();
  }

  public BackportDuplicationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    // R8 does not support desugaring with class file output so this test is only valid for DEX.
    assumeTrue(parameters.isDexRuntime());
    runR8(false);
    runR8(true);
  }

  private void runR8(boolean minify) throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .setMinApi(parameters)
        .minification(minify)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoOriginalsAndNoInternalSynthetics);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoOriginalsAndNoInternalSynthetics)
        .inspect(this::checkExpectedSynthetics);
  }

  @Test
  public void testD8Merging() throws Exception {
    boolean intermediate = true;
    runD8Merging(intermediate);
  }

  @Test
  public void testD8MergingNonIntermediate() throws Exception {
    boolean intermediate = false;
    runD8Merging(intermediate);
  }

  private void runD8Merging(boolean intermediate) throws Exception {
    // Compile part 1 of the input (maybe intermediate)
    Path out1 =
        testForD8(parameters.getBackend())
            .addProgramClasses(User1.class)
            .addClasspathClasses(CLASSES)
            .setMinApi(parameters)
            .setIntermediate(intermediate)
            .compile()
            .writeToZip();

    // Compile part 2 of the input (maybe intermediate)
    Path out2 =
        testForD8(parameters.getBackend())
            .addProgramClasses(User2.class)
            .addClasspathClasses(CLASSES)
            .setMinApi(parameters)
            .setIntermediate(intermediate)
            .compile()
            .writeToZip();

    SetView<MethodReference> syntheticsInParts =
        Sets.union(
            getSyntheticMethods(new CodeInspector(out1)),
            getSyntheticMethods(new CodeInspector(out2)));

    // Merge parts as an intermediate artifact.
    // This will not merge synthetics regardless of the setting of intermediate.
    Path out3 = temp.newFolder().toPath().resolve("out3.zip");
    testForD8(parameters.getBackend())
        .addProgramClasses(MiniAssert.class, TestClass.class)
        .addProgramFiles(out1, out2)
        .setMinApi(parameters)
        .setIntermediate(true)
        .compile()
        .writeToZip(out3)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoOriginalsAndNoInternalSynthetics)
        .inspect(inspector -> assertEquals(syntheticsInParts, getSyntheticMethods(inspector)));

    // Finally do a non-intermediate merge.
    testForD8(parameters.getBackend())
        .addProgramFiles(out3)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoOriginalsAndNoInternalSynthetics)
        .inspect(
            inspector -> {
              if (intermediate) {
                // If all previous builds where intermediate then synthetics are merged.
                checkExpectedSynthetics(inspector);
              } else {
                // Otherwise merging non-intermediate artifacts, synthetics will not be identified.
                // Check that they are exactly as in the part inputs.
                assertEquals(syntheticsInParts, getSyntheticMethods(inspector));
              }
            });
  }

  @Test
  public void testD8FilePerClassFile() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    runD8FilePerMode(OutputMode.DexFilePerClassFile);
  }

  @Test
  public void testD8FilePerClass() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    runD8FilePerMode(OutputMode.DexFilePerClass);
  }

  public void runD8FilePerMode(OutputMode outputMode) throws Exception {
    Path perClassOutput =
        testForD8(parameters.getBackend())
            .setOutputMode(outputMode)
            .addProgramClasses(CLASSES)
            .setMinApi(parameters)
            .compile()
            .writeToZip();
    testForD8()
        .addProgramFiles(perClassOutput)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoOriginalsAndNoInternalSynthetics)
        .inspect(this::checkExpectedSynthetics);
  }

  @Test
  public void testPerFileIntermediate() throws Exception {
    ProcessResult result = runDoublePerFileCompilation(Backend.CF, true);
    assertEquals(result.toString(), 0, result.exitCode);
    assertEquals(EXPECTED, result.stdout);
  }

  @Test
  public void testPerFileNonIntermediate() throws Exception {
    try {
      runDoublePerFileCompilation(Backend.CF, false);
      fail("Should expect the compilation to fail.");
    } catch (CompilationFailedException e) {
      assertThat(
          e.getCause().getMessage(),
          containsString("Attempt at compiling intermediate artifact without its context"));
    }
  }

  @Test
  public void testPerFileNonIntermediateDex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    try {
      runDoublePerFileCompilation(Backend.DEX, false);
      fail("Should expect the compilation to fail.");
    } catch (CompilationFailedException e) {
      assertThat(
          e.getCause().getMessage(),
          containsString("Attempt at compiling intermediate artifact without its context"));
    }
  }

  public ProcessResult runDoublePerFileCompilation(Backend firstRoundOutput, boolean intermediate)
      throws Exception {
    List<byte[]> outputsRoundOne = Collections.synchronizedList(new ArrayList<>());
    testForD8(firstRoundOutput)
        .addProgramClasses(CLASSES)
        .setMinApi(parameters)
        .setIntermediate(true /* First round is always intermediate. */)
        .setProgramConsumer(
            firstRoundOutput.isCf()
                ? new ClassFileConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                    byte[] bytes = data.copyByteData();
                    assert bytes != null;
                    outputsRoundOne.add(bytes);
                  }
                }
                : new DexFilePerClassFileConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      String primaryClassDescriptor,
                      ByteDataView data,
                      Set<String> descriptors,
                      DiagnosticsHandler handler) {
                    byte[] bytes = data.copyByteData();
                    assert bytes != null;
                    outputsRoundOne.add(bytes);
                  }

                  @Override
                  public boolean combineSyntheticClassesWithPrimaryClass() {
                    return false;
                  }
                })
        .compile();

    List<Path> outputsRoundTwo = new ArrayList<>();
    for (byte[] bytes : outputsRoundOne) {
      assert bytes != null;
      outputsRoundTwo.add(
          testForD8(parameters.getBackend())
              .applyIf(
                  firstRoundOutput.isCf(),
                  b -> b.addProgramClassFileData(bytes),
                  b -> b.addProgramDexFileData(bytes))
              .setMinApi(parameters)
              .setIntermediate(intermediate)
              .compile()
              .writeToZip());
    }

    if (parameters.isCfRuntime()) {
      return ToolHelper.runJava(
          parameters.getRuntime().asCf(), outputsRoundTwo, TestClass.class.getTypeName());
    } else {
      ArtCommandBuilder builder = new ArtCommandBuilder();
      builder.setMainClass(TestClass.class.getTypeName());
      outputsRoundTwo.forEach(p -> builder.appendClasspath(p.toAbsolutePath().toString()));
      return ToolHelper.runArtRaw(builder);
    }
  }

  private void checkNoOriginalsAndNoInternalSynthetics(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          SyntheticNaming.verifyNotInternalSynthetic(clazz.getFinalReference());
          if (!clazz.getOriginalName().equals(MiniAssert.class.getTypeName())) {
            clazz.forAllMethods(
                method ->
                    assertTrue(
                        "Unexpected static invoke to java.lang method:\n"
                            + method.getMethod().codeToString(),
                        method
                            .streamInstructions()
                            .filter(InstructionSubject::isInvokeStatic)
                            .noneMatch(
                                i -> i.getMethod().qualifiedName().startsWith("java.lang"))));
          }
        });
  }

  private Set<MethodReference> getSyntheticMethods(CodeInspector inspector) {
    Set<MethodReference> methods = new HashSet<>();
    inspector.allClasses().stream()
        .filter(c -> !CLASS_TYPE_NAMES.contains(c.getFinalName()))
        .forEach(c -> c.allMethods().forEach(m -> methods.add(m.asMethodReference())));
    return methods;
  }

  private void checkExpectedSynthetics(CodeInspector inspector) throws Exception {
    // Hardcoded set of expected synthetics in a "final" build. This set could change if the
    // compiler makes any changes to the naming, sorting or grouping of synthetics. It is hard-coded
    // here to check that the compiler generates this deterministically for any single run or merge
    // of intermediates.
    Set<MethodReference> expectedSynthetics =
        ImmutableSet.of(
            SyntheticItemsTestUtils.syntheticBackportMethod(
                User1.class, 1, Boolean.class.getMethod("compare", boolean.class, boolean.class)),
            SyntheticItemsTestUtils.syntheticBackportMethod(
                User1.class, 0, Character.class.getMethod("compare", char.class, char.class)),
            SyntheticItemsTestUtils.syntheticBackportMethod(
                User2.class, 0, Integer.class.getMethod("compare", int.class, int.class)));
    assertEquals(expectedSynthetics, getSyntheticMethods(inspector));
  }

  static class User1 {

    private static void testBooleanCompare() {
      // These 4 calls should share the same synthetic method.
      MiniAssert.assertTrue(Boolean.compare(true, false) > 0);
      MiniAssert.assertTrue(Boolean.compare(true, true) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, false) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, true) < 0);
    }

    private static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('b', 'a') > 0);
      MiniAssert.assertTrue(Character.compare('a', 'a') == 0);
      MiniAssert.assertTrue(Character.compare('a', 'b') < 0);
    }
  }

  static class User2 {

    private static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('y', 'x') > 0);
      MiniAssert.assertTrue(Character.compare('x', 'x') == 0);
      MiniAssert.assertTrue(Character.compare('x', 'y') < 0);
    }

    private static void testIntegerCompare() {
      // These 3 calls should share the same synthetic method.
      MiniAssert.assertTrue(Integer.compare(2, 0) > 0);
      MiniAssert.assertTrue(Integer.compare(0, 0) == 0);
      MiniAssert.assertTrue(Integer.compare(0, 2) < 0);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      User1.testBooleanCompare();
      User1.testCharacterCompare();
      User2.testCharacterCompare();
      User2.testIntegerCompare();
      System.out.println("Hello, world");
    }
  }
}
