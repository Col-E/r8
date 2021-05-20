// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.structural.Ordered;
import java.util.List;

public class CfVersionUtils {

  public static CfVersion max(List<ProgramMethod> methods) {
    CfVersion result = null;
    for (ProgramMethod method : methods) {
      if (method.getDefinition().hasClassFileVersion()) {
        result = Ordered.maxIgnoreNull(result, method.getDefinition().getClassFileVersion());
      }
    }
    return result;
  }
}
