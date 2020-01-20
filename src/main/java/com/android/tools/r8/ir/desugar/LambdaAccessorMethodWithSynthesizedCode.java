// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.UseRegistry;
import java.util.function.Consumer;

public class LambdaAccessorMethodWithSynthesizedCode extends LambdaSynthesizedCode {

  public LambdaAccessorMethodWithSynthesizedCode(LambdaClass lambda) {
    super(lambda);
  }

  @Override
  public SourceCodeProvider getSourceCodeProvider() {
    return callerPosition -> new AccessorMethodSourceCode(lambda, callerPosition);
  }

  @Override
  public Consumer<UseRegistry> getRegistryCallback() {
    return registry -> {
      DexMethodHandle handle = lambda.descriptor.implHandle;
      DexMethod target = handle.asMethod();
      switch (handle.type) {
        case INVOKE_STATIC:
          registry.registerInvokeStatic(target);
          break;
        case INVOKE_INSTANCE:
          registry.registerInvokeVirtual(target);
          break;
        case INVOKE_CONSTRUCTOR:
        case INVOKE_DIRECT:
          registry.registerInvokeDirect(target);
          break;
        default:
          throw new Unreachable("Unexpected handle type: " + handle.type);
      }
    };
  }
}
