// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions.kotlinassertionhandlerdoublecheck;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.rewrite.assertions.AssertionConfigurationAssertionHandlerKotlinTestBase;
import com.android.tools.r8.rewrite.assertions.assertionhandler.AssertionHandlers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationAssertionHandlerKotlinDoubleCheckTest
    extends AssertionConfigurationAssertionHandlerKotlinTestBase {

  public AssertionConfigurationAssertionHandlerKotlinDoubleCheckTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean kotlinStdlibAsClasspath,
      boolean useJvmAssertions)
      throws IOException {
    super(parameters, kotlinParameters, kotlinStdlibAsClasspath, useJvmAssertions);
  }

  @Override
  protected String getExpectedOutput() {
    return StringUtils.lines("assertionHandler: doubleCheckAssertion");
  }

  @Override
  protected MethodReference getAssertionHandler() throws Exception {
    return Reference.methodFromMethod(
        AssertionHandlers.class.getMethod("assertionHandler", Throwable.class));
  }

  @Override
  protected List<Path> getKotlinFiles() throws IOException {
    return getKotlinFilesInTestPackage(getClass().getPackage());
  }

  @Override
  protected boolean transformKotlinClasses() {
    return true;
  }

  @Override
  protected byte[] transformedKotlinClasses(Path kotlinClasses) throws IOException {
    Path compiledKotlinClasses = temp.newFolder().toPath();
    ZipUtils.unzip(
        compiledForAssertions.getForConfiguration(kotlinc, targetVersion), compiledKotlinClasses);
    String testClassPath =
        DescriptorUtils.getPackageBinaryNameFromJavaType(getTestClassName())
            + FileUtils.CLASS_EXTENSION;
    String assertionsMockBinaryName =
        DescriptorUtils.getPackageBinaryNameFromJavaType(
            getClass().getPackage().getName() + ".AssertionsMock");
    // Rewrite the static get on AssertionsMock.Enabled to static get on kotlin._Assertions.ENABLED
    return transformer(
            compiledKotlinClasses.resolve(testClassPath),
            Reference.classFromTypeName(getTestClassName()))
        .transformFieldInsnInMethod(
            "doubleCheckAssertionsEnabled",
            (opcode, owner, name, descriptor, continuation) -> {
              continuation.visitFieldInsn(
                  opcode,
                  owner.equals(assertionsMockBinaryName) ? "kotlin/_Assertions" : owner,
                  name,
                  descriptor);
            })
        .transform();
  }

  @Override
  protected String getTestClassName() {
    return getClass().getPackage().getName() + ".AssertionDoubleCheckKt";
  }

  @Override
  protected void configureR8(R8FullTestBuilder builder) {
    boolean referencesNotNull =
        !kotlinParameters.is(KotlinCompilerVersion.KOTLINC_1_3_72) && !kotlinStdlibAsLibrary;
    builder.applyIf(referencesNotNull, b -> b.addDontWarn("org.jetbrains.annotations.NotNull"));
  }
}
