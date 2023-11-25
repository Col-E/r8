// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.utils;

import com.android.tools.r8.examples.sync.Sync.Consumer;
import com.google.common.html.HtmlEscapers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class DocPrinterBase<T> {

  private String title = null;
  private String returnDesc = null;
  private String deprecatedDesc = null;
  private final List<String> additionalLines = new ArrayList<>();

  public abstract T self();

  private boolean isEmptyOrJustTitle() {
    return returnDesc == null && deprecatedDesc == null && additionalLines.isEmpty();
  }

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

  public T setDocReturn(String desc) {
    returnDesc = desc;
    return self();
  }

  public boolean isDeprecated() {
    return deprecatedDesc != null;
  }

  public T setDeprecated(String desc) {
    deprecatedDesc = desc;
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
    for (String line : lines) {
      additionalLines.add(HtmlEscapers.htmlEscaper().escape(line).replace("@", "&#64;"));
    }
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
    if (title == null) {
      assert isEmptyOrJustTitle();
      return;
    }
    if (isEmptyOrJustTitle()) {
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
    if (returnDesc != null) {
      println.accept(" * @return " + returnDesc);
    }
    if (deprecatedDesc != null) {
      println.accept(" * @deprecated " + deprecatedDesc);
    }
    println.accept(" */");
  }
}
