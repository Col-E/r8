// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizationsEventConsumer;
import com.android.tools.r8.ir.optimize.api.InstanceInitializerOutlinerEventConsumer;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxerMethodProcessorEventConsumer;
import com.android.tools.r8.profile.art.rewriting.ArtProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ArtProfileRewritingMethodProcessorEventConsumer;

public abstract class MethodProcessorEventConsumer
    implements EnumUnboxerMethodProcessorEventConsumer,
        InstanceInitializerOutlinerEventConsumer,
        UtilityMethodsForCodeOptimizationsEventConsumer {

  public static MethodProcessorEventConsumer create(
      ArtProfileCollectionAdditions artProfileCollectionAdditions) {
    return ArtProfileRewritingMethodProcessorEventConsumer.attach(
        artProfileCollectionAdditions, empty());
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
