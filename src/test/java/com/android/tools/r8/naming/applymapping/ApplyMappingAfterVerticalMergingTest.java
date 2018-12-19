// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class Resources {
}

@NeverMerge
class ResourceWrapper extends Resources {
  // Will be merged down, and represented as:
  //     ...applymapping.Resources ...applymapping.ResourceWrapper.mResources -> a
  private Resources mResources;

  ResourceWrapper(Resources resource) {
    this.mResources = resource;
  }

  // Will be merged down, and represented as:
  //     java.lang.String ...applymapping.ResourceWrapper.foo() -> a
  String foo() {
    return mResources.toString();
  }

  @Override
  public String toString() {
    return mResources.toString();
  }
}

class TintResources extends ResourceWrapper {
  private WeakReference<Resources> ref;

  TintResources(Resources resource) {
    super(resource);
    ref = new WeakReference<>(resource);
  }

  public static void main(String[] args) {
    TintResources t = new TintResources(new Resources());
    System.out.println(t.foo());
  }
}

@RunWith(Parameterized.class)
public class ApplyMappingAfterVerticalMergingTest extends TestBase {
  private final static Class<?>[] CLASSES = {
      NeverMerge.class, Resources.class, ResourceWrapper.class, TintResources.class
  };
  private final static Class<?> MAIN = TintResources.class;

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public ApplyMappingAfterVerticalMergingTest(Backend backend) {
    this.backend = backend;
  }

  @Ignore("b/12042934")
  @Test
  public void b121042934() throws Exception {
    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    CodeInspector inspector1 = testForR8(backend)
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN)
        .addKeepRules("-printmapping " + mapPath.toAbsolutePath())
        .compile()
        .inspector();
    CodeInspector inspector2 = testForR8(backend)
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mapPath.toAbsolutePath())
        .compile()
        .inspector();

    ClassSubject classSubject1 = inspector1.clazz(MAIN);
    assertThat(classSubject1, isPresent());
    assertThat(classSubject1, isRenamed());
    ClassSubject classSubject2 = inspector2.clazz(
        DescriptorUtils.getClassNameFromDescriptor(classSubject1.getFinalDescriptor()));
    assertThat(classSubject2, isPresent());
    assertThat(classSubject2, not(isRenamed()));
    assertEquals(classSubject1.getFinalDescriptor(), classSubject2.getFinalDescriptor());

    FieldSubject field1 = classSubject1.uniqueFieldWithName("mResources");
    assertThat(field1, isPresent());
    assertThat(field1, isRenamed());
    FieldSubject field2 = classSubject2.uniqueFieldWithName("mResources");
    assertThat(field2, isPresent());
    assertThat(field2, not(isRenamed()));
    assertEquals(field1.getFinalSignature().toString(), field2.getFinalSignature().toString());

    MethodSubject method1 = classSubject1.uniqueMethodWithName("foo");
    assertThat(method1, isPresent());
    assertThat(method1, isRenamed());
    MethodSubject method2 = classSubject2.uniqueMethodWithName("foo");
    assertThat(method2, isPresent());
    assertThat(method2, not(isRenamed()));
    assertEquals(method1.getFinalSignature().toString(), method2.getFinalSignature().toString());
  }

}
