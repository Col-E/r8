// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.LibraryDesugaringTestConfiguration.AbsentKeepRuleConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IteratorTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private final boolean canUseDefaultAndStaticInterfaceMethods;

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "2", "3");

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  public IteratorTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
    this.canUseDefaultAndStaticInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testIterator() throws Exception {
    if (parameters.isCfRuntime()) {
      testForJvm()
          .addInnerClasses(IteratorTest.class)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
      return;
    }
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
        .addInnerClasses(IteratorTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Cf() throws Exception {
    // Use D8 to desugar with Java classfile output.
    Path firstJar =
        testForD8(Backend.CF)
            .setMinApi(parameters.getApiLevel())
            .addProgramClasses(Main.class, MyIterator.class)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), new AbsentKeepRuleConsumer())
            .compile()
            .writeToZip();

    ClassFileInfo info =
        extractClassFileInfo(
            ZipUtils.readSingleEntry(firstJar, ZipUtils.zipEntryNameForClass(MyIterator.class)));
    assertEquals(
        MyIterator.class.getTypeName(),
        DescriptorUtils.getJavaTypeFromBinaryName(info.getClassBinaryName()));
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 0 : 1,
        info.getInterfaces().stream().filter(name -> name.equals("j$/util/Iterator")).count());
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 1 : 2,
        info.getMethodNames().stream().filter(name -> name.equals("forEachRemaining")).count());

    AndroidApiLevel apiLevelNotRequiringDesugaring = AndroidApiLevel.N;
    if (parameters.getApiLevel().isLessThan(apiLevelNotRequiringDesugaring)) {
      try {
        // Use D8 to desugar with Java classfile output.
        testForD8(Backend.CF)
            .setMinApi(parameters.getApiLevel())
            .addProgramFiles(firstJar)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), new AbsentKeepRuleConsumer())
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics.assertErrorsMatch(
                        diagnosticMessage(
                            containsString(
                                "Code has already been library desugared. "
                                    + "Interface Lj$/util/Iterator; is already implemented by "
                                    + "Lcom/android/tools/r8/desugar/desugaredlibrary/"
                                    + "IteratorTest$MyIterator;"))));
        fail("Expected failure");
      } catch (CompilationFailedException e) {
        // Expected.
      }
    }

    // Use D8 to desugar with Java classfile output.
    Path secondJar =
        testForD8(Backend.CF)
            .addOptionsModification(
                options ->
                    options.desugarSpecificOptions().allowAllDesugaredInput =
                        parameters.getApiLevel().isLessThan(apiLevelNotRequiringDesugaring))
            .setMinApi(parameters.getApiLevel())
            .addProgramFiles(firstJar)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), new AbsentKeepRuleConsumer())
            .compile()
            .writeToZip();

    info =
        extractClassFileInfo(
            ZipUtils.readSingleEntry(secondJar, ZipUtils.zipEntryNameForClass(MyIterator.class)));
    assertEquals(
        MyIterator.class.getTypeName(),
        DescriptorUtils.getJavaTypeFromBinaryName(info.getClassBinaryName()));
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 0 : 1,
        info.getInterfaces().stream().filter(name -> name.equals("j$/util/Iterator")).count());
    assertEquals(
        canUseDefaultAndStaticInterfaceMethods ? 1 : 2,
        info.getMethodNames().stream().filter(name -> name.equals("forEachRemaining")).count());

    if (parameters.getRuntime().isDex()) {
      // Convert to DEX without desugaring and run.
      testForD8()
          .addProgramFiles(firstJar)
          .setMinApi(parameters.getApiLevel())
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary,
              parameters.getApiLevel(),
              collectKeepRulesWithTraceReferences(
                  firstJar, buildDesugaredLibraryClassFile(parameters.getApiLevel())),
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      // Run on the JVM with desugared library on classpath.
      testForJvm()
          .addProgramFiles(firstJar)
          .addRunClasspathFiles(buildDesugaredLibraryClassFile(parameters.getApiLevel()))
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  static class Main {

    public static void main(String[] args) {
      Iterator<Integer> iterator = new MyIterator<>(1, 2, 3);
      iterator.forEachRemaining(System.out::println);
    }
  }

  static class MyIterator<E> implements Iterator<E> {

    int index;
    E[] items;

    @SafeVarargs
    public MyIterator(E... items) {
      this.items = items;
    }

    @Override
    public boolean hasNext() {
      return index < items.length;
    }

    @Override
    public E next() {
      return items[index++];
    }

    @Override
    public void forEachRemaining(Consumer<? super E> action) {
      while (hasNext()) {
        action.accept(next());
      }
    }
  }
}
