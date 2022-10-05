// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.assertionconfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.compilerapi.mockdata.MockClassWithAssertion;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;

public class AssertionConfigurationTest extends CompilerApiTestRunner {

  static MethodReference assertionHandler =
      Reference.methodFromDescriptor(
          "Lcom/example/SomeClass;", "assertionHandler", "(Ljava/lang/AssertionError;)V");

  public AssertionConfigurationTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    assertEquals(assertionHandler, ApiTest.assertionHandler);
    runTest(test::runD8);
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runR8);
  }

  private boolean invokesAssertionHandler(InstructionSubject instruction) {
    return instruction.isInvokeStatic()
        && ((InvokeInstructionSubject) instruction)
            .invokedMethod()
            .asMethodReference()
            .equals(assertionHandler);
  }

  private void runTest(ThrowingBiConsumer<ProgramConsumer, MethodReference, Exception> test)
      throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    test.accept(new DexIndexedConsumer.ArchiveConsumer(output), assertionHandler);

    // TODO(b/209445989): This should be true when the assertion handler support is implemented.
    assertTrue(
        new CodeInspector(output)
            .clazz(MockClassWithAssertion.class)
            .uniqueMethodWithOriginalName("main")
            .streamInstructions()
            .anyMatch(this::invokesAssertionHandler));
  }

  public static class ApiTest extends CompilerApiTest {

    static MethodReference assertionHandler =
        Reference.methodFromDescriptor(
            "Lcom/example/SomeClass;", "assertionHandler", "(Ljava/lang/AssertionError;)V");

    public ApiTest(Object parameters) {
      super(parameters);
    }

    AssertionsConfiguration buildWithAssertionHandler(AssertionsConfiguration.Builder builder) {
      AssertionsConfiguration configuration =
          builder.setAssertionHandler(assertionHandler).setScopeAll().build();
      assertFalse(configuration.isCompileTimeEnabled());
      assertFalse(configuration.isCompileTimeDisabled());
      assertFalse(configuration.isPassthrough());
      assertTrue(configuration.isAssertionHandler());
      assertSame(assertionHandler, configuration.getAssertionHandler());
      return configuration;
    }

    public void runD8(ProgramConsumer programConsumer, MethodReference assertionHandler)
        throws Exception {
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClassWithAssertion()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .addAssertionsConfiguration(this::buildWithAssertionHandler)
              .setProgramConsumer(programConsumer)
              .build());
    }

    public void runR8(ProgramConsumer programConsumer, MethodReference assertionHandler)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClassWithAssertion()), Origin.unknown())
              .addProguardConfiguration(
                  getKeepMainRules(getMockClassWithAssertion()), Origin.unknown())
              .addProguardConfiguration(
                  Collections.singletonList("-dontwarn com.example.SomeClass"), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .addAssertionsConfiguration(
                  builder -> builder.setAssertionHandler(assertionHandler).setScopeAll().build())
              .setProgramConsumer(programConsumer)
              .build());
    }

    @Test
    public void testD8() throws Exception {
      runD8(DexIndexedConsumer.emptyConsumer(), assertionHandler);
    }

    @Test
    public void testR8() throws Exception {
      runR8(DexIndexedConsumer.emptyConsumer(), assertionHandler);
    }
  }
}
