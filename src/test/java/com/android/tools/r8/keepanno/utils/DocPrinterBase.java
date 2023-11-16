// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import com.android.tools.r8.examples.sync.Sync.Consumer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DocPrinterBase<T> {

  private String title = null;
  private final List<String> additionalLines = new ArrayList<>();

  public abstract T self();

  public T clearDocLines() {
    additionalLines.clear();
    return self();
  }

  public T setDocTitle(String title) {
    assert this.title == null;
    assert title.endsWith(".");
    this.title = title;
    return self();
  }

  public T addParagraph(String... lines) {
    return addParagraph(Arrays.asList(lines));
  }

  public T addParagraph(List<String> lines) {
    assert lines.isEmpty() || !lines.get(0).startsWith("<p>");
    additionalLines.add("<p>");
    additionalLines.addAll(lines);
    return self();
  }

  public T addCodeBlock(String... lines) {
    return addCodeBlock(Arrays.asList(lines));
  }

  public T addCodeBlock(List<String> lines) {
    additionalLines.add("<pre>");
    additionalLines.addAll(lines);
    additionalLines.add("</pre>");
    return self();
  }

  public T addUnorderedList(String... items) {
    return addUnorderedList(Arrays.asList(items));
  }

  public T addUnorderedList(List<String> items) {
    additionalLines.add("<ul>");
    for (String item : items) {
      additionalLines.add("<li>" + item);
    }
    additionalLines.add("</ul>");
    return self();
  }

  public void printDoc(Consumer<String> println) {
    assert additionalLines.isEmpty() || title != null;
    if (title == null) {
      return;
    }
    if (additionalLines.isEmpty()) {
      println.accept("/** " + title + " */");
      return;
    }
    println.accept("/**");
    println.accept(" * " + title);
    for (String line : additionalLines) {
      if (line.startsWith("<p>")) {
        println.accept(" *");
      }
      println.accept(" * " + line);
    }
    println.accept(" */");
  }
}
