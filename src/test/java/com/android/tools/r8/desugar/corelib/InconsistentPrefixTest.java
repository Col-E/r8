// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class InconsistentPrefixTest extends TestBase {

  @Test
  public void testNoInconsistentPrefixes() throws Exception {
    Map<String, String> x = new HashMap<>();
    x.put("pkg.sub", "p$.bus");
    x.put("pkg", "p$");

    JasminBuilder jasminBuilder = new JasminBuilder();
    jasminBuilder.addClass("pkg/notsub/A");
    jasminBuilder.addClass("pkg/sub/A");
    Path inputJar = temp.getRoot().toPath().resolve("input.jar");
    jasminBuilder.writeJar(inputJar);

    try {
      testForD8()
          .addProgramFiles(inputJar)
          .addOptionsModification(
              options ->
                  options.desugaredLibraryConfiguration =
                      new DesugaredLibraryConfiguration(
                          false,
                          x,
                          ImmutableMap.of(),
                          ImmutableMap.of(),
                          ImmutableMap.of(),
                          ImmutableMap.of(),
                          ImmutableList.of()))
          .compile();
      fail("Should have raised the compilation error.");
    } catch (CompilationFailedException e) {
      assertTrue(
          e.getCause().getMessage().startsWith("Error: Inconsistent prefix in desugared library:"));
    }
  }
}
