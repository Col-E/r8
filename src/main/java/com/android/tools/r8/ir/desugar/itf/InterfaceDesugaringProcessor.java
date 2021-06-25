// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.collections.ProgramMethodSet;

public interface InterfaceDesugaringProcessor {

  // The process phase can be performed before, concurrently or after code desugaring. It can be
  // executed concurrently on all classes. Each processing may require to read other classes,
  // so this phase cannot modify the classes themselves (for example insertion/removal of methods).
  // The phase can insert new classes with new methods, such as emulated interface dispatch classes
  // or companion classes with their methods.
  void process(DexProgramClass clazz, ProgramMethodSet synthesizedMethods);

  // The finalization phase is done at a join point, after all code desugaring have been performed.
  // All finalization phases of all desugaring processors are performed sequentially.
  // Complex computations should be avoided if possible here and be moved to the concurrent phase.
  // Classes may be mutated here (new methods can be inserted, etc.).
  void finalizeProcessing(ProgramMethodSet synthesizedMethods);
}
