// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.KeepReason;

public interface EnqueuerAnalysis {

  void processNewlyInstantiatedClass(DexProgramClass clazz, KeepReason reason);

  void done();
}
