// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class LongestPrefixlTest extends TestBase {

  @Test
  public void testLongestPrefixMatching() throws Exception {
    for (Boolean trueOrFalse : BooleanUtils.values()) {

      // Use a LinkedHashMap with different insertion orders to (try to) test that the renaming
      // is independent of the iteration order of the map.
      Map<String, String> x = new LinkedHashMap<>();
      if (trueOrFalse) {
        x.put("pkg.sub", "p$.bus");
        x.put("pkg", "p$");
      } else {
        x.put("pkg", "p$");
        x.put("pkg.sub", "p$.bus");
      }

      JasminBuilder jasminBuilder = new JasminBuilder();
      jasminBuilder.addClass("pkg/notsub/A");
      jasminBuilder.addClass("pkg/sub/A");
      Path inputJar = temp.getRoot().toPath().resolve("input.jar");
      jasminBuilder.writeJar(inputJar);

      testForD8()
          .addProgramFiles(inputJar)
          .addOptionsModification(options -> options.rewritePrefix = x)
          .compile()
          .inspect(
              inspector -> {
                assertThat(inspector.clazz("p$.notsub.A"), isPresent());
                assertThat(inspector.clazz("p$.bus.A"), isPresent());
              });
    }
  }
}
