// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class ClassMayHaveInitializationSideEffectsTestBase extends TestBase {

  public void assertMayHaveClassInitializationSideEffects(
      AppView<AppInfoWithLiveness> appView, Class<?> clazz) {
    assertMayHaveClassInitializationSideEffectsEquals(appView, clazz, true);
  }

  public void assertNoClassInitializationSideEffects(
      AppView<AppInfoWithLiveness> appView, Class<?> clazz) {
    assertMayHaveClassInitializationSideEffectsEquals(appView, clazz, false);
  }

  public void assertMayHaveClassInitializationSideEffectsEquals(
      AppView<AppInfoWithLiveness> appView, Class<?> clazz, boolean expected) {
    DexType type = toDexType(clazz, appView.dexItemFactory());
    DexProgramClass programClass = asProgramClassOrNull(appView.appInfo().definitionFor(type));
    assertNotNull(programClass);
    assertEquals(expected, programClass.classInitializationMayHaveSideEffects(appView));
  }
}
