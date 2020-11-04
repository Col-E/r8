// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;

class CompatProguardSmaliTestBase extends SmaliTestBase {

  CodeInspector runCompatProguard(SmaliBuilder builder, List<String> keepRules) throws Exception {
    return runCompatProguard(builder, testBuilder -> testBuilder.addKeepRules(keepRules));
  }

  CodeInspector runCompatProguard(
      SmaliBuilder builder, ThrowableConsumer<R8CompatTestBuilder> configuration) throws Exception {
    return testForR8Compat(Backend.DEX)
        .addProgramDexFileData(builder.compile())
        .applyIf(configuration != null, configuration)
        .compile()
        .inspector();
  }
}
