// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class TestBackportedNotPresentInAndroidJar extends TestBase {

  @Test
  public void testBackportedMethodsPerAPILevel() throws Exception {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.values()) {
      if (!ToolHelper.hasAndroidJar(apiLevel)) {
        // Only check for the android jar versions present in third_party.
        System.out.println("Skipping check for " + apiLevel);
        continue;
      }
      // Check that the backported methods for each API level are are not present in the
      // android.jar for that level.
      CodeInspector inspector = new CodeInspector(ToolHelper.getAndroidJar(apiLevel));
      InternalOptions options = new InternalOptions();
      options.minApiLevel = apiLevel.getLevel();
      List<DexMethod> backportedMethods =
          BackportedMethodRewriter.generateListOfBackportedMethods(
              AndroidApp.builder().build(), options, ThreadUtils.getExecutorService(options));
      for (DexMethod method : backportedMethods) {
        // Two different DexItemFactories are in play, but as toSourceString is used for lookup
        // that is not an issue.
        ClassSubject clazz = inspector.clazz(method.holder.toSourceString());
        MethodSubject foundInAndroidJar =
            clazz.method(
                method.proto.returnType.toSourceString(),
                method.name.toSourceString(),
                Arrays.stream(method.proto.parameters.values)
                    .map(DexType::toSourceString)
                    .collect(Collectors.toList()));
        assertThat(
            foundInAndroidJar + " present in " + apiLevel, foundInAndroidJar, not(isPresent()));
      }
    }
  }
}
