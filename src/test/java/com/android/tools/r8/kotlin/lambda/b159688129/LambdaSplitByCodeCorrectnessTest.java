// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda.b159688129;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.kotlin.AbstractR8KotlinTestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaSplitByCodeCorrectnessTest extends AbstractR8KotlinTestBase {

  private final TestParameters parameters;
  private final KotlinTargetVersion targetVersion;
  private final boolean splitGroup;

  @Parameters(name = "{0}, targetVersion: {1}, splitGroup: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        KotlinTargetVersion.values(),
        BooleanUtils.values());
  }

  public LambdaSplitByCodeCorrectnessTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, boolean splitGroup) {
    super(targetVersion);
    this.parameters = parameters;
    this.targetVersion = targetVersion;
    this.splitGroup = splitGroup;
  }

  @Test
  public void testSplitLambdaGroups() throws Exception {
    String PKG_NAME = LambdaSplitByCodeCorrectnessTest.class.getPackage().getName();
    String folder = DescriptorUtils.getBinaryNameFromJavaType(PKG_NAME);
    CfRuntime cfRuntime =
        parameters.isCfRuntime() ? parameters.getRuntime().asCf() : TestRuntime.getCheckedInJdk9();
    Path ktClasses =
        kotlinc(cfRuntime, KOTLINC, targetVersion)
            .addSourceFiles(getKotlinFileInTest(folder, "Simple"))
            .compile();
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .addProgramFiles(ktClasses)
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(PKG_NAME + ".SimpleKt")
        .applyIf(
            splitGroup,
            b ->
                b.addOptionsModification(
                    internalOptions ->
                        // Setting verificationSizeLimitInBytesOverride = 1 will force a a chain
                        // having
                        // only a single implementation method in each.
                        internalOptions.testing.verificationSizeLimitInBytesOverride =
                            splitGroup ? 1 : -1))
        .noMinification()
        .allowDiagnosticWarningMessages()
        .compile()
        .assertAllWarningMessagesMatch(equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(
            codeInspector -> {
              List<FoundClassSubject> lambdaGroups =
                  codeInspector.allClasses().stream()
                      .filter(c -> c.getFinalName().contains("LambdaGroup"))
                      .collect(Collectors.toList());
              assertEquals(1, lambdaGroups.size());
              FoundClassSubject lambdaGroup = lambdaGroups.get(0);
              List<FoundMethodSubject> invokeChain =
                  lambdaGroup.allMethods(method -> method.getFinalName().contains("invoke$"));
              assertEquals(splitGroup ? 5 : 0, invokeChain.size());
            })
        .run(parameters.getRuntime(), PKG_NAME + ".SimpleKt")
        .assertSuccessWithOutputLines("Hello1", "Hello2", "Hello3", "Hello4", "Hello5", "Hello6");
  }
}
