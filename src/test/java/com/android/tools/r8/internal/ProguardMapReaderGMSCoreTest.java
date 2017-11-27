// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.naming.ClassNameMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class ProguardMapReaderGMSCoreTest {

  public static final String GMSCORE_V4_MAP = "third_party/gmscore/v4/proguard.map";
  public static final String GMSCORE_V5_MAP = "third_party/gmscore/v5/proguard.map";
  public static final String GMSCORE_V6_MAP = "third_party/gmscore/v6/proguard.map";
  public static final String GMSCORE_V7_MAP = "third_party/gmscore/v7/proguard.map";
  public static final String GMSCORE_V8_MAP = "third_party/gmscore/v8/proguard.map";
  public static final String GMSCORE_V9_MAP =
      "third_party/gmscore/gmscore_v9/GmsCore_prod_alldpi_release_all_locales_proguard.map";
  public static final String GMSCORE_V10_MAP =
      "third_party/gmscore/gmscore_v10/GmsCore_prod_alldpi_release_all_locales_proguard.map";

  public void roundTripTest(Path path) throws IOException {
    ClassNameMapper firstMapper = ClassNameMapper.mapperFromFile(path);
    ClassNameMapper secondMapper = ClassNameMapper.mapperFromString(firstMapper.toString());
    Assert.assertEquals(firstMapper, secondMapper);
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
}
