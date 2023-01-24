// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class ArtProfileMatchers {

  public static Matcher<ExternalArtProfile> containsClassRule(ClassReference classReference) {
    return new TypeSafeMatcher<ExternalArtProfile>() {
      @Override
      protected boolean matchesSafely(ExternalArtProfile subject) {
        return subject.containsClassRule(classReference);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "contains class rule " + ClassReferenceUtils.toSmaliString(classReference));
      }

      @Override
      public void describeMismatchSafely(ExternalArtProfile subject, Description description) {
        description.appendText("profile did not");
      }
    };
  }

  public static Matcher<ExternalArtProfile> containsClassRule(ClassSubject classSubject) {
    assertThat(classSubject, isPresent());
    return containsClassRule(classSubject.getFinalReference());
  }

  public static Matcher<ExternalArtProfile> containsMethodRule(MethodReference methodReference) {
    return new TypeSafeMatcher<ExternalArtProfile>() {
      @Override
      protected boolean matchesSafely(ExternalArtProfile subject) {
        return subject.containsMethodRule(methodReference);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText(
            "contains method rule " + MethodReferenceUtils.toSmaliString(methodReference));
      }

      @Override
      public void describeMismatchSafely(ExternalArtProfile subject, Description description) {
        description.appendText("profile did not");
      }
    };
  }

  public static Matcher<ExternalArtProfile> containsMethodRule(MethodSubject methodSubject) {
    assertThat(methodSubject, isPresent());
    return containsMethodRule(methodSubject.getFinalReference());
  }
}
