// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingLibraryMemberRenamingTests extends TestBase {

  public static class Dto {
    public final String result;

    public Dto(String result) {
      this.result = result;
    }
  }

  public interface Interface {
    Dto compute();
  }

  public static class Main {

    static void doWork(Interface i) {
      System.out.println(i.compute().result);
    }
  }

  public static class ClientTest {

    public static class MockupProvider implements Interface {
      @Override
      public Dto compute() {
        return new Dto("Hello from MockupProvider");
      }
    }

    public static void main(String[] args) {
      Main.doWork(new MockupProvider());
      Main.doWork(() -> new Dto("Hello from LambdaProvider"));
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingLibraryMemberRenamingTests(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRenamingInProgram()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Dto.class, Interface.class, Main.class)
            .addKeepClassAndMembersRules(Main.class)
            .addKeepClassAndMembersRulesWithAllowObfuscation(Interface.class, Dto.class)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(
                codeInspector -> {
                  ClassSubject clazz = codeInspector.clazz(Interface.class);
                  assertThat(clazz, isPresent());
                  MethodSubject computeSubject = clazz.uniqueMethodWithOriginalName("compute");
                  assertThat(computeSubject, isPresent());
                  assertTrue(computeSubject.isRenamed());
                });
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(ClientTest.class)
        .addClasspathClasses(Dto.class, Interface.class, Main.class)
        .addKeepAllClassesRule()
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), ClientTest.class)
        .assertSuccessWithOutputLines("Hello from MockupProvider", "Hello from LambdaProvider");
  }
}
