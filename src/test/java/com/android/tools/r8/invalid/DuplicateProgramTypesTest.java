// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.invalid;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.DuplicateTypesDiagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.SetUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DuplicateProgramTypesTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  public DuplicateProgramTypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final Origin originA =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "SourceA";
        }
      };

  private final Origin originB =
      new Origin(Origin.root()) {
        @Override
        public String part() {
          return "SourceB";
        }
      };

  private void addDuplicateDefinitions(BaseCompilerCommand.Builder<?, ?> builder) throws Exception {
    byte[] bytes = ToolHelper.getClassAsBytes(TestClass.class);
    builder.addClassProgramData(bytes, originA);
    builder.addClassProgramData(bytes, originB);
  }

  private void addResolvingHandler(BaseCompilerCommand.Builder<?, ?> builder) throws Exception {
    builder.setClassConflictResolver(
        (reference, origins, handler) -> {
          assertEquals(
              SetUtils.newIdentityHashSet(originA, originB), SetUtils.newIdentityHashSet(origins));
          return originA;
        });
  }

  private void addNonResolvingHandler(BaseCompilerCommand.Builder<?, ?> builder) throws Exception {
    builder.setClassConflictResolver(
        (reference, origins, handler) -> {
          assertEquals(
              SetUtils.newIdentityHashSet(originA, originB), SetUtils.newIdentityHashSet(origins));
          return null;
        });
  }

  private void checkErrorDiagnostic(TestDiagnosticMessages diagnostics) {
    diagnostics.assertOnlyErrors();
    diagnostics.assertErrorsCount(1);
    DuplicateTypesDiagnostic diagnostic = (DuplicateTypesDiagnostic) diagnostics.getErrors().get(0);
    assertEquals(Position.UNKNOWN, diagnostic.getPosition());
    assertThat(diagnostic.getType(), equalTo(Reference.classFromClass(TestClass.class)));
    assertThat(diagnostic.getOrigin(), anyOf(equalTo(originA), equalTo(originB)));
    assertThat(diagnostic.getOrigins(), hasItems(originA, originB));
    assertThat(
        diagnostic.getDiagnosticMessage(),
        allOf(
            containsString("defined multiple"),
            containsString("SourceA"),
            containsString("SourceB")));
  }

  @Test
  public void testDefaultError() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .setMinApi(parameters.getApiLevel())
                .apply(b -> addDuplicateDefinitions(b.getBuilder()))
                .compileWithExpectedDiagnostics(this::checkErrorDiagnostic));
  }

  @Test
  public void testResolvedConflictD8() throws Exception {
    testForD8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .apply(
            b -> {
              addDuplicateDefinitions(b.getBuilder());
              addResolvingHandler(b.getBuilder());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testResolvedConflictR8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .apply(
            b -> {
              addDuplicateDefinitions(b.getBuilder());
              addResolvingHandler(b.getBuilder());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
  }

  @Test
  public void testNonResolvedConflictD8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(parameters.getBackend())
                .setMinApi(parameters.getApiLevel())
                .apply(
                    b -> {
                      addDuplicateDefinitions(b.getBuilder());
                      addNonResolvingHandler(b.getBuilder());
                    })
                .compileWithExpectedDiagnostics(this::checkErrorDiagnostic));
  }

  @Test
  public void testNonResolvedConflictR8() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .setMinApi(parameters.getApiLevel())
                .addKeepMainRule(TestClass.class)
                .apply(
                    b -> {
                      addDuplicateDefinitions(b.getBuilder());
                      addNonResolvingHandler(b.getBuilder());
                    })
                .compileWithExpectedDiagnostics(this::checkErrorDiagnostic));
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
