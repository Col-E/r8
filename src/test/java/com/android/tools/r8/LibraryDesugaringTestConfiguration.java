// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.errors.Unreachable;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LibraryDesugaringTestConfiguration {

  private final List<StringResource> desugaredLibrarySpecificationResources;
  private final KeepRuleConsumer keepRuleConsumer;

  public static final LibraryDesugaringTestConfiguration DISABLED =
      new LibraryDesugaringTestConfiguration();

  private LibraryDesugaringTestConfiguration() {
    this.keepRuleConsumer = null;
    this.desugaredLibrarySpecificationResources = null;
  }

  private LibraryDesugaringTestConfiguration(
      List<StringResource> desugaredLibrarySpecificationResources,
      KeepRuleConsumer keepRuleConsumer) {
    this.desugaredLibrarySpecificationResources = desugaredLibrarySpecificationResources;
    this.keepRuleConsumer = keepRuleConsumer;
  }

  public static class Builder {

    private final List<StringResource> desugaredLibrarySpecificationResources = new ArrayList<>();
    KeepRuleConsumer keepRuleConsumer;
    private Builder() {}

    public Builder setKeepRuleConsumer(KeepRuleConsumer keepRuleConsumer) {
      this.keepRuleConsumer = keepRuleConsumer;
      return this;
    }

    public Builder addDesugaredLibraryConfiguration(StringResource desugaredLibrarySpecification) {
      desugaredLibrarySpecificationResources.add(desugaredLibrarySpecification);
      return this;
    }

    public LibraryDesugaringTestConfiguration build() {
      assert !desugaredLibrarySpecificationResources.isEmpty();
      return new LibraryDesugaringTestConfiguration(
          desugaredLibrarySpecificationResources, keepRuleConsumer);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static LibraryDesugaringTestConfiguration forSpecification(Path specification) {
    return LibraryDesugaringTestConfiguration.builder()
        .addDesugaredLibraryConfiguration(StringResource.fromFile(specification))
        .build();
  }

  public boolean isEnabled() {
    return this != DISABLED;
  }

  public void configure(D8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    if (keepRuleConsumer != null) {
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    desugaredLibrarySpecificationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }

  public void configure(R8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    if (keepRuleConsumer != null) {
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    desugaredLibrarySpecificationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }

  public static class PresentKeepRuleConsumer implements KeepRuleConsumer {

    StringBuilder stringBuilder = new StringBuilder();
    String result = null;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      stringBuilder.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      result = stringBuilder.toString();
      stringBuilder = null;
    }

    public String get() {
      // TODO(clement): remove that branch once StringConsumer has finished again.
      if (stringBuilder != null) {
        finished(null);
      }

      assert stringBuilder == null;
      assert result != null;
      return result;
    }
  }

  public static class AbsentKeepRuleConsumer implements KeepRuleConsumer {

    public String get() {
      return null;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }
  }
}
