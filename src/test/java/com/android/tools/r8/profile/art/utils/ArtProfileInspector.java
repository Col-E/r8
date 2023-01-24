// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

public class ArtProfileInspector {

  private final ExternalArtProfile artProfile;

  private final Set<ClassReference> checkedClassReferences = new HashSet<>();
  private final Set<MethodReference> checkedMethodReferences = new HashSet<>();

  public ArtProfileInspector(ExternalArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  public ArtProfileInspector assertEmpty() {
    assertEquals(0, artProfile.size());
    return this;
  }

  public ArtProfileInspector assertNotEmpty() {
    assertNotEquals(0, artProfile.size());
    return this;
  }

  public ArtProfileInspector assertContainsClassRule(ClassReference classReference) {
    assertThat(artProfile, ArtProfileMatchers.containsClassRule(classReference));
    checkedClassReferences.add(classReference);
    return this;
  }

  public ArtProfileInspector assertContainsClassRule(ClassSubject classSubject) {
    return assertContainsClassRule(classSubject.getFinalReference());
  }

  public ArtProfileInspector assertContainsClassRules(ClassReference... classReferences) {
    for (ClassReference classReference : classReferences) {
      assertContainsClassRule(classReference);
    }
    return this;
  }

  public ArtProfileInspector assertContainsMethodRule(MethodReference methodReference) {
    assertThat(artProfile, ArtProfileMatchers.containsMethodRule(methodReference));
    checkedMethodReferences.add(methodReference);
    return this;
  }

  public ArtProfileInspector assertContainsMethodRules(MethodReference... methodReferences) {
    for (MethodReference methodReference : methodReferences) {
      assertContainsMethodRule(methodReference);
    }
    return this;
  }

  public ArtProfileInspector assertContainsMethodRule(MethodSubject methodSubject) {
    return assertContainsMethodRule(methodSubject.getFinalReference());
  }

  public ArtProfileInspector assertContainsMethodRules(MethodSubject... methodSubjects) {
    for (MethodSubject methodSubject : methodSubjects) {
      assertContainsMethodRule(methodSubject);
    }
    return this;
  }

  public ArtProfileInspector assertContainsNoOtherRules() {
    assertEquals(checkedClassReferences.size() + checkedMethodReferences.size(), artProfile.size());
    return this;
  }

  public ArtProfileInspector inspectClassRule(
      ClassReference classReference, Consumer<ArtProfileClassRuleInspector> inspector) {
    assertContainsClassRule(classReference);
    ExternalArtProfileClassRule classRule = artProfile.getClassRule(classReference);
    inspector.accept(new ArtProfileClassRuleInspector(classRule));
    return this;
  }

  public ArtProfileInspector inspectMethodRule(
      MethodReference methodReference, Consumer<ArtProfileMethodRuleInspector> inspector) {
    assertContainsMethodRule(methodReference);
    ExternalArtProfileMethodRule methodRule = artProfile.getMethodRule(methodReference);
    inspector.accept(new ArtProfileMethodRuleInspector(methodRule));
    return this;
  }
}
