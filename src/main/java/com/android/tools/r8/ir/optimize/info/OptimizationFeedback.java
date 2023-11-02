// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.conversion.FieldOptimizationFeedback;
import com.android.tools.r8.ir.conversion.MethodOptimizationFeedback;
import com.android.tools.r8.shaking.AppInfoWithLivenessModifier;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public abstract class OptimizationFeedback
    implements FieldOptimizationFeedback, MethodOptimizationFeedback {

  public interface OptimizationInfoFixer {

    void fixup(DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo);

    void fixup(DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo);

    default void fixup(DexEncodedMember<?, ?> member) {
      MemberOptimizationInfo<?> optimizationInfo = member.getOptimizationInfo();
      if (optimizationInfo.isMutableOptimizationInfo()) {
        member.accept(
            field -> fixup(field, optimizationInfo.asMutableFieldOptimizationInfo()),
            method -> fixup(method, optimizationInfo.asMutableMethodOptimizationInfo()));
      }
    }
  }

  public static OptimizationFeedbackIgnore getIgnoreFeedback() {
    return OptimizationFeedbackIgnore.getInstance();
  }

  public static OptimizationFeedbackSimple getSimple() {
    return OptimizationFeedbackSimple.getInstance();
  }

  public static OptimizationFeedbackSimple getSimpleFeedback() {
    return getSimple();
  }

  public void fixupOptimizationInfos(
      AppView<?> appView,
      ThreadingModule threadingModule,
      ExecutorService executorService,
      OptimizationInfoFixer fixer)
      throws ExecutionException {
    fixupOptimizationInfos(appView.appInfo().classes(), threadingModule, executorService, fixer);
  }

  public void fixupOptimizationInfos(
      Collection<DexProgramClass> classes,
      ThreadingModule threadingModule,
      ExecutorService executorService,
      OptimizationInfoFixer fixer)
      throws ExecutionException {
    ThreadUtils.processItems(
        classes, clazz -> clazz.members().forEach(fixer::fixup), threadingModule, executorService);
  }

  public void modifyAppInfoWithLiveness(Consumer<AppInfoWithLivenessModifier> consumer) {
    // Intentionally empty.
  }
}
