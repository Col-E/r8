// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Reporter;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SeedMapperTests extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SeedMapperTests(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private Path getApplyMappingFile(String... pgMap) throws IOException {
    Path mapPath = temp.newFile().toPath();
    FileUtils.writeTextFile(mapPath, pgMap);
    return mapPath;
  }

  @Test
  public void testNoDuplicates() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int aaaa(B) -> a",
            "  int bbbb(B) -> b",
            "  void cccc() -> a",
            "  B foo       -> a",
            "A.B.D -> b:",
            "  int aaaa(B) -> a");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
    testDiagnosticMessages.assertNoMessages();
  }

  @Test
  public void testDuplicateSourceClasses() throws IOException {
    Path applyMappingFile = getApplyMappingFile("A.B.C -> a:", "A.B.C -> b:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    try {
      SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
      fail("Should have thrown an error");
    } catch (RuntimeException e) {
      assertEquals(1, testDiagnosticMessages.getErrors().size());
      Diagnostic diagnostic = testDiagnosticMessages.getErrors().get(0);
      assertEquals(
          String.format(ProguardMapError.DUPLICATE_SOURCE_MESSAGE, "A.B.C"),
          diagnostic.getDiagnosticMessage());
      assertEquals("line 2", diagnostic.getPosition().getDescription());
    }
  }

  @Test
  public void testDuplicateSourceMethods() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int aaaa(B) -> a",
            "  int aaaa(B) -> a",
            "A.B.D -> b:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    try {
      SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
      // TODO(b/293630963): Re-enable check.
      if (false) {
        fail("Should have thrown an error");
      }
    } catch (RuntimeException e) {
      assertEquals(1, testDiagnosticMessages.getErrors().size());
      Diagnostic diagnostic = testDiagnosticMessages.getErrors().get(0);
      assertEquals(
          String.format(ProguardMapError.DUPLICATE_SOURCE_MEMBER_MESSAGE, "int aaaa(B)", "A.B.C"),
          diagnostic.getDiagnosticMessage());
      assertEquals("line 3", diagnostic.getPosition().getDescription());
    }
  }

  @Test
  public void testDuplicateSourceFields() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int aaaa -> a",
            "  int aaaa -> a",
            "A.B.D -> b:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    try {
      SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
      fail("Should have thrown an error");
    } catch (RuntimeException e) {
      assertEquals(1, testDiagnosticMessages.getErrors().size());
      Diagnostic diagnostic = testDiagnosticMessages.getErrors().get(0);
      assertEquals(
          String.format(ProguardMapError.DUPLICATE_SOURCE_MEMBER_MESSAGE, "int aaaa", "A.B.C"),
          diagnostic.getDiagnosticMessage());
      assertEquals("line 3", diagnostic.getPosition().getDescription());
    }
  }

  @Test
  public void testDuplicateClassTargets() throws IOException {
    Path applyMappingFile = getApplyMappingFile("A.B.C -> a:", "A.B.D -> a:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    try {
      SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
      fail("Should have thrown an error");
    } catch (RuntimeException e) {
      assertEquals(1, testDiagnosticMessages.getErrors().size());
      Diagnostic diagnostic = testDiagnosticMessages.getErrors().get(0);
      assertEquals(
          String.format(ProguardMapError.DUPLICATE_TARGET_MESSAGE, "A.B.D", "A.B.C", "a"),
          diagnostic.getDiagnosticMessage());
      assertEquals("line 2", diagnostic.getPosition().getDescription());
    }
  }

  @Test
  public void testSameNameMethodTargets() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> A:",
            "  int foo(A) -> a",
            "  int bar(B) -> a",
            "  int baz(A,B) -> a",
            "A.B.D -> b:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
    testDiagnosticMessages.assertNoMessages();
  }

  @Test
  @Ignore("b/136697829")
  public void testDuplicateMethodTargets() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int foo(A) -> a",
            "  int bar(A) -> a",
            "A.B.D -> b:");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    try {
      SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
      fail("Should have thrown an error");
    } catch (AbortException e) {
      assertEquals(1, testDiagnosticMessages.getErrors().size());
      Diagnostic diagnostic = testDiagnosticMessages.getErrors().get(0);
      assertEquals(
          String.format(ProguardMapError.DUPLICATE_TARGET_MESSAGE, "int bar(A)", "int foo(A)", "a"),
          diagnostic.getDiagnosticMessage());
      assertEquals("line 2", diagnostic.getPosition().getDescription());
    }
  }

  @Test
  public void testInliningFrames() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int foo(A) -> a",
            "  1:2:int bar(A):3:4 -> a",
            "  1:2:int baz(B):3 -> a");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
  }

  @Test
  public void testDuplicateInliningFrames() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "A.B.C -> a:",
            "  int foo(Z) -> a",
            "  1:1:int qux(A):3:3 -> a",
            "  1:1:int bar(A):3 -> a",
            "  2:2:int qux(A):3:3 -> a",
            "  2:2:int bar(A):4 -> a",
            "  3:3:int bar(A):5:5 -> a",
            "  int qux(C) -> a");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
  }

  @Test
  public void testMultipleInitRanges() throws IOException {
    Path applyMappingFile =
        getApplyMappingFile(
            "com.android.tools.r8.ArchiveClassFileProvider ->"
                + " com.android.tools.r8.ArchiveClassFileProvider:",
            "    1:1:void <init>(java.nio.file.Path):50:50 -> <init>",
            "    2:2:void <init>(java.nio.file.Path):59:59 -> <init>",
            "    boolean lambda$new$0(java.lang.String) -> a");
    TestDiagnosticMessagesImpl testDiagnosticMessages = new TestDiagnosticMessagesImpl();
    Reporter reporter = new Reporter(testDiagnosticMessages);
    SeedMapper.seedMapperFromFile(reporter, applyMappingFile);
  }
}
