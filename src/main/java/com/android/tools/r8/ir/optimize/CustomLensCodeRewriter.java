// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import java.util.Set;

public interface CustomLensCodeRewriter {

  Set<Phi> rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      RewrittenPrototypeDescription prototypeChanges,
      NonIdentityGraphLens lens);
}
