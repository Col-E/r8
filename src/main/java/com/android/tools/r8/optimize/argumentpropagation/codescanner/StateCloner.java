// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

/**
 * A strategy for cloning method and parameter states.
 *
 * <p>During the primary optimization pass, for each invoke we compute a fresh method state and join
 * the state into the existing method state for the resolved method. Since the added method state is
 * completely fresh and not stored anywhere else, we can avoid copying the method state when we join
 * it into the existing method state. This is achieved by using the {@link #getIdentity()} cloner
 * below.
 *
 * <p>When we later propagate argument information for virtual methods to their overrides, we join
 * method states from one virtual method into the state for another virtual method. Therefore, it is
 * important to copy the method state during the join, which is achieved using the {@link
 * #getCloner()} cloner.
 */
public abstract class StateCloner {

  private static StateCloner CLONER =
      new StateCloner() {
        @Override
        public MethodState mutableCopy(MethodState methodState) {
          return methodState.mutableCopy();
        }

        @Override
        public ParameterState mutableCopy(ParameterState parameterState) {
          return parameterState.mutableCopy();
        }
      };

  private static StateCloner IDENTITY =
      new StateCloner() {
        @Override
        public MethodState mutableCopy(MethodState methodState) {
          return methodState;
        }

        @Override
        public ParameterState mutableCopy(ParameterState parameterState) {
          return parameterState;
        }
      };

  public static StateCloner getCloner() {
    return CLONER;
  }

  public static StateCloner getIdentity() {
    return IDENTITY;
  }

  public abstract MethodState mutableCopy(MethodState methodState);

  public abstract ParameterState mutableCopy(ParameterState parameterState);
}
