// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.androidapi.AndroidApiClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.function.Consumer;

/** This is a template for generating the AndroidApiDatabaseBuilder. */
public class AndroidApiDatabaseBuilderTemplate /* AndroidApiDatabaseBuilder */ {

  public static void visitApiClasses(Consumer<String> classDescriptorConsumer) {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    placeHolderForVisitApiClasses();
  }

  public static AndroidApiClass buildClass(ClassReference classReference) {
    String descriptor = classReference.getDescriptor();
    String packageName = DescriptorUtils.getPackageNameFromDescriptor(descriptor);
    String simpleClassName = DescriptorUtils.getSimpleClassNameFromDescriptor(descriptor);
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    placeHolderForBuildClass();
    return null;
  }

  private static void placeHolderForVisitApiClasses() {}

  private static void placeHolderForBuildClass() {}
}
