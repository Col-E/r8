// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceConstructorRenamingTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private static final String EXPECTED = StringUtils.lines("true");

  private static Function<Backend, R8TestCompileResult> compilation =
      memoizeFunction(InterfaceConstructorRenamingTest::compile);

  @ClassRule public static TemporaryFolder staticTemp = ToolHelper.getTemporaryFolderForTest();

  private static R8TestCompileResult compile(Backend backend)
      throws com.android.tools.r8.CompilationFailedException, IOException, ExecutionException {
    R8TestCompileResult compileResult =
        testForR8(staticTemp, backend)
            .addProgramClasses(TestInterface.class, TestClass.class)
            .addKeepMainRule(TestClass.class)
            .addLibraryProvider(
                new ArchiveClassFileProvider(
                    ToolHelper.getJava8RuntimeJar(),
                    name ->
                        ImmutableSet.of("java/lang/Object", "java/lang/System")
                            .contains(name.replace(".class", ""))))
            .setMinApi(AndroidApiLevel.B)
            .compile()
            .assertNoMessages();
    MethodSubject clinit =
        compileResult
            .inspector()
            .method(
                Reference.method(
                    Reference.classFromClass(TestInterface.class),
                    "<clinit>",
                    Collections.emptyList(),
                    null));
    assertTrue(clinit.isPresent());
    assertFalse(clinit.isRenamed());
    return compileResult;
  }

  private final TestParameters parameters;

  public InterfaceConstructorRenamingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Throwable {
    compilation.apply(parameters.getBackend())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  public interface TestInterface {
    // Force <clinit> to exist.
    long x = System.nanoTime();
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println(TestInterface.x > 0);
    }
  }
}
