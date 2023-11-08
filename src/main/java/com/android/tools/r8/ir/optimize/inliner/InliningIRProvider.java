// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.origin.Origin;
import java.util.IdentityHashMap;
import java.util.Map;

public class InliningIRProvider {

  private final AppView<?> appView;
  private final ProgramMethod context;
  private final LensCodeRewriter lensCodeRewriter;
  private final NumberGenerator valueNumberGenerator;
  private final MethodProcessor methodProcessor;

  private final Map<InvokeMethod, IRCode> cache = new IdentityHashMap<>();

  private InliningIRProvider() {
    this.appView = null;
    this.context = null;
    this.lensCodeRewriter = null;
    this.valueNumberGenerator = null;
    this.methodProcessor = null;
  }

  public InliningIRProvider(
      AppView<?> appView,
      ProgramMethod context,
      IRCode code,
      LensCodeRewriter lensCodeRewriter,
      MethodProcessor methodProcessor) {
    this.appView = appView;
    this.context = context;
    this.lensCodeRewriter = lensCodeRewriter;
    this.valueNumberGenerator = code.valueNumberGenerator;
    this.methodProcessor = methodProcessor;
  }

  public static InliningIRProvider getThrowingInstance() {
    return new InliningIRProvider() {
      @Override
      public IRCode getInliningIR(InvokeMethod invoke, ProgramMethod method) {
        throw new Unreachable();
      }

      @Override
      public IRCode getAndCacheInliningIR(InvokeMethod invoke, ProgramMethod method) {
        throw new Unreachable();
      }

      @Override
      public void cacheInliningIR(InvokeMethod invoke, IRCode code) {
        throw new Unreachable();
      }

      @Override
      public MethodProcessor getMethodProcessor() {
        throw new Unreachable();
      }

      @Override
      public boolean verifyIRCacheIsEmpty() {
        throw new Unreachable();
      }
    };
  }

  public IRCode getInliningIR(InvokeMethod invoke, ProgramMethod method) {
    IRCode cached = cache.remove(invoke);
    if (cached != null) {
      return cached;
    }
    Origin origin = method.getOrigin();
    IRCode code =
        method.buildInliningIR(
            context,
            appView,
            valueNumberGenerator,
            Position.getPositionForInlining(invoke, context),
            origin,
            methodProcessor);
    if (lensCodeRewriter != null && methodProcessor.shouldApplyCodeRewritings(method)) {
      lensCodeRewriter.rewrite(code, method, methodProcessor);
    }
    return code;
  }

  public IRCode getAndCacheInliningIR(InvokeMethod invoke, ProgramMethod method) {
    IRCode inliningIR = getInliningIR(invoke, method);
    cacheInliningIR(invoke, inliningIR);
    return inliningIR;
  }

  public void cacheInliningIR(InvokeMethod invoke, IRCode code) {
    IRCode existing = cache.put(invoke, code);
    assert existing == null;
  }

  public MethodProcessor getMethodProcessor() {
    return methodProcessor;
  }

  public boolean verifyIRCacheIsEmpty() {
    assert cache.isEmpty();
    return true;
  }
}
