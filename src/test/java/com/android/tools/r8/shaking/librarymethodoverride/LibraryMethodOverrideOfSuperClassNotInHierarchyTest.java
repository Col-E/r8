// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.utils.OptionalBool;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideOfSuperClassNotInHierarchyTest extends TestBase {

  private final String[] EXPECTED = new String[] {"SecondProgramClass::foo"};

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(
            FirstProgramClass.class, SecondProgramClass.class, ThirdProgramClass.class, Main.class)
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .addRunClasspathFiles(
            buildOnDexRuntime(parameters, LibraryClass.class, LibraryInterface.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(
            FirstProgramClass.class, SecondProgramClass.class, ThirdProgramClass.class, Main.class)
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .compile()
        .addBootClasspathClasses(LibraryClass.class, LibraryInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    if (!mode.isInitialTreeShaking()) {
      return;
    }
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    DexProgramClass clazz =
        appInfo
            .definitionFor(dexItemFactory.createType(descriptor(FirstProgramClass.class)))
            .asProgramClass();
    DexEncodedMethod method =
        clazz.lookupVirtualMethod(m -> m.getReference().name.toString().equals("foo"));
    assertEquals(OptionalBool.FALSE, method.isLibraryMethodOverride());
    clazz =
        appInfo
            .definitionFor(dexItemFactory.createType(descriptor(SecondProgramClass.class)))
            .asProgramClass();
    method = clazz.lookupVirtualMethod(m -> m.getReference().name.toString().equals("foo"));
    assertEquals(OptionalBool.TRUE, method.isLibraryMethodOverride());
  }

  public interface LibraryInterface {

    void foo();
  }

  public static class LibraryClass {

    public static void callFoo(LibraryInterface i) {
      i.foo();
    }
  }

  public static class FirstProgramClass {

    public void foo() {
      System.out.println("FirstProgramClass::foo");
    }
  }

  public static class SecondProgramClass extends FirstProgramClass {

    @Override
    public void foo() {
      System.out.println("SecondProgramClass::foo");
    }
  }

  public static class ThirdProgramClass extends SecondProgramClass implements LibraryInterface {}

  public static class Main {

    public static void main(String[] args) {
      LibraryClass.callFoo(new ThirdProgramClass());
    }
  }
}
