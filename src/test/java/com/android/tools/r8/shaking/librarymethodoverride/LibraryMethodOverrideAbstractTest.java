// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.Enqueuer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideAbstractTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(SubProgramClass.class, Main.class)
        .addProgramClassFileData(
            transformer(ProgramClass.class).removeMethodsWithName("foo").transform())
        .addLibraryClasses(LibraryClass.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(SubProgramClass.class, Main.class)
        .addProgramClassFileData(
            transformer(ProgramClass.class).removeMethodsWithName("foo").transform())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .compile()
        .addBootClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    if (mode.isInitialTreeShaking()) {
      DexItemFactory dexItemFactory = appInfo.dexItemFactory();
      DexProgramClass clazz =
          appInfo
              .definitionFor(dexItemFactory.createType(descriptor(SubProgramClass.class)))
              .asProgramClass();
      DexEncodedMethod method =
          clazz.lookupVirtualMethod(m -> m.getReference().name.toString().equals("foo"));
      assertTrue(method.isLibraryMethodOverride().isTrue());
    }
  }

  public abstract static class LibraryClass {

    abstract void foo();

    public static void callFoo(LibraryClass libraryClass) {
      libraryClass.foo();
    }
  }

  public abstract static class SubProgramClass extends LibraryClass {

    @Override
    abstract void foo();
  }

  public static class ProgramClass extends SubProgramClass {

    @Override
    void foo() {
      throw new RuntimeException("Should have been removed.");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      LibraryClass.callFoo(new ProgramClass());
    }
  }
}
