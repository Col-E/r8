// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public class ProguardMapReaderTest {

  public static final String ROOT = ToolHelper.EXAMPLES_BUILD_DIR;
  public static final String EXAMPLE_MAP = "throwing/throwing.map";
  public static final String EXAMPLE_MAP_WITH_PACKAGE_INFO =
      "dagger.android.package-info -> dagger.android.package-info\n";

  public static final String GMSCORE_V4_MAP = "third_party/gmscore/v4/proguard.map";
  public static final String GMSCORE_V5_MAP = "third_party/gmscore/v5/proguard.map";
  public static final String GMSCORE_V6_MAP = "third_party/gmscore/v6/proguard.map";
  public static final String GMSCORE_V7_MAP = "third_party/gmscore/v7/proguard.map";
  public static final String GMSCORE_V8_MAP = "third_party/gmscore/v8/proguard.map";
  public static final String GMSCORE_V9_MAP =
      "third_party/gmscore/gmscore_v9/GmsCore_prod_alldpi_release_all_locales_proguard.map";
  public static final String GMSCORE_V10_MAP =
      "third_party/gmscore/gmscore_v10/GmsCore_prod_alldpi_release_all_locales_proguard.map";

  @Test
  public void parseThrowingMap() throws IOException {
    ClassNameMapper.mapperFromFile(Paths.get(ROOT, EXAMPLE_MAP));
  }

  public void roundTripTest(Path path) throws IOException {
    ClassNameMapper firstMapper = ClassNameMapper.mapperFromFile(path);
    ClassNameMapper secondMapper = ClassNameMapper.mapperFromString(firstMapper.toString());
    Assert.assertEquals(firstMapper, secondMapper);
  }

  @Test
  public void roundTripTest() throws IOException {
    roundTripTest(Paths.get(ROOT, EXAMPLE_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV4() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V4_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV5() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V5_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV6() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V6_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV7() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V7_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV8() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V8_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV9() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V9_MAP));
  }

  @Test
  public void roundTripTestGmsCoreV10() throws IOException {
    roundTripTest(Paths.get(GMSCORE_V10_MAP));
  }

  @Test
  public void parseMapWithPackageInfo() throws IOException {
    ClassNameMapper mapper = ClassNameMapper.mapperFromString(EXAMPLE_MAP_WITH_PACKAGE_INFO);
    Assert.assertTrue(mapper.getObfuscatedToOriginalMapping().isEmpty());
  }

  @Test
  public void testSingleCases() throws IOException {
    List<String> ss =
        ImmutableList.of(
            /* */ "a.b.C -> d.e.F:\n" //
                + "    void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a() -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():2:2 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a():11:12 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:2:void a():11:12 -> b\n"
                + "    1:2:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a(int):11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void g.a(int):11:11 -> b\n" //
                + "    1:1:void c():21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a(int):11:11 -> b\n" //
                + "    2:2:void c():21:21 -> b\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    1:1:void a():11:11 -> b\n" //
                + "    1:1:void c():21:21 -> d\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    2:2:void f1():11:11 -> b\n" //
                + "    2:2:void f2():21 -> b\n" //
                + "    2:2:void f3():21 -> b\n" //
                + "    2:2:void f4():22 -> b\n" //
                + "    2:2:void f5():21 -> b\n" //
                + "    3:3:void f6():12:34 -> c\n",
            /* */ "a.b.C -> d.e.F:\n" //
                + "    2:2:void f1():11:11 -> b\n" //
                + "    2:2:void f2():21 -> b\n" //
                + "    2:2:void f3() -> b\n" //
                + "    2:2:void f5():21 -> b\n" //
                + "    3:3:void f6() -> c\n");

    for (String s : ss) {
      ClassNameMapper cnm = ClassNameMapper.mapperFromString(s);
      String result = cnm.toString();
      Assert.assertEquals(result, s);
    }
  }
}
