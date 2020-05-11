// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Consumer;

/**
 * An abstraction of {@link IRCode}-level optimization, which may retrieve info from
 * {@link AppView}; update {@link OptimizationFeedback}; or utilize {@link MethodProcessor}.
 */
public interface CodeOptimization {

  // TODO(b/140766440): if some other code optimizations require more info to pass, i.e., requires
  //  a signature change, we may need to turn this interface into an abstract base, instead of
  //  rewriting every affected optimization.
  // Note that a code optimization can be a collection of other code optimizations.
  // In that way, IRConverter will serve as the default full processing of all optimizations.
  void optimize(
      IRCode code,
      OptimizationFeedback feedback,
      MethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId);

  static CodeOptimization from(Consumer<IRCode> consumer) {
    return (code, feedback, methodProcessor, methodProcessingId) -> {
      consumer.accept(code);
    };
  }

  static CodeOptimization sequence(CodeOptimization... codeOptimizations) {
    return sequence(Arrays.asList(codeOptimizations));
  }

  static CodeOptimization sequence(Collection<CodeOptimization> codeOptimizations) {
    return (code, feedback, methodProcessor, methodProcessingId) -> {
      for (CodeOptimization codeOptimization : codeOptimizations) {
        codeOptimization.optimize(code, feedback, methodProcessor, methodProcessingId);
      }
    };
  }
}
