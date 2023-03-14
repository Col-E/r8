// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.profile.art.rewriting.ProfileRewritingApiReferenceStubberEventConsumer;

public interface ApiReferenceStubberEventConsumer {

  void acceptMockedLibraryClass(DexProgramClass mockClass, DexLibraryClass libraryClass);

  void acceptMockedLibraryClassContext(
      DexProgramClass mockClass, DexLibraryClass libraryClass, DexProgramClass context);

  default void finished(AppView<?> appView) {}

  boolean isEmpty();

  static ApiReferenceStubberEventConsumer create(AppView<?> appView) {
    return ProfileRewritingApiReferenceStubberEventConsumer.attach(appView, empty());
  }

  static EmptyApiReferenceStubberEventConsumer empty() {
    return EmptyApiReferenceStubberEventConsumer.getInstance();
  }

  class EmptyApiReferenceStubberEventConsumer implements ApiReferenceStubberEventConsumer {

    private static final EmptyApiReferenceStubberEventConsumer INSTANCE =
        new EmptyApiReferenceStubberEventConsumer();

    private EmptyApiReferenceStubberEventConsumer() {}

    static EmptyApiReferenceStubberEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptMockedLibraryClass(DexProgramClass mockClass, DexLibraryClass libraryClass) {
      // Intentionally empty.
    }

    @Override
    public void acceptMockedLibraryClassContext(
        DexProgramClass mockClass, DexLibraryClass libraryClass, DexProgramClass context) {
      // Intentionally empty.
    }

    @Override
    public boolean isEmpty() {
      return true;
    }
  }
}
