// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.DexSegments;
import com.android.tools.r8.DexSegments.Command;
import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.startup.utils.MixedSectionLayoutInspector;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexCodeDeduppingTest extends TestBase {
  private final TestParameters parameters;
  private static final List<String> EXPECTED = ImmutableList.of("foo", "bar", "foo", "bar");

  private static final int ONE_CLASS_COUNT = 4;
  private static final int ONE_CLASS_DEDUPLICATED_COUNT = 3;

  private static final int TWO_CLASS_COUNT = 6;
  private static final int TWO_CLASS_DEDUPLICATED_COUNT = 3;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexCodeDeduppingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8SingleClass() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(Foo.class)
            .setMinApi(parameters)
            .addKeepAllClassesRule()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooSizes(compile.writeToZip());
  }

  @Test
  public void testR8WithLinesSingleClass() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(Foo.class)
            .setMinApi(parameters)
            .addKeepAllClassesRule()
            .addKeepAttributeLineNumberTable()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooSizes(compile.writeToZip());
  }

  @Test
  public void testD8SingleClassMappingOutput() throws Exception {
    D8TestCompileResult compile =
        testForD8(parameters.getBackend())
            .addProgramClasses(Foo.class)
            .setMinApi(parameters)
            .release()
            .internalEnableMappingOutput()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooSizes(compile.writeToZip());
  }

  @Test
  public void testD8SingleClassNoMappingOutput() throws Exception {
    D8TestCompileResult compile =
        testForD8(parameters.getBackend())
            .addProgramClasses(Foo.class)
            .setMinApi(parameters)
            .release()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    // When d8 has no map output we can't share debug info and hence can't share code.
    assertSizes(compile.writeToZip(), 4, 4);
  }

  @Test
  public void testR8TwoClasses() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(Foo.class, Bar.class)
            .setMinApi(parameters)
            .addOptionsModification(
                options ->
                    options
                        .getTestingOptions()
                        .setMixedSectionLayoutStrategyInspector(
                            codeLayoutInspector(
                                parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S),
                                true)))
            .addKeepAllClassesRule()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooAndBarSizes(compile.writeToZip());
  }

  @Test
  public void testR8WithLinesTwoClasses() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addProgramClasses(Foo.class, Bar.class)
            .addKeepAttributeLineNumberTable()
            .setMinApi(parameters)
            .addOptionsModification(
                options ->
                    options
                        .getTestingOptions()
                        .setMixedSectionLayoutStrategyInspector(
                            codeLayoutInspector(
                                parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S),
                                true)))
            .addKeepAllClassesRule()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooAndBarSizes(compile.writeToZip());
  }

  @Test
  public void testD8TwoClassesMappingOutput() throws Exception {
    D8TestCompileResult compile =
        testForD8(parameters.getBackend())
            .addProgramClasses(Foo.class, Bar.class)
            .setMinApi(parameters)
            .addOptionsModification(
                options ->
                    options
                        .getTestingOptions()
                        .setMixedSectionLayoutStrategyInspector(
                            codeLayoutInspector(
                                parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S),
                                true)))
            .release()
            .internalEnableMappingOutput()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    assertFooAndBarSizes(compile.writeToZip());
  }

  @Test
  public void testD8TwoClassesNoMappingOutput() throws Exception {
    D8TestCompileResult compile =
        testForD8(parameters.getBackend())
            .addProgramClasses(Foo.class, Bar.class)
            .setMinApi(parameters)
            .addOptionsModification(
                options ->
                    options
                        .getTestingOptions()
                        .setMixedSectionLayoutStrategyInspector(codeLayoutInspector(false, true)))
            .release()
            .compile();
    compile.run(parameters.getRuntime(), Foo.class).assertSuccessWithOutputLines(EXPECTED);
    // When d8 has no map output we can't share debug info and hence can't share code.
    assertSizes(compile.writeToZip(), 6, 6);
  }

  private MixedSectionLayoutInspector codeLayoutInspector(
      boolean assumeDeduplicated, boolean twoClasses) {
    return new MixedSectionLayoutInspector() {
      @Override
      public void inspectCodeLayout(int virtualFile, Collection<ProgramMethod> layout) {
        // We have as many methods, the ones sharing code are just last.
        assertTrue(layout.size() == (twoClasses ? TWO_CLASS_COUNT : ONE_CLASS_COUNT));

        ArrayList<ProgramMethod> programMethods = new ArrayList<>(layout);
        if (twoClasses) {
          if (assumeDeduplicated) {
            // The two init methods are shared.
            // The two foo methods AND the bar method are shared, so they should be last.
            // We sort by count, then fully qualified name.
            assertTrue(programMethods.get(0).toString().contains("Foo.main"));
            assertTrue(programMethods.get(1).toString().contains("Bar.<init>"));
            assertTrue(programMethods.get(2).toString().contains("Foo.<init>"));
            assertTrue(programMethods.get(3).toString().contains("Bar.foo"));
            assertTrue(programMethods.get(4).toString().contains("Foo.bar"));
            assertTrue(programMethods.get(5).toString().contains("Foo.foo"));
          } else {
            // We sort by name.
            assertTrue(programMethods.get(0).toString().contains("Bar.<init>"));
            assertTrue(programMethods.get(1).toString().contains("Bar.foo"));
            assertTrue(programMethods.get(2).toString().contains("Foo.<init>"));
            assertTrue(programMethods.get(3).toString().contains("Foo.bar"));
            assertTrue(programMethods.get(4).toString().contains("Foo.foo"));
            assertTrue(programMethods.get(5).toString().contains("Foo.main"));
          }
        }
      }
    };
  }

  private void assertFooSizes(Path output) throws Exception {
    assertSizes(output, ONE_CLASS_DEDUPLICATED_COUNT, ONE_CLASS_COUNT);
  }

  private void assertFooAndBarSizes(Path output) throws Exception {
    assertSizes(output, TWO_CLASS_DEDUPLICATED_COUNT, TWO_CLASS_COUNT);
  }

  private void assertSizes(Path output, int deduppedSize, int originalSize)
      throws CompilationFailedException, ResourceException, IOException {
    if (parameters.isDexRuntime()) {
      SegmentInfo codeSegmentInfo = getCodeSegmentInfo(output);
      if (parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.S)) {
        assertEquals(codeSegmentInfo.getItemCount(), deduppedSize);
      } else {
        assertEquals(codeSegmentInfo.getItemCount(), originalSize);
      }
    }
  }

  public SegmentInfo getCodeSegmentInfo(Path path)
      throws CompilationFailedException, ResourceException, IOException {
    Command.Builder builder = Command.builder().addProgramFiles(path);
    Map<Integer, SegmentInfo> segmentInfoMap = DexSegments.run(builder.build());
    return segmentInfoMap.get(Constants.TYPE_CODE_ITEM);
  }

  public static class Foo {
    public static void main(String[] args) {
      foo();
      bar();
    }

    public static void foo() {
      if (System.currentTimeMillis() == 0) {
        System.out.println("That was early");
      } else {
        System.out.println("foo");
      }
      System.out.println("bar");
    }

    public static void bar() {
      if (System.currentTimeMillis() == 0) {
        System.out.println("That was early");
      } else {
        System.out.println("foo");
      }
      System.out.println("bar");
    }
  }

  public static class Bar {
    public static void foo() {
      if (System.currentTimeMillis() == 0) {
        System.out.println("That was early");
      } else {
        System.out.println("foo");
      }
      System.out.println("bar");
    }
  }
}
