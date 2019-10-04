// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b139991218;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a reproduction of b/139991218 where a defined annotation can no longer be found because
 * the hash has changed from when it was cached, due to the GenericSignatureRewriter.
 *
 * <p>It requires that the hash is pre-computed and that seem to happen only in the lambda merger,
 * which is why the ASMified code is generated from kotlin.
 */
@RunWith(Parameterized.class)
public class TestRunner extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public TestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testSignatureRewriteHash()
      throws ExecutionException, CompilationFailedException, IOException {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(Lambda1.dump(), Lambda2.dump(), Main.dump(), Alpha.dump())
        .addProgramFiles(
            Paths.get(
                ToolHelper.TESTS_BUILD_DIR,
                "kotlinR8TestResources",
                "JAVA_8",
                "lambdas_kstyle_generics" + FileUtils.JAR_EXTENSION))
        .addKeepMainRule(Main.class)
        .addKeepAllAttributes()
        .addOptionsModification(
            options -> {
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
              options.enableClassInlining = false;
            })
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(StringUtils.lines("11", "12"))
        .inspect(
            inspector -> {
              // Ensure that we have created a lambda group and that the lambda classes are now
              // gone.
              boolean foundLambdaGroup = false;
              for (FoundClassSubject allClass : inspector.allClasses()) {
                foundLambdaGroup |= allClass.getOriginalName().contains("LambdaGroup");
                assertFalse(allClass.getOriginalName().contains("b139991218.Lambda"));
              }
              assertTrue(foundLambdaGroup);
            });
  }
}
