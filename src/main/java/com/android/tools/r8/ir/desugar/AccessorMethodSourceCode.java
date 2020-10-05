// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;

// Source code representing synthesized accessor method.

public class AccessorMethodSourceCode {

  public static CfCode build(LambdaClass lambda, DexMethod accessor) {
    DexMethod target = lambda.descriptor.implHandle.asMethod();
    ForwardMethodBuilder forwardMethodBuilder =
        ForwardMethodBuilder.builder(lambda.appView.dexItemFactory()).setStaticSource(accessor);
    switch (lambda.descriptor.implHandle.type) {
      case INVOKE_INSTANCE:
        {
          forwardMethodBuilder.setVirtualTarget(target, false);
          break;
        }
      case INVOKE_STATIC:
        {
          forwardMethodBuilder.setStaticTarget(target, false);
          break;
        }
      case INVOKE_DIRECT:
        {
          forwardMethodBuilder.setDirectTarget(target, false);
          break;
        }
      case INVOKE_CONSTRUCTOR:
        {
          forwardMethodBuilder.setConstructorTarget(target, lambda.appView.dexItemFactory());
          break;
        }
      case INVOKE_INTERFACE:
        throw new Unreachable("Accessor for an interface method?");
      default:
        throw new Unreachable();
    }
    return forwardMethodBuilder.build();
  }
}
