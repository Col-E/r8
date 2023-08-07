// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.ClassNameMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class GMSCoreV10ProguardMapReaderTest extends GMSCoreCompilationTestBase {

  private static final String APP_DIR = ToolHelper.THIRD_PARTY_DIR + "gmscore/gmscore_v10/";

  @Test
  public void roundTripTestGmsCoreV10() throws IOException {
    Path map = Paths.get(APP_DIR).resolve(PG_MAP);
    ClassNameMapper firstMapper = ClassNameMapper.mapperFromFile(map).sorted();
    ClassNameMapper secondMapper =
        ClassNameMapper.mapperFromString(firstMapper.toString()).sorted();
    Assert.assertEquals(firstMapper, secondMapper);
  }
}
