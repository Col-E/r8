// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.CompilationMode;
import org.junit.Test;

public class YouTubeDexVerificationTest extends YouTubeCompilationBase {

  @Test
  public void buildDebugFromDex() throws Exception {
    runR8AndCheckVerification(CompilationMode.DEBUG, APK);
  }

  @Test
  public void buildReleaseFromDex() throws Exception {
    runR8AndCheckVerification(CompilationMode.RELEASE, APK);
  }
}
