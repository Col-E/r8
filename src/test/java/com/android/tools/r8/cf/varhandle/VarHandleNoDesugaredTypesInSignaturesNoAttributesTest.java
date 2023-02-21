// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.varhandle;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.JdkClassFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.examples.jdk9.VarHandle;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VarHandleNoDesugaredTypesInSignaturesNoAttributesTest extends TestBase {

  private static final String EXPECTED_OUTPUT = StringUtils.lines("0");
  private static final String MAIN_CLASS = VarHandle.NoDesugaredTypesInSignatures.typeName();
  private static final String JAR_ENTRY = "varhandle/NoDesugaredTypesInSignatures.class";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        // Running on 4.0.4 and 4.4.4 needs to be checked. Output seems correct, but at the
        // same time there are VFY errors on stderr.
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withAllApiLevels()
        .build();
  }

  @Test
  public void testR8() throws Throwable {
    // Strip the attributes (including InnerClasses) to ensure that the enqueuer does not
    // register references to MethodHandles$Lookup until the desugar step.
    Path programWithoutAttributes =
        testForR8(Backend.CF)
            .addLibraryProvider(JdkClassFileProvider.fromSystemJdk())
            .addProgramClassFileData(ZipUtils.readSingleEntry(VarHandle.jar(), JAR_ENTRY))
            .addKeepClassAndMembersRules(MAIN_CLASS)
            .compile()
            .writeToZip();
    assertTrue(
        new CodeInspector(programWithoutAttributes)
            .clazz(MAIN_CLASS)
            .getDexProgramClass()
            .getInnerClasses()
            .isEmpty());

    testForR8(parameters.getBackend())
        .applyIf(
            parameters.isDexRuntime(),
            // Use android.jar from Android T to get the VarHandle type.
            b -> b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T)),
            // Use system JDK to have references types including StringConcatFactory.
            b -> b.addLibraryProvider(JdkClassFileProvider.fromSystemJdk()))
        .addProgramFiles(programWithoutAttributes)
        .addOptionsModification(options -> options.enableVarHandleDesugaring = true)
        .setMinApi(parameters)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keep class " + MAIN_CLASS + "{ <fields>; }")
        .run(parameters.getRuntime(), MAIN_CLASS)
        .applyIf(
            parameters.isDexRuntime()
                && parameters.asDexRuntime().getVersion().isOlderThanOrEqual(Version.V4_4_4),
            // TODO(b/247076137): Running on 4.0.4 and 4.4.4 needs to be checked. Output seems
            // correct, but at the same time there are VFY errors on stderr.
            r -> r.assertFailureWithErrorThatThrows(NoSuchFieldException.class),
            r -> r.assertSuccessWithOutput(EXPECTED_OUTPUT));
  }
}
