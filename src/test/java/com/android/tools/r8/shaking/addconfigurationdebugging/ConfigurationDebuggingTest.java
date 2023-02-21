// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.addconfigurationdebugging;

import static com.android.tools.r8.graph.DexEncodedMethod.CONFIGURATION_DEBUGGING_PREFIX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class BaseClass {
  Object field;
  BaseClass(Object usedArg) {
    field = usedArg;
  }
}

class UninstantiatedClass extends BaseClass {
  UninstantiatedClass() {
    super(null);
    System.out.println("UninstantiatedClass#<init>");
  }

  UninstantiatedClass(String arg) {
    super(arg);
    System.out.println("UninstantiatedClass#<init>(String)");
  }
}

class TestClass {
  BaseClass b;

  TestClass() {
    b = new BaseClass(this);
    System.out.println(b);
  }

  void foo(int i, long l) {
    System.out.println("void TestClass#foo(IJ)");
  }

  static void bar(TestClass arg) {
    System.out.println("void TestClass#bar(TestClass)");
  }
}

class Caller {
  public static void main(String[] args) {
    try {
      new UninstantiatedClass();
    } catch (RuntimeException e) {
    }
    try {
      new UninstantiatedClass("aaarrrrrrhhhhhh");
    } catch (RuntimeException e) {
    }

    TestClass instance = new TestClass();
    try {
      instance.foo(4, 2L);
    } catch (RuntimeException e) {
    }
    try {
      TestClass.bar(instance);
    } catch (RuntimeException e) {
    }

    throw new RuntimeException("Reaching the end");
  }
}

@RunWith(Parameterized.class)
public class ConfigurationDebuggingTest extends TestBase {
  private static final String PACKAGE_NAME =
      ConfigurationDebuggingTest.class.getPackage().getName();

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ConfigurationDebuggingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path firstRunArchive =
        testForR8(parameters.getBackend())
            .addProgramClasses(BaseClass.class, UninstantiatedClass.class, TestClass.class)
            .addKeepRules("-addconfigurationdebugging")
            .addKeepRules("-keep class **.TestClass { <init>(); }")
            .addDontObfuscate()
            .setMinApi(parameters)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    R8FullTestBuilder builder =
        testForR8(parameters.getBackend())
            .addLibraryClasses(BaseClass.class, UninstantiatedClass.class, TestClass.class)
            .addDefaultRuntimeLibrary(parameters)
            .addProgramClasses(Caller.class)
            .addKeepMainRule(Caller.class)
            .setMinApi(parameters);
    R8TestRunResult result =
        builder
            .compile()
            .addRunClasspathFiles(firstRunArchive)
            .run(parameters.getRuntime(), Caller.class);
    // TODO(b/117302947): Dex runtime should be able to find that framework class.
    if (parameters.isDexRuntime()) {
      result.assertFailureWithErrorThatMatches(containsString("NoClassDefFoundError"));
      result.assertFailureWithErrorThatMatches(containsString("android.util.Log"));
      return;
    }
    result
        .assertFailureWithErrorThatMatches(
            containsString(createExpectedMessage(UninstantiatedClass.class)))
        .assertFailureWithErrorThatMatches(containsString("void <init>()"))
        .assertFailureWithErrorThatMatches(containsString("void <init>(java.lang.String)"))
        .assertFailureWithErrorThatMatches(
            containsString(createExpectedMessage(TestClass.class)))
        .assertFailureWithErrorThatMatches(containsString("void foo(int,long)"))
        .assertFailureWithErrorThatMatches(
            containsString("void bar(" + PACKAGE_NAME + ".TestClass" +")"))
        .assertFailureWithErrorThatMatches(containsString("Reaching the end"));
  }

  private String createExpectedMessage(Class<?> clazz) {
    return CONFIGURATION_DEBUGGING_PREFIX + clazz.getName();
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject baseClass = inspector.clazz(BaseClass.class);
    assertThat(baseClass, isPresent());
    baseClass.allMethods().forEach(methodSubject -> {
      assertTrue(methodSubject.isInstanceInitializer());
      assertFalse(hasThrow(methodSubject));
    });

    ClassSubject uninstantiatedClass = inspector.clazz(UninstantiatedClass.class);
    assertThat(uninstantiatedClass, isPresent());
    uninstantiatedClass.allMethods().forEach(methodSubject -> {
      assertTrue(methodSubject.isInstanceInitializer());
      assertTrue(hasThrow(methodSubject));
    });

    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());
    MethodSubject foo = testClass.uniqueMethodWithOriginalName("foo");
    assertThat(foo, isPresent());
    assertTrue(hasThrow(foo));
    MethodSubject bar = testClass.uniqueMethodWithOriginalName("bar");
    assertThat(bar, isPresent());
    assertTrue(hasThrow(bar));
  }

  private static boolean hasThrow(MethodSubject methodSubject) {
    return methodSubject.iterateInstructions(InstructionSubject::isThrow).hasNext();
  }
}
