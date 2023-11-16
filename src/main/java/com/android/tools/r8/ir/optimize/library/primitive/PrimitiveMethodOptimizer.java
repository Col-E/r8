// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.optimize.library.StatelessLibraryMethodModelCollection;
import java.util.function.Consumer;

public abstract class PrimitiveMethodOptimizer extends StatelessLibraryMethodModelCollection {

  public static void forEachPrimitiveOptimizer(
      AppView<?> appView, Consumer<StatelessLibraryMethodModelCollection> register) {
    register.accept(new BooleanMethodOptimizer(appView));
    register.accept(new ByteMethodOptimizer(appView));
    register.accept(new CharacterMethodOptimizer(appView));
    register.accept(new DoubleMethodOptimizer(appView));
    register.accept(new FloatMethodOptimizer(appView));
    register.accept(new IntegerMethodOptimizer(appView));
    register.accept(new LongMethodOptimizer(appView));
    register.accept(new ShortMethodOptimizer(appView));
  }
}
