// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.naming.MemberNaming.MethodSignature;

public class TestFileLevelKotlinClass extends TestKotlinClass {

  public TestFileLevelKotlinClass(String className) {
    super(className);
  }

  @Override
  public TestFileLevelKotlinClass addProperty(String name, String type, Visibility visibility) {
    return (TestFileLevelKotlinClass) super.addProperty(name, type, visibility);
  }

  @Override
  public MethodSignature getGetterForProperty(String propertyName) {
    KotlinProperty property = getProperty(propertyName);
    // File-level properties accessor do not apply mangling.
    return getGetterForProperty(property, false);
  }

  @Override
  public MethodSignature getSetterForProperty(String propertyName) {
    KotlinProperty property = getProperty(propertyName);
    // File-level properties accessor do not apply mangling.
    return getSetterForProperty(property, false);
  }
}
