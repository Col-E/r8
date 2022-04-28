// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;

import com.android.tools.r8.utils.FeatureClassMapping.FeatureMappingException;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class FeatureClassMappingTest {

  @Test
  public void testSimpleParse() throws Exception {

    List<String> lines =
        ImmutableList.of(
            "# Comment, don't care about contents: even more ::::",
            "com.google.base:base",
            "", // Empty lines allowed
            "com.google.feature1:feature1",
            "com.google.feature1:feature1", // Multiple definitions of the same predicate allowed.
            "com.google$:feature1",
            "_com.google:feature21",
            "com.google.*:feature32");
    FeatureClassMapping mapping = new FeatureClassMapping(lines);
  }

  private void ensureThrowsMappingException(List<String> lines) {
    try {
      new FeatureClassMapping(lines);
      assertFalse(true);
    } catch (FeatureMappingException e) {
      // Expected
    }
  }

  private void ensureThrowsMappingException(String string) {
    ensureThrowsMappingException(ImmutableList.of(string));
  }

  @Test
  public void testLookup() throws Exception {
    List<String> lines =
        ImmutableList.of(
            "com.google.Base:base",
            "",
            "com.google.Feature1:feature1",
            "com.google.Feature1:feature1", // Multiple definitions of the same predicate allowed.
            "com.google.different.*:feature1",
            "_com.Google:feature21",
            "com.google.bas42.*:feature42");
    FeatureClassMapping mapping = new FeatureClassMapping(lines);
    assertEquals(mapping.featureForClass("com.google.Feature1"), "feature1");
    assertEquals(mapping.featureForClass("com.google.different.Feature1"), "feature1");
    assertEquals(mapping.featureForClass("com.google.different.Foobar"), "feature1");
    assertEquals(mapping.featureForClass("com.google.Base"), "base");
    assertEquals(mapping.featureForClass("com.google.bas42.foo.bar.bar.Foo"), "feature42");
    assertEquals(mapping.featureForClass("com.google.bas42.f$o$o$.bar43.bar.Foo"), "feature42");
    assertEquals(mapping.featureForClass("_com.Google"), "feature21");
  }

  @Test
  public void testCatchAllWildcards() throws Exception {
    testBaseWildcard(true);
    testBaseWildcard(false);
    testNonBaseCatchAll();
  }

  private void testNonBaseCatchAll() throws FeatureMappingException {
    List<String> lines =
        ImmutableList.of(
            "com.google.Feature1:feature1",
            "*:nonbase",
            "com.strange.*:feature2");
    FeatureClassMapping mapping = new FeatureClassMapping(lines);
    assertEquals(mapping.featureForClass("com.google.Feature1"), "feature1");
    assertEquals(mapping.featureForClass("com.google.different.Feature1"), "nonbase");
    assertEquals(mapping.featureForClass("com.strange.different.Feature1"), "feature2");
    assertEquals(mapping.featureForClass("Feature1"), "nonbase");
    assertEquals(mapping.featureForClass("a.b.z.A"), "nonbase");
  }

  private void testBaseWildcard(boolean explicitBase) throws FeatureMappingException {
    List<String> lines =
        ImmutableList.of(
            "com.google.Feature1:feature1",
            explicitBase ? "*:base" : "",
            "com.strange.*:feature2");
    FeatureClassMapping mapping = new FeatureClassMapping(lines);
    assertEquals(mapping.featureForClass("com.google.Feature1"), "feature1");
    assertEquals(mapping.featureForClass("com.google.different.Feature1"), "base");
    assertEquals(mapping.featureForClass("com.strange.different.Feature1"), "feature2");
    assertEquals(mapping.featureForClass("com.stranger.Clazz"), "base");
    assertEquals(mapping.featureForClass("Feature1"), "base");
    assertEquals(mapping.featureForClass("a.b.z.A"), "base");
  }

  @Test
  public void testWrongLines() throws Exception {
    // No colon.
    ensureThrowsMappingException("foo");
    ensureThrowsMappingException("com.google.base");
    // Two colons.
    ensureThrowsMappingException(ImmutableList.of("a:b:c"));

    // Empty identifier.
    ensureThrowsMappingException("com..google:feature1");

    // Ambiguous redefinition
    ensureThrowsMappingException(
        ImmutableList.of("com.google.foo:feature1", "com.google.foo:feature2"));
    ensureThrowsMappingException(
        ImmutableList.of("com.google.foo.*:feature1", "com.google.foo.*:feature2"));
  }

  @Test
  public void testUsesOnlyExactMappings() throws Exception {
    List<String> lines =
        ImmutableList.of(
            "com.pkg1.Clazz:feature1",
            "com.pkg2.Clazz:feature2");
    FeatureClassMapping mapping = new FeatureClassMapping(lines);

    assertEquals(mapping.featureForClass("com.pkg1.Clazz"), "feature1");
    assertEquals(mapping.featureForClass("com.pkg2.Clazz"), "feature2");
    assertEquals(mapping.featureForClass("com.pkg1.Other"), mapping.baseName);
  }
}
