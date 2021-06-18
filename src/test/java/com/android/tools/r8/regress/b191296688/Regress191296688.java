// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b191296688;

import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress191296688 extends KotlinTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getKotlinTestParameters()
            .withTargetVersion(KotlinTargetVersion.JAVA_8)
            .withCompiler(ToolHelper.getKotlinC_1_5_0_m2())
            .build());
  }

  public Regress191296688(TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testRegress191296688() throws Exception {
    Path aLib = temp.newFolder().toPath().resolve("alib.jar");
    writeClassesToJar(aLib, A.class);
    String pkg = getClass().getPackage().getName();
    String folder = DescriptorUtils.getBinaryNameFromJavaType(pkg);
    CfRuntime cfRuntime = TestRuntime.getCheckedInJdk9();
    Path ktClasses =
        kotlinc(cfRuntime, kotlinc, targetVersion)
            .addSourceFiles(getKotlinFileInTest(folder, "B"))
            .addClasspathFiles(aLib)
            .compile();
    Path desugaredJar =
        testForD8(Backend.CF)
            .addLibraryFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
            .addProgramFiles(ktClasses)
            .addProgramClasses(A.class)
            .addOptionsModification(o -> o.cfToCfDesugar = true)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();
    testForD8()
        .addProgramFiles(desugaredJar)
        .setMinApi(parameters.getApiLevel())
        .disableDesugaring()
        .run(parameters.getRuntime(), pkg + ".BKt")
        // TDOO(b/191296688): This should succeed.
        .assertFailure();
  }
}
