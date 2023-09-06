// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.GraphReporter;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.google.common.collect.ImmutableList;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

@Keep
public class CheckDiscardDiagnostic implements Diagnostic {

  private final List<String> messages;

  public static class Builder {
    private ImmutableList.Builder<String> messagesBuilder = ImmutableList.builder();

    @SuppressWarnings("DefaultCharset")
    public Builder addFailedItems(
        List<ProgramDefinition> failed,
        GraphReporter graphReporter,
        WhyAreYouKeepingConsumer whyAreYouKeepingConsumer) {
      for (ProgramDefinition definition : failed) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        whyAreYouKeepingConsumer.printWhyAreYouKeeping(
            graphReporter.getGraphNode(definition.getReference()), new PrintStream(baos));
        messagesBuilder.add(
            "Item "
                + definition.getReference().toSourceString()
                + " was not discarded.\n"
                + baos.toString());
      }
      return this;
    }

    public CheckDiscardDiagnostic build() {
      return new CheckDiscardDiagnostic(messagesBuilder.build());
    }
  }

  private CheckDiscardDiagnostic(List<String> messages) {
    this.messages = messages;
  }

  public int getNumberOfFailures() {
    return messages.size();
  }

  /** The origin of a -checkdiscarded failure is not unique. (The whole app is to blame.) */
  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  /** The position of a -checkdiscarded failure is always unknown. */
  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    StringBuilder builder = new StringBuilder("Discard checks failed.");
    if (messages.size() > 0) {
      builder.append(System.lineSeparator());
      builder.append("The following items were not discarded");
      messages.forEach(
          message -> {
            builder.append(System.lineSeparator());
            builder.append(message);
          });
    }
    return builder.toString();
  }
}
