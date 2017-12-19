// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;

public class CompatDxHelper {
  public static void run(D8Command command) throws IOException, CompilationException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    // DX does not desugar.
    options.enableDesugaring = false;
    // DX allows --multi-dex without specifying a main dex list for legacy devices.
    // That is broken, but for CompatDX we do the same to not break existing builds
    // that are trying to transition.
    options.enableMainDexListCheck = false;
    D8.runForTesting(app, options);
  }
}
