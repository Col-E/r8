// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.Collections.emptyList;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryMemberRebindingInterfaceTest extends TestBase {

  private static Path oldRuntimeJar;
  private static Path newRuntimeJar;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @BeforeClass
  public static void compileCustomLib() throws Exception {
    oldRuntimeJar =
        createJar(
            ImmutableList.of(LibraryI.class, LibraryB.class, LibraryC.class),
            getProgramClassFileDataWithoutMethods(LibraryA.class));
    newRuntimeJar =
        createJar(ImmutableList.of(LibraryI.class, LibraryA.class, LibraryB.class, LibraryC.class));
  }

  // Tests old android.jar with old runtime.
  @Test
  public void testEmptyAAtCompileTimeAndRuntime() throws Exception {
    test(oldRuntimeJar, oldRuntimeJar);
  }

  // Tests new android.jar with new runtime.
  @Test
  public void testNonEmptyAAtCompileTimeAndRuntime() throws Exception {
    test(newRuntimeJar, newRuntimeJar);
  }

  // Tests old android.jar with new runtime.
  @Test
  public void testEmptyAAtCompileTime() throws Exception {
    test(oldRuntimeJar, newRuntimeJar);
  }

  // Tests new android.jar with old runtime.
  @Test
  public void testEmptyAAtRuntimeTime() throws Exception {
    test(newRuntimeJar, oldRuntimeJar);
  }

  private void test(Path compileTimeLibrary, Path runtimeLibrary) throws Exception {
    Method m = LibraryI.class.getDeclaredMethod("m");
    MethodReference methodReferenceForM = Reference.methodFromMethod(m);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addKeepClassAndMembersRules(Main.class)
        .addLibraryFiles(compileTimeLibrary)
        .addDefaultRuntimeLibrary(parameters)
        .apply(setMockApiLevelForMethod(m, AndroidApiLevel.B))
        // The api database needs to have an api level for each target, so even though B do not
        // define 'm', we still give it an API level.
        .apply(
            setMockApiLevelForMethod(
                Reference.method(
                    Reference.classFromClass(LibraryB.class),
                    methodReferenceForM.getMethodName(),
                    methodReferenceForM.getFormalTypes(),
                    methodReferenceForM.getReturnType()),
                AndroidApiLevel.B))
        .apply(setMockApiLevelForMethod(LibraryC.class.getDeclaredMethod("m"), AndroidApiLevel.B))
        .apply(setMockApiLevelForMethod(LibraryA.class.getDeclaredMethod("m"), AndroidApiLevel.N))
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertThat(
                  testMethodSubject,
                  invokesMethod(
                      "int",
                      getExpectedMemberRebindingTarget(compileTimeLibrary).getTypeName(),
                      "m",
                      emptyList()));
            })
        .addRunClasspathFiles(buildOnDexRuntime(parameters, runtimeLibrary))
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            // Compiling to an API level above the API where LibraryA.m() was defined with a new
            // android.jar, and running this on an older runtime (correctly) results in a NSME.
            parameters.isDexRuntime()
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)
                && compileTimeLibrary == newRuntimeJar
                && runtimeLibrary == oldRuntimeJar,
            runResult -> runResult.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            runResult -> runResult.assertSuccessWithOutputLines("42"));
  }

  private Class<?> getExpectedMemberRebindingTarget(Path compileTimeLibrary) {
    if (compileTimeLibrary == newRuntimeJar) {
      // If we are compiling to a new runtime with a new android.jar, we should rebind to LibraryA.
      if (parameters.isDexRuntime()
          && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
        return LibraryA.class;
      }
      // If we are compiling to an old runtime with a new android.jar, we should rebind to LibraryB.
      return LibraryB.class;
    }
    // Otherwise, we are compiling to an old android.jar, in which case we should rebind to
    // LibraryI.
    return LibraryI.class;
  }

  private static Path createJar(Collection<Class<?>> programClasses, byte[]... programClassFileData)
      throws Exception {
    return testForR8(getStaticTemp(), Backend.CF)
        .addProgramClasses(programClasses)
        .addProgramClassFileData(programClassFileData)
        .addKeepAllClassesRule()
        .compile()
        .writeToZip();
  }

  private static byte[] getProgramClassFileDataWithoutMethods(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .removeMethods(
            (int access, String name, String descriptor, String signature, String[] exceptions) ->
                !name.equals("<init>"))
        .transform();
  }

  public static class Main {

    public static void main(String[] args) {
      test(new LibraryC());
    }

    private static void test(LibraryB b) {
      System.out.println(b.m());
    }
  }

  public interface LibraryI {

    int m();
  }

  public static class LibraryA {

    // Added in API N, so we can't rebind to this in APIs < N.
    public int m() {
      return 42;
    }
  }

  public abstract static class LibraryB extends LibraryA implements LibraryI {}

  public static class LibraryC extends LibraryB {

    @Override
    public int m() {
      return 42;
    }
  }
}
