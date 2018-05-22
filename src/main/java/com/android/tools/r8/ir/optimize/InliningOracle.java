// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokePolymorphic;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.optimize.Inliner.InlineAction;

/**
 * The InliningOracle contains information needed for when inlining other methods into @method.
 */
public interface InliningOracle {

  void finish();

  InlineAction computeForInvokeWithReceiver(
      InvokeMethodWithReceiver invoke, DexType invocationContext);

  InlineAction computeForInvokeStatic(
      InvokeStatic invoke, DexType invocationContext);

  InlineAction computeForInvokePolymorphic(
      InvokePolymorphic invoke, DexType invocationContext);
}
