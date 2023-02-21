// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingInterfaceClInitTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ApplyMappingInterfaceClInitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNotRenamingClInitIfNotInMap()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addInnerClasses(ApplyMappingInterfaceClInitTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(TestInterface.class)
        .addApplyMapping("")
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::verifyNoRenamingOfClInit);
  }

  @Test
  public void testNotRenamingClInitIfInMap()
      throws ExecutionException, CompilationFailedException, IOException {
    String interfaceName = TestInterface.class.getTypeName();
    testForR8(parameters.getBackend())
        .addInnerClasses(ApplyMappingInterfaceClInitTest.class)
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(TestInterface.class)
        .addApplyMapping(
            StringUtils.lines(
                interfaceName + " -> " + interfaceName + ":", "    void <clinit>() -> a"))
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .inspect(this::verifyNoRenamingOfClInit);
  }

  private void verifyNoRenamingOfClInit(CodeInspector inspector) {
    ClassSubject interfaceSubject = inspector.clazz(TestInterface.class);
    assertThat(interfaceSubject, isPresent());
    interfaceSubject.allMethods().stream()
        .allMatch(
            method -> {
              boolean classInitNotRenamed = !method.isClassInitializer() || !method.isRenamed();
              assertTrue(classInitNotRenamed);
              return classInitNotRenamed;
            });
  }

  public interface TestInterface {
    Throwable t = new Throwable();

    void foo();
  }

  public static class Main implements TestInterface {

    public static void main(String[] args) {
      new Main().foo();
    }

    @Override
    public void foo() {
      System.out.println("Hello World!");
    }
  }
}
