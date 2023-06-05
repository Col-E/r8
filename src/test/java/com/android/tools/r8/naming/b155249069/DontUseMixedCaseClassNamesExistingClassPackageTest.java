// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b155249069;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.b155249069.package_b.A;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DontUseMixedCaseClassNamesExistingClassPackageTest extends TestBase {

  private final TestParameters parameters;
  private final boolean dontUseMixedCase;

  private final String renamedATypeName = "Testpackage.A";

  @Parameters(name = "{0}, dontusemixedcaseclassnames: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public DontUseMixedCaseClassNamesExistingClassPackageTest(
      TestParameters parameters, boolean dontUseMixedCase) {
    this.parameters = parameters;
    this.dontUseMixedCase = dontUseMixedCase;
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    Path packageDictionary = temp.getRoot().toPath().resolve("packagedictionary.txt");
    // Suggest the name 'a' for the package, to force a collision with A.A.
    FileUtils.writeTextFile(packageDictionary, "testpackage");
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class)
        .addProgramClassFileData(
            transformer(Main.class)
                .replaceClassDescriptorInMethodInstructions(
                    DescriptorUtils.javaTypeToDescriptor(
                        com.android.tools.r8.naming.b155249069.A.A.class.getTypeName()),
                    DescriptorUtils.javaTypeToDescriptor(renamedATypeName))
                .transform(),
            transformer(com.android.tools.r8.naming.b155249069.A.A.class)
                .setClassDescriptor(DescriptorUtils.javaTypeToDescriptor(renamedATypeName))
                .transform())
        .setMinApi(parameters)
        // Keep testpackage.A such that the package A is kept.
        .addKeepClassRules(renamedATypeName)
        .addKeepClassRulesWithAllowObfuscation(A.class)
        .addKeepMainRule(Main.class)
        .addKeepRules("-packageobfuscationdictionary " + packageDictionary.toString())
        .applyIf(dontUseMixedCase, b -> b.addKeepRules("-dontusemixedcaseclassnames"))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A.A.foo()", "package_b.B.foo()")
        .inspect(
            inspector -> {
              ClassSubject aSubject = inspector.clazz(renamedATypeName);
              ClassSubject bSubject = inspector.clazz(A.class);
              if (dontUseMixedCase) {
                assertNotEquals(
                    StringUtils.toLowerCase(aSubject.getFinalName()),
                    StringUtils.toLowerCase(bSubject.getFinalName()));
              } else {
                assertEquals(
                    StringUtils.toLowerCase(aSubject.getFinalName()),
                    StringUtils.toLowerCase(bSubject.getFinalName()));
              }
            });
  }

  public static class Main {

    public static void main(String[] args) {
      new com.android.tools.r8.naming.b155249069.A.A().foo();
      new A().foo();
    }
  }
}
