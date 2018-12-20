// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b69906048;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

public class Regress69906048Test extends TestBase {

  @Test
  public void buildWithD8AndRunWithDalvikOrArt() throws Exception {
    AndroidApp androidApp = ToolHelper.runR8(
        ToolHelper.prepareR8CommandBuilder(
            readClasses(ClassWithAnnotations.class, AnAnnotation.class))
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addProguardConfiguration(
                ImmutableList.of("-keepattributes *Annotation*"), Origin.unknown())
            .build(),
        options -> options.minApiLevel = ToolHelper.getMinApiLevelForDexVm().getLevel());
    String result = runOnArt(androidApp, ClassWithAnnotations.class);
    Assert.assertEquals("@" + AnAnnotation.class.getCanonicalName() + "()", result);
  }

}
