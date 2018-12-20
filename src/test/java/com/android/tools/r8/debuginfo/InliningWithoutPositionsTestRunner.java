// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debuginfo.InliningWithoutPositionsTestSourceDump.Location;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRangesOfName;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningWithoutPositionsTestRunner {

  enum Backend {
    CF,
    DEX
  }

  private static final String TEST_CLASS = "InliningWithoutPositionsTestSource";
  private static final String TEST_PACKAGE = "com.android.tools.r8.debuginfo";

  @ClassRule public static TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  private final Backend backend;
  private final boolean mainPos;
  private final boolean foo1Pos;
  private final boolean barPos;
  private final boolean foo2Pos;
  private final Location throwLocation;

  @Parameters(name = "{0}: main/foo1/bar/foo2 positions: {1}/{2}/{3}/{4}, throwLocation: {5}")
  public static Collection<Object[]> data() {
    List<Object[]> testCases = new ArrayList<>();
    for (Backend backend : Backend.values()) {
    for (int i = 0; i < 16; ++i) {
      for (Location throwLocation : Location.values()) {
        if (throwLocation != Location.MAIN) {
            testCases.add(
                new Object[] {
                  backend, (i & 1) != 0, (i & 2) != 0, (i & 4) != 0, (i & 8) != 0, throwLocation
                });
        }
      }
      }
    }
    return testCases;
  }

  public InliningWithoutPositionsTestRunner(
      Backend backend,
      boolean mainPos,
      boolean foo1Pos,
      boolean barPos,
      boolean foo2Pos,
      Location throwLocation) {
    this.backend = backend;
    this.mainPos = mainPos;
    this.foo1Pos = foo1Pos;
    this.barPos = barPos;
    this.foo2Pos = foo2Pos;
    this.throwLocation = throwLocation;
  }

  @Test
  public void testStackTrace() throws Exception {
    // See InliningWithoutPositionsTestSourceDump for the code compiled here.
    Path testClassDir = temp.newFolder(TEST_PACKAGE.split(".")).toPath();
    Path testClassPath = testClassDir.resolve(TEST_CLASS + ".class");
    Path outputPath = temp.newFolder().toPath();

    Files.write(
        testClassPath,
        InliningWithoutPositionsTestSourceDump.dump(
            mainPos, foo1Pos, barPos, foo2Pos, throwLocation));

    Path proguardMapPath = testClassDir.resolve("proguard.map");

    R8Command.Builder builder =
        R8Command.builder()
            .addProgramFiles(testClassPath)
            .setMode(CompilationMode.RELEASE)
            .setProguardMapOutputPath(proguardMapPath);
    if (backend == Backend.DEX) {
      AndroidApiLevel minSdk = ToolHelper.getMinApiLevelForDexVm();
      builder
          .setMinApiLevel(minSdk.getLevel())
          .addLibraryFiles(ToolHelper.getAndroidJar(minSdk))
          .setOutput(outputPath, OutputMode.DexIndexed);
    } else {
      assert (backend == Backend.CF);
      builder
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
          .setOutput(outputPath, OutputMode.ClassFile);
    }
    builder
        .setDisableTreeShaking(true)
        .setDisableMinification(true)
        .addProguardConfiguration(
            ImmutableList.of("-keepattributes SourceFile,LineNumberTable"), Origin.unknown());

    ToolHelper.runR8(builder.build(), options -> options.inliningInstructionLimit = 40);

    ProcessResult result;
    if (backend == Backend.DEX) {
      ArtCommandBuilder artCommandBuilder = new ArtCommandBuilder();
      artCommandBuilder.appendClasspath(outputPath.resolve("classes.dex").toString());
      artCommandBuilder.setMainClass(TEST_PACKAGE + "." + TEST_CLASS);

      result = ToolHelper.runArtRaw(artCommandBuilder);
    } else {
      result = ToolHelper.runJava(outputPath, TEST_PACKAGE + "." + TEST_CLASS);
    }
    assertNotEquals(result.exitCode, 0);

    // Verify stack trace.
    // result.stderr looks like this:
    //
    //     Exception in thread "main" java.lang.RuntimeException: <FOO1-exception>
    //       at
    // com.android.tools.r8.debuginfo.InliningWithoutPositionsTestSource.main(InliningWithoutPositionsTestSource.java:1)
    String[] lines = result.stderr.split("\n");

    // The line containing 'java.lang.RuntimeException' should contain the expected message, which
    // is "LOCATIONCODE-exception>"
    int i = 0;
    boolean foundException = false;
    for (; i < lines.length && !foundException; ++i) {
      boolean hasExpectedException = lines[i].contains("<" + throwLocation + "-exception>");
      if (lines[i].contains("java.lang.RuntimeException")) {
        assertTrue(hasExpectedException);
        foundException = true;
      } else {
        assertFalse(hasExpectedException);
      }
    }
    assertTrue(foundException);

    // The next line, the stack trace, must always be the same, indicating 'main' and line = 1 or 2.
    Assert.assertTrue(i < lines.length);
    String line = lines[i].trim();
    assertTrue(line.startsWith("at " + TEST_PACKAGE + "." + TEST_CLASS + "." + "main"));

    // It must contain the '<source-file>:1' or ':2', if we're throwing at foo2.
    int expectedLineNumber = throwLocation == Location.FOO2 ? 2 : 1;
    String expectedFilePos = TEST_CLASS + ".java:" + expectedLineNumber;
    int idx = line.indexOf(expectedFilePos);
    assertTrue(idx >= 0);

    // And the next character must be a non-digit or nothing.
    int idxAfter = idx + expectedFilePos.length();
    assertTrue(idxAfter == line.length() || !Character.isDigit(line.charAt(idxAfter)));

    // Reading the Proguard map. An example map (only the relevant part, 'main'):
    //
    //     1:1:void bar():0:0 -> main
    //     1:1:void foo(boolean):0 -> main
    //     1:1:void main(java.lang.String[]):0 -> main
    ClassNameMapper mapper = ClassNameMapper.mapperFromFile(proguardMapPath);
    assertNotNull(mapper);

    ClassNamingForNameMapper classNaming = mapper.getClassNaming(TEST_PACKAGE + "." + TEST_CLASS);
    assertNotNull(classNaming);

    MappedRangesOfName rangesForMain = classNaming.mappedRangesByRenamedName.get("main");
    assertNotNull(rangesForMain);

    List<MappedRange> frames = rangesForMain.allRangesForLine(expectedLineNumber);

    switch (throwLocation) {
      case FOO1:
        assertEquals(2, frames.size());
        assertFrame(true, "foo", Location.FOO1, foo1Pos, frames.get(0));
        break;
      case BAR:
        assertEquals(3, frames.size());
        assertFrame(true, "bar", Location.BAR, barPos, frames.get(0));
        assertFrame(false, "foo", Location.FOO1, foo1Pos, frames.get(1));
        break;
      case FOO2:
        assertEquals(2, frames.size());
        // If there's no foo2Pos then we expect foo1 pos at this location.
        if (foo2Pos) {
          assertFrame(true, "foo", Location.FOO2, true, frames.get(0));
        } else {
          assertFrame(true, "foo", Location.FOO1, foo1Pos, frames.get(0));
        }
        break;
      default:
        Assert.fail();
    }
    assertFrame(false, "main", Location.MAIN, mainPos, frames.get(frames.size() - 1));
  }

  private void assertFrame(
      boolean innermostFrame,
      String function,
      Location location,
      boolean hasPosition,
      MappedRange range) {
    assertEquals(function, range.signature.name);
    int expectedLineNumber = hasPosition ? location.line : 0;
    Object expectedOriginalRange =
        innermostFrame ? new Range(expectedLineNumber, expectedLineNumber) : expectedLineNumber;
    assertEquals(expectedOriginalRange, range.originalRange);
  }
}
