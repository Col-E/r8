// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.jasmin.JasminBuilder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InconsistentPrefixTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InconsistentPrefixTest(TestParameters parameters) {}

  @Test(expected = CompilationFailedException.class)
  public void testNoInconsistentPrefixes() throws Exception {
    Map<String, String> x = new HashMap<>();
    x.put("pkg.sub", "p$.bus");
    x.put("pkg", "p$");

    JasminBuilder jasminBuilder = new JasminBuilder();
    jasminBuilder.addClass("pkg/notsub/A");
    jasminBuilder.addClass("pkg/sub/A");
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar);

    testForD8()
        .addProgramFiles(inputJar)
        .addOptionsModification(
            options ->
                options.desugaredLibraryConfiguration =
                    DesugaredLibraryConfiguration.withOnlyRewritePrefixForTesting(x))
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              diagnostics.assertErrorMessageThatMatches(
                  containsString("Inconsistent prefix in desugared library"));
            });
  }
}
