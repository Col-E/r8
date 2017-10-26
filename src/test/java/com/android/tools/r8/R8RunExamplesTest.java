// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestCondition.R8_COMPILER;
import static com.android.tools.r8.TestCondition.match;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.R8RunArtTestsTest.CompilerUnderTest;
import com.android.tools.r8.R8RunArtTestsTest.DexTool;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8RunExamplesTest {

  enum Input {
    DX, JAVAC, JAVAC_ALL, JAVAC_NONE
  }

  enum Output {
    DEX,
    CF
  }

  private static final String EXAMPLE_DIR = ToolHelper.EXAMPLES_BUILD_DIR;

  // For local testing on a specific Art version(s) change this set. e.g. to
  // ImmutableSet.of(DexVm.ART_DEFAULT) or pass the option -Ddex_vm=<version> to the Java VM.
  private static final Set<DexVm> artVersions = ToolHelper.getArtVersions();

  // Tests failing to run.
  private static final Map<String, TestCondition> failingRun =
      new ImmutableMap.Builder<String, TestCondition>()
          .put("memberrebinding2.Test", match(R8_COMPILER)) // b/38187737
          .build();

  private static final Map<String, TestCondition> outputNotIdenticalToJVMOutput =
      new ImmutableMap.Builder<String, TestCondition>()
          // Traverses stack frames that contain Art specific frames.
          .put("throwing.Throwing", TestCondition.any())
          // Early art versions incorrectly print Float.MIN_VALUE.
          .put(
              "filledarray.FilledArray",
              TestCondition.match(
                  TestCondition.runtimes(Version.V6_0_1, Version.V5_1_1, Version.V4_4_4)))
          .build();

  @Parameters(name = "{0}_{1}_{2}_{3}_{5}")
  public static Collection<String[]> data() {
    String[] tests = {
        "arithmetic.Arithmetic",
        "arrayaccess.ArrayAccess",
        "barray.BArray",
        "bridge.BridgeMethod",
        "cse.CommonSubexpressionElimination",
        "constants.Constants",
        "controlflow.ControlFlow",
        "conversions.Conversions",
        "floating_point_annotations.FloatingPointValuedAnnotationTest",
        "filledarray.FilledArray",
        "hello.Hello",
        "ifstatements.IfStatements",
        "instancevariable.InstanceVariable",
        "instanceofstring.InstanceofString",
        "invoke.Invoke",
        "jumbostring.JumboString",
        "loadconst.LoadConst",
        "newarray.NewArray",
        "regalloc.RegAlloc",
        "returns.Returns",
        "staticfield.StaticField",
        "stringbuilding.StringBuilding",
        "switches.Switches",
        "sync.Sync",
        "throwing.Throwing",
        "trivial.Trivial",
        "trycatch.TryCatch",
        "nestedtrycatches.NestedTryCatches",
        "trycatchmany.TryCatchMany",
        "invokeempty.InvokeEmpty",
        "regress.Regress",
        "regress2.Regress2",
        "regress_37726195.Regress",
        "regress_37658666.Regress",
        "regress_37875803.Regress",
        "regress_37955340.Regress",
        "regress_62300145.Regress",
        "regress_64881691.Regress",
        "regress_65104300.Regress",
        "memberrebinding2.Memberrebinding",
        "memberrebinding3.Memberrebinding",
        "minification.Minification",
        "enclosingmethod.Main",
        "interfaceinlining.Main",
        "switchmaps.Switches",
    };

    String[] javaBytecodeTests = {
      "hello.Hello",
    };

    List<String[]> fullTestList = new ArrayList<>(tests.length * 2);
    for (String test : tests) {
      fullTestList.add(makeTest(Input.JAVAC, CompilerUnderTest.D8, CompilationMode.DEBUG, test));
      fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.D8, CompilationMode.DEBUG,
          test));
      fullTestList.add(makeTest(Input.JAVAC_NONE, CompilerUnderTest.D8, CompilationMode.DEBUG,
          test));
      fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.D8, CompilationMode.RELEASE,
          test));
      fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.RELEASE,
          test));
      fullTestList.add(makeTest(Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.DEBUG,
          test));
      fullTestList.add(makeTest(Input.DX, CompilerUnderTest.R8, CompilationMode.RELEASE, test));
    }
    // TODO(zerny): Once all tests pass create the java tests in the main test loop.
    for (String test : javaBytecodeTests) {
      fullTestList.add(
          makeTest(Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.DEBUG, test, Output.CF));
      fullTestList.add(
          makeTest(
              Input.JAVAC_ALL, CompilerUnderTest.R8, CompilationMode.RELEASE, test, Output.CF));
    }
    return fullTestList;
  }

  private static String[] makeTest(
      Input input, CompilerUnderTest compiler, CompilationMode mode, String clazz) {
    return makeTest(input, compiler, mode, clazz, Output.DEX);
  }

  private static String[] makeTest(
      Input input, CompilerUnderTest compiler, CompilationMode mode, String clazz, Output output) {
    String pkg = clazz.substring(0, clazz.lastIndexOf('.'));
    return new String[] {pkg, input.name(), compiler.name(), mode.name(), clazz, output.name()};
  }

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private final Input input;
  private final CompilerUnderTest compiler;
  private final CompilationMode mode;
  private final String pkg;
  private final String mainClass;
  private final Output output;

  public R8RunExamplesTest(
      String pkg,
      String input,
      String compiler,
      String mode,
      String mainClass,
      String output) {
    this.pkg = pkg;
    this.input = Input.valueOf(input);
    this.compiler = CompilerUnderTest.valueOf(compiler);
    this.mode = CompilationMode.valueOf(mode);
    this.mainClass = mainClass;
    this.output = Output.valueOf(output);
  }

  private Path getOutputFile() {
    return temp.getRoot().toPath().resolve("out.jar");
  }

  private Path getInputFile() {
    switch(input) {
      case DX:
        return getOriginalDexFile();
      case JAVAC:
        return getOriginalJarFile("");
      case JAVAC_ALL:
        return getOriginalJarFile("_debuginfo_all");
      case JAVAC_NONE:
        return getOriginalJarFile("_debuginfo_none");
      default:
        throw new Unreachable();
    }
  }

  public Path getOriginalJarFile(String postFix) {
    return Paths.get(EXAMPLE_DIR, pkg + postFix + JAR_EXTENSION);
  }

  private Path getOriginalDexFile() {
    return Paths.get(EXAMPLE_DIR, pkg, ToolHelper.DEFAULT_DEX_FILENAME);
  }

  private DexTool getTool() {
    return input == Input.DX ? DexTool.DX : DexTool.NONE;
  }

  private Path getOutputPath() {
    return temp.getRoot().toPath().resolve("out.jar");
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Before
  public void compile()
      throws IOException, ProguardRuleParserException, ExecutionException, CompilationException {
    switch (compiler) {
      case D8: {
        assertTrue(output == Output.DEX);
        ToolHelper.runD8(D8Command.builder()
            .addProgramFiles(getInputFile())
            .setOutputPath(getOutputFile())
            .setMode(mode)
            .build());
        break;
      }
      case R8: {
        ToolHelper.runR8(R8Command.builder()
            .addProgramFiles(getInputFile())
            .setOutputPath(getOutputFile())
            .setMode(mode)
            .build(),
            options -> options.outputClassFiles = (output == Output.CF));
        break;
      }
      default:
        throw new Unreachable();
    }
  }

  @Test
  public void outputIsIdentical() throws IOException, InterruptedException, ExecutionException {
    if (!ToolHelper.artSupported()) {
      return;
    }

    String original = getOriginalDexFile().toString();
    Path generated = getOutputFile();

    ToolHelper.ProcessResult javaResult =
        ToolHelper.runJava(ImmutableList.of(getOriginalJarFile("").toString()), mainClass);
    if (javaResult.exitCode != 0) {
      fail("JVM failed for: " + mainClass);
    }

    if (output == Output.CF) {
      ToolHelper.ProcessResult result =
          ToolHelper.runJava(ImmutableList.of(generated.toString()), mainClass);
      if (result.exitCode != 0) {
        System.err.println(result.stderr);
        fail("JVM failed on compiled output for: " + mainClass);
      }
      assertEquals(
          "JavaC/JVM and " + compiler.name() + "/JVM output differ",
          javaResult.stdout,
          result.stdout);
      return;
    }

    DexVm vm = ToolHelper.getDexVm();
    TestCondition condition = failingRun.get(mainClass);
    if (condition != null && condition.test(getTool(), compiler, vm.getVersion(), mode)) {
      thrown.expect(Throwable.class);
    } else {
      thrown = ExpectedException.none();
    }

    // Check output against Art output on original dex file.
    String output =
        ToolHelper.checkArtOutputIdentical(original, generated.toString(), mainClass, vm);

    // Check output against JVM output.
    if (shouldMatchJVMOutput(vm.getVersion())) {
      String javaOutput = javaResult.stdout;
      assertEquals("JVM and Art output differ", javaOutput, output);
    }
  }

  private boolean shouldMatchJVMOutput(DexVm.Version version) {
    TestCondition condition = outputNotIdenticalToJVMOutput.get(mainClass);
    return condition == null || !condition.test(getTool(), compiler, version, mode);
  }
}
