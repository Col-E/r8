// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.google.common.base.Predicates;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinClassStaticizerTest extends AbstractR8KotlinTestBase {

  @Parameterized.Parameters(name = "target: {0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public KotlinClassStaticizerTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void testCompanionAndRegularObjects() throws Exception {
    expectThrowsWithHorizontalClassMerging();
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_staticizer.MainKt";

    // Without class staticizer.
    runTest(
        "class_staticizer",
        mainClassName,
        false,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          assertThat(inspector.clazz("class_staticizer.Regular$Companion"), isPresent());
          assertThat(inspector.clazz("class_staticizer.Derived$Companion"), isPresent());

          // The Util class is there, but its instance methods have been inlined.
          ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
          assertThat(utilClass, isPresent());
          assertTrue(
              utilClass.allMethods().stream()
                  .filter(Predicates.not(FoundMethodSubject::isStatic))
                  .allMatch(FoundMethodSubject::isInstanceInitializer));
        });

    // With class staticizer.
    runTest(
        "class_staticizer",
        mainClassName,
        true,
        app -> {
          CodeInspector inspector = new CodeInspector(app);
          assertThat(inspector.clazz("class_staticizer.Regular$Companion"), not(isPresent()));
          assertThat(inspector.clazz("class_staticizer.Derived$Companion"), not(isPresent()));

          ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
          assertThat(utilClass, isPresent());
          assertTrue(utilClass.allMethods().stream().allMatch(FoundMethodSubject::isStatic));
        });
  }

  protected void runTest(String folder, String mainClass,
      boolean enabled, AndroidAppInspector inspector) throws Exception {
    runTest(
        folder,
        mainClass,
        null,
        options -> {
          options.enableClassInlining = false;
          options.enableClassStaticizer = enabled;
        },
        inspector);
  }
}
