// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.diagnostic.HumanReadableArtProfileParserErrorDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.Reporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;

public class HumanReadableArtProfileParser {

  private final ArtProfileBuilder profileBuilder;
  private final Reporter reporter;

  HumanReadableArtProfileParser(ArtProfileBuilder profileBuilder, Reporter reporter) {
    this.profileBuilder = profileBuilder;
    this.reporter = reporter;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void parse(TextInputStream textInputStream, Origin origin) {
    try {
      try (InputStreamReader inputStreamReader =
              new InputStreamReader(
                  textInputStream.getInputStream(), textInputStream.getCharset());
          BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
        int lineNumber = 1;
        while (bufferedReader.ready()) {
          String rule = bufferedReader.readLine();
          if (!parseRule(rule)) {
            parseError(rule, lineNumber, origin);
          }
          lineNumber++;
        }
      }
      if (reporter != null) {
        reporter.failIfPendingErrors();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void parseError(String rule, int lineNumber, Origin origin) {
    if (reporter != null) {
      reporter.error(new HumanReadableArtProfileParserErrorDiagnostic(rule, lineNumber, origin));
    }
  }

  public boolean parseRule(String rule) {
    ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder =
        ArtProfileMethodRuleInfoImpl.builder();
    rule = parseFlag(rule, 'H', methodRuleInfoBuilder::setHot);
    rule = parseFlag(rule, 'S', methodRuleInfoBuilder::setStartup);
    rule = parseFlag(rule, 'P', methodRuleInfoBuilder::setPostStartup);
    return parseClassOrMethodDescriptor(rule, methodRuleInfoBuilder.build());
  }

  private static String parseFlag(String rule, char c, Action action) {
    if (!rule.isEmpty() && rule.charAt(0) == c) {
      action.execute();
      return rule.substring(1);
    }
    return rule;
  }

  private boolean parseClassOrMethodDescriptor(
      String descriptor, ArtProfileMethodRuleInfoImpl methodRuleInfo) {
    int arrowStartIndex = descriptor.indexOf("->");
    if (arrowStartIndex >= 0) {
      return parseMethodRule(descriptor, methodRuleInfo, arrowStartIndex);
    } else if (methodRuleInfo.isEmpty()) {
      return parseClassRule(descriptor);
    } else {
      return false;
    }
  }

  private boolean parseClassRule(String descriptor) {
    ClassReference classReference = ClassReferenceUtils.parseClassDescriptor(descriptor);
    if (classReference == null) {
      return false;
    }
    profileBuilder.addClassRule(classReference, ArtProfileClassRuleInfoImpl.empty());
    return true;
  }

  private boolean parseMethodRule(
      String descriptor, ArtProfileMethodRuleInfoImpl methodRuleInfo, int arrowStartIndex) {
    MethodReference methodReference =
        MethodReferenceUtils.parseSmaliString(descriptor, arrowStartIndex);
    if (methodReference == null) {
      return false;
    }
    profileBuilder.addMethodRule(methodReference, methodRuleInfo);
    return true;
  }

  public static class Builder {

    private ArtProfileBuilder profileBuilder;
    private Reporter reporter;

    public Builder setReporter(Reporter reporter) {
      this.reporter = reporter;
      return this;
    }

    public Builder setProfileBuilder(ArtProfileBuilder profileBuilder) {
      this.profileBuilder = profileBuilder;
      return this;
    }

    public HumanReadableArtProfileParser build() {
      return new HumanReadableArtProfileParser(profileBuilder, reporter);
    }
  }
}
