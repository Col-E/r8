// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.androidapi.AndroidApiClass;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.BiFunction;

/** This is a template for generating AndroidApiDatabaseClass extending AndroidApiClass */
public class AndroidApiDatabaseClassTemplate extends AndroidApiClass {

  protected AndroidApiDatabaseClassTemplate() {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    super(Reference.classFromDescriptor(placeHolderForInit()));
  }

  @Override
  public AndroidApiLevel getApiLevel() {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    return placeHolderForGetApiLevel();
  }

  @Override
  public int getMemberCount() {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    return placeHolderForGetMemberCount();
  }

  @Override
  public TraversalContinuation visitFields(
      BiFunction<FieldReference, AndroidApiLevel, TraversalContinuation> visitor,
      ClassReference holder,
      int minApi) {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    placeHolderForVisitFields();
    return TraversalContinuation.CONTINUE;
  }

  @Override
  public TraversalContinuation visitMethods(
      BiFunction<MethodReference, AndroidApiLevel, TraversalContinuation> visitor,
      ClassReference holder,
      int minApi) {
    // Code added dynamically in AndroidApiDatabaseBuilderGenerator.
    placeHolderForVisitMethods();
    return TraversalContinuation.CONTINUE;
  }

  private static String placeHolderForInit() {
    return null;
  }

  private static AndroidApiLevel placeHolderForGetApiLevel() {
    return null;
  }

  private static int placeHolderForGetMemberCount() {
    return 0;
  }

  private static void placeHolderForVisitFields() {}

  private static void placeHolderForVisitMethods() {}
}
