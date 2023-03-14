// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.AssertionErrorTwoArgsConstructorRewriterEventConsumer;
import com.android.tools.r8.ir.optimize.ServiceLoaderRewriterEventConsumer;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizationsEventConsumer;
import com.android.tools.r8.ir.optimize.api.InstanceInitializerOutlinerEventConsumer;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxerMethodProcessorEventConsumer;
import com.android.tools.r8.profile.art.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ProfileRewritingMethodProcessorEventConsumer;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class MethodProcessorEventConsumer
    implements AssertionErrorTwoArgsConstructorRewriterEventConsumer,
        EnumUnboxerMethodProcessorEventConsumer,
        InstanceInitializerOutlinerEventConsumer,
        ServiceLoaderRewriterEventConsumer,
        UtilityMethodsForCodeOptimizationsEventConsumer {

  public void finished(AppView<AppInfoWithLiveness> appView) {}

  public static MethodProcessorEventConsumer createForD8(
      ProfileCollectionAdditions profileCollectionAdditions) {
    return ProfileRewritingMethodProcessorEventConsumer.attach(profileCollectionAdditions, empty());
  }

  public static MethodProcessorEventConsumer createForR8(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return ProfileRewritingMethodProcessorEventConsumer.attach(appView, empty());
  }

  public static MethodProcessorEventConsumer empty() {
    return EmptyMethodProcessorEventConsumer.getInstance();
  }

  private static class EmptyMethodProcessorEventConsumer extends MethodProcessorEventConsumer {

    private static final EmptyMethodProcessorEventConsumer INSTANCE =
        new EmptyMethodProcessorEventConsumer();

    private EmptyMethodProcessorEventConsumer() {}

    static EmptyMethodProcessorEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptAssertionErrorCreateMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptEnumUnboxerCheckNotZeroContext(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptEnumUnboxerLocalUtilityClassMethodContext(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptEnumUnboxerSharedUtilityClassMethodContext(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptInstanceInitializerOutline(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptServiceLoaderLoadUtilityMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowIllegalAccessErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowNoSuchMethodErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }
  }
}
