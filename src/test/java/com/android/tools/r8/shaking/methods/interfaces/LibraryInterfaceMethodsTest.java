// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.interfaces;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryInterfaceMethodsTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryInterfaceMethodsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testLibraryBridgeDesugaring()
      throws CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseInterface.class, SubInterface.class)
            .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
            .addKeepMethodRules(SubInterface.class, "int hashCode()")
            .setMinApi(parameters)
            .compile();
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addRunClasspathFiles(compileResult.writeToZip())
        .run(parameters.getRuntime(), Main.class, A.class.getTypeName(), "hashCode")
        .assertSuccessWithOutputLines("Hello World!");
  }

  public interface BaseInterface {

    int hashCode();
  }

  public interface SubInterface extends BaseInterface {}

  public static class A implements SubInterface {

    @Override
    public int hashCode() {
      System.out.println("Hello World!");
      return 42;
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      Object o = Class.forName(args[0]).getDeclaredConstructor().newInstance();
      o.getClass().getMethod(args[1]).invoke(o);
    }
  }
}
