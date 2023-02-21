// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.applymapping.shared.InnerLibraryClass;
import com.android.tools.r8.naming.applymapping.shared.InnerLibraryClass.LibraryClass;
import com.android.tools.r8.naming.applymapping.shared.ProgramClassWithSimpleLibraryReference;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingInnerClassesPreserveTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingInnerClassesPreserveTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInnerClasses()
      throws IOException, CompilationFailedException, ExecutionException {
    R8TestCompileResult libraryCompileResult =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(InnerLibraryClass.class)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .addKeepClassAndMembersRulesWithAllowObfuscation(InnerLibraryClass.class)
            .setMinApi(parameters)
            .compile();
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(ProgramClassWithSimpleLibraryReference.class)
        .addClasspathClasses(InnerLibraryClass.class, LibraryClass.class)
        .addKeepMainRule(ProgramClassWithSimpleLibraryReference.class)
        .addApplyMapping(libraryCompileResult.getProguardMap())
        .addKeepAttributes("EnclosingMethod", "InnerClasses")
        .setMinApi(parameters)
        .noTreeShaking()
        .compile()
        .addRunClasspathFiles(libraryCompileResult.writeToZip())
        .run(parameters.getRuntime(), ProgramClassWithSimpleLibraryReference.class)
        .assertSuccessWithOutputLines("SubLibraryClass.foo()", "LibraryClass.foo()");
  }
}
