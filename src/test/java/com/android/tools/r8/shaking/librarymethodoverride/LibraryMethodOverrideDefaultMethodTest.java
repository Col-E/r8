// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
public class LibraryMethodOverrideDefaultMethodTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(ProgramI.class, ProgramClass.class, Main.class)
        .addLibraryClasses(LibraryI.class, LibraryClass.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, LibraryI.class, LibraryClass.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ProgramI::foo");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ProgramI.class, ProgramClass.class, Main.class)
        .addDefaultRuntimeLibrary(parameters)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .addLibraryClasses(LibraryI.class, LibraryClass.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .compile()
        .addBootClasspathClasses(LibraryI.class, LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ProgramI::foo");
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    DexProgramClass clazz =
        appInfo
            .definitionFor(dexItemFactory.createType(descriptor(ProgramI.class)))
            .asProgramClass();
    DexEncodedMethod method = clazz.lookupVirtualMethod(m -> m.getName().toString().equals("foo"));
    if (appInfo.options().canUseDefaultAndStaticInterfaceMethods() || mode.isInitialTreeShaking()) {
      assertNotNull(method);
      assertTrue(method.isLibraryMethodOverride().isTrue());
    } else {
      assertNull(method);
    }
  }

  public interface LibraryI {

    void foo();
  }

  public static class LibraryClass {

    public static void callI(LibraryI i) {
      i.foo();
    }
  }

  public interface ProgramI extends LibraryI {

    @Override
    default void foo() {
      System.out.println("ProgramI::foo");
    }
  }

  public static class ProgramClass implements ProgramI {}

  public static class Main {

    public static void main(String[] args) {
      LibraryClass.callI(new ProgramClass());
    }
  }
}
