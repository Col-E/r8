// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.MemberNaming.NoSignature;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MapReaderVersionTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MapReaderVersionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testNoVersion() throws IOException {
    ClassNameMapper mapper =
        ClassNameMapper.mapperFromString(
            StringUtils.joinLines(
                "pkg.Foo -> a.a:", "# { id: \"com.android.tools.r8.synthesized\" }"));
    assertMapping("a.a", "pkg.Foo", false, mapper);
  }

  @Test
  public void testExperimentalVersion() throws IOException {
    ClassNameMapper mapper =
        ClassNameMapper.mapperFromString(
            StringUtils.joinLines(
                "# { id: 'com.android.tools.r8.metainf', map-version: 'experimental' }",
                "pkg.Foo -> a.a:",
                "# { id: 'com.android.tools.r8.synthesized' }"));
    assertMapping("a.a", "pkg.Foo", true, mapper);
  }

  @Test
  public void testConcatMapFiles() throws IOException {
    ClassNameMapper mapper =
        ClassNameMapper.mapperFromString(
            StringUtils.joinLines(
                // Default map-version is none.
                "pkg.Foo -> a.a:",
                "# { id: 'com.android.tools.r8.synthesized' }",
                // Section with map-version experimental.
                "# { id: 'com.android.tools.r8.metainf', map-version: 'experimental' }",
                "pkg.Bar -> a.b:",
                "# { id: 'com.android.tools.r8.synthesized' }",
                // Section reverting map-version back to none (to support tooling that
                // concatenates).
                "# { id: 'com.android.tools.r8.metainf', map-version: 'none' }",
                "pkg.Baz -> a.c:",
                "# { id: 'com.android.tools.r8.synthesized' }"));
    assertMapping("a.a", "pkg.Foo", false, mapper);
    assertMapping("a.b", "pkg.Bar", true, mapper);
    assertMapping("a.c", "pkg.Baz", false, mapper);
  }

  private void assertMapping(
      String finalName, String originalName, boolean isSynthesized, ClassNameMapper mapper) {
    ClassNamingForNameMapper naming = mapper.getClassNaming(finalName);
    assertEquals(originalName, naming.originalName);
    Assert.assertEquals(isSynthesized, isCompilerSynthesized(naming));
  }

  private boolean isCompilerSynthesized(ClassNamingForNameMapper naming) {
    List<MappingInformation> infos = naming.getAdditionalMappings().get(NoSignature.NO_SIGNATURE);
    if (infos == null || infos.isEmpty()) {
      return false;
    }
    return infos.stream().anyMatch(MappingInformation::isCompilerSynthesizedMappingInformation);
  }
}
