// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
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
public class LibraryMethodOverrideDefaultMethodICCETest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ProgramI.class, ProgramJ.class)
        .addProgramClassFileData(
            transformer(ProgramClass.class)
                .setImplements(LibraryInterface.class, ProgramI.class, ProgramJ.class)
                .transform())
        .addLibraryClasses(LibraryInterface.class, LibraryCaller.class)
        .addRunClasspathFiles(
            buildOnDexRuntime(parameters, LibraryInterface.class, LibraryCaller.class))
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(
            parameters.isCfRuntime() && parameters.getRuntime().asCf().isOlderThan(CfVm.JDK11),
            IncompatibleClassChangeError.class)
        .assertFailureWithErrorThatThrowsIf(
            parameters.isCfRuntime()
                && parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK11),
            AbstractMethodError.class)
        .assertFailureWithErrorThatThrowsIf(
            parameters.isDexRuntime(), IncompatibleClassChangeError.class);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, ProgramI.class, ProgramJ.class)
        .addProgramClassFileData(
            transformer(ProgramClass.class)
                .setImplements(LibraryInterface.class, ProgramI.class, ProgramJ.class)
                .transform())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(LibraryInterface.class, LibraryCaller.class)
        .addOptionsModification(
            options -> options.testing.enqueuerInspector = this::verifyLibraryOverrideInformation)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Main.class)
        .addDontObfuscate()
        .compile()
        .addBootClasspathClasses(LibraryInterface.class, LibraryCaller.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrowsIf(
            parameters.canUseDefaultAndStaticInterfaceMethods(), AbstractMethodError.class)
        .assertFailureWithErrorThatThrowsIf(
            !parameters.canUseDefaultAndStaticInterfaceMethods(),
            IncompatibleClassChangeError.class);
  }

  private void verifyLibraryOverrideInformation(AppInfoWithLiveness appInfo, Enqueuer.Mode mode) {
    if (mode.isInitialTreeShaking()) {
      if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
        verifyMethodFooOnHolderIsSetAsLibraryOverride(appInfo, ProgramI.class);
        verifyMethodFooOnHolderIsSetAsLibraryOverride(appInfo, ProgramJ.class);
      } else {
        verifyMethodFooOnHolderIsSetAsLibraryOverride(appInfo, ProgramClass.class);
      }
    }
  }

  private void verifyMethodFooOnHolderIsSetAsLibraryOverride(
      AppInfoWithLiveness appInfo, Class<?> programClass) {
    DexItemFactory dexItemFactory = appInfo.dexItemFactory();
    DexProgramClass clazz =
        appInfo.definitionFor(dexItemFactory.createType(descriptor(programClass))).asProgramClass();
    DexEncodedMethod method =
        clazz.lookupVirtualMethod(m -> m.getReference().name.toString().equals("foo"));
    assertTrue(method.isLibraryMethodOverride().isTrue());
  }

  public interface LibraryInterface {

    void foo();
  }

  public static class LibraryCaller {

    public static void callFoo(LibraryInterface iface) {
      iface.foo();
    }
  }

  public interface ProgramI extends LibraryInterface {

    @Override
    default void foo() {
      System.out.println("ProgramI::foo");
    }
  }

  public interface ProgramJ extends LibraryInterface {

    @Override
    default void foo() {
      System.out.println("ProgramJ::foo");
    }
  }

  public static class ProgramClass implements LibraryInterface, ProgramI /* ,ProgramJ */ {}

  public static class Main {

    public static void main(String[] args) {
      LibraryCaller.callFoo(new ProgramClass());
    }
  }
}
