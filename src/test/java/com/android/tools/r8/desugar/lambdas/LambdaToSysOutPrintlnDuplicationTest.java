// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LambdaToSysOutPrintlnDuplicationTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("User1", "User2");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(TestClass.class, MyConsumer.class, Accept.class, User1.class, User2.class);

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

  public LambdaToSysOutPrintlnDuplicationTest(TestParameters parameters) {
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
    ;
  }

  @Test
  public void testD8Merging() throws Exception {
    assumeTrue(
        "b/147485959: Merging does not happen for CF due to lack of synthetic annotations",
        parameters.isDexRuntime());
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
        .addProgramClasses(TestClass.class, MyConsumer.class, Accept.class)
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

  private void checkNoOriginalsAndNoInternalSynthetics(CodeInspector inspector) {
    inspector.forAllClasses(
        clazz -> {
          assertFalse(SyntheticItemsTestUtils.isInternalLambda(clazz.getFinalReference()));
          clazz.forAllMethods(
              method ->
                  assertTrue(
                      "Unexpected invoke dynamic:\n" + method.getMethod().codeToString(),
                      method.isAbstract()
                          || method
                              .streamInstructions()
                              .noneMatch(InstructionSubject::isInvokeDynamic)));
        });
  }

  private Set<MethodReference> getSyntheticMethods(CodeInspector inspector) {
    Set<MethodReference> methods = new HashSet<>();
    inspector.allClasses().stream()
        .filter(c -> !CLASS_TYPE_NAMES.contains(c.getFinalName()))
        .forEach(
            c ->
                c.allMethods(m -> !m.isInstanceInitializer())
                    .forEach(m -> methods.add(m.asMethodReference())));
    return methods;
  }

  private void checkExpectedSynthetics(CodeInspector inspector) throws Exception {
    // Hardcoded set of expected synthetics in a "final" build. This set could change if the
    // compiler makes any changes to the naming, sorting or grouping of synthetics. It is hard-coded
    // here to check that the compiler generates this deterministically for any single run or merge
    // of intermediates.
    Set<MethodReference> expectedSynthetics =
        ImmutableSet.of(
            SyntheticItemsTestUtils.syntheticLambdaMethod(
                User1.class, 0, MyConsumer.class.getMethod("accept", Object.class)));
    assertEquals(expectedSynthetics, getSyntheticMethods(inspector));
  }

  interface MyConsumer {
    void accept(Object o);
  }

  static class Accept {
    public static void accept(Object o, MyConsumer consumer) {
      consumer.accept(o);
    }
  }

  static class User1 {

    private static void testSystemPrintln() {
      Accept.accept("User1", System.out::println);
    }
  }

  static class User2 {

    private static void testSystemPrintln() {
      Accept.accept("User2", System.out::println);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      User1.testSystemPrintln();
      User2.testSystemPrintln();
    }
  }
}
