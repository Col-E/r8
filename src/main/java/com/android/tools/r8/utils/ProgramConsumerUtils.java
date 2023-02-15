// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.dex.Marker.Backend;

public class ProgramConsumerUtils {

  public static Backend getBackend(ProgramConsumer programConsumer) {
    if (isGeneratingClassFiles(programConsumer)) {
      return Backend.CF;
    } else {
      assert isGeneratingDex(programConsumer);
      return Backend.DEX;
    }
  }

  public static boolean isGeneratingClassFiles(ProgramConsumer programConsumer) {
    return programConsumer instanceof ClassFileConsumer;
  }

  public static boolean isGeneratingDex(ProgramConsumer programConsumer) {
    return programConsumer instanceof DexIndexedConsumer
        || programConsumer instanceof DexFilePerClassFileConsumer;
  }
}
