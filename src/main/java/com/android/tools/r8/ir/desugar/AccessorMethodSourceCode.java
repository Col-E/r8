// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;

// Source code representing synthesized accessor method.

public class AccessorMethodSourceCode {

  public static CfCode build(
      DexMethod target,
      boolean isInterface,
      MethodHandleType type,
      DexMethod accessor,
      AppView<?> appView) {
    ForwardMethodBuilder forwardMethodBuilder =
        ForwardMethodBuilder.builder(appView.dexItemFactory()).setStaticSource(accessor);
    switch (type) {
      case INVOKE_INSTANCE:
      case INVOKE_INTERFACE:
        {
          forwardMethodBuilder.setVirtualTarget(target, isInterface);
          break;
        }
      case INVOKE_STATIC:
        {
          forwardMethodBuilder.setStaticTarget(target, isInterface);
          break;
        }
      case INVOKE_DIRECT:
        {
          forwardMethodBuilder.setDirectTarget(target, isInterface);
          break;
        }
      case INVOKE_CONSTRUCTOR:
        {
          forwardMethodBuilder.setConstructorTargetWithNewInstance(target);
          break;
        }
      default:
        throw new Unreachable();
    }
    return forwardMethodBuilder.build();
  }
}
