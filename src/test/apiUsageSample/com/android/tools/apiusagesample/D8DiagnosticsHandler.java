// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.apiusagesample;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Location;
import com.android.tools.r8.TextRangeLocation;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import java.nio.file.Files;
import java.nio.file.Path;

class D8DiagnosticsHandler implements DiagnosticsHandler {

  public D8DiagnosticsHandler() {
  }

  public static Origin getOrigin(Path root, Path entry) {
    if (Files.isRegularFile(root)) {
      return new ArchiveEntryOrigin(entry.toString(), new PathOrigin(root));
    } else {
      return new PathOrigin(root.resolve(entry.toString()));
    }
  }

  @Override
  public void error(Diagnostic error) {
    convertToMessage(error);
  }

  @Override
  public void warning(Diagnostic warning) {
    convertToMessage(warning);
  }

  @Override
  public void info(Diagnostic info) {
    convertToMessage(info);
  }

  protected void convertToMessage(Diagnostic diagnostic) {
    String textMessage = diagnostic.getDiagnosticMessage();

    Location location = diagnostic.getLocation();
    String position;
    if (location instanceof TextRangeLocation && location.getOrigin() instanceof PathOrigin) {
      TextRangeLocation textRange = (TextRangeLocation) location;
      position = ((PathOrigin) location.getOrigin()).getPath().toFile() + ": "
          + textRange.getStart().getLine() + "," + textRange.getStart().getColumn()
          + " - " + textRange.getEnd().getLine() + "," + textRange.getEnd().getColumn();
    } else if (location.getOrigin() instanceof PathOrigin) {
      position = ((PathOrigin) location.getOrigin()).getPath().toFile().toString();
    } else {
      position = "UNKNOWN";
      if (location != Location.UNKNOWN) {
        textMessage = location.getDescription() + ": " + textMessage;
      }
    }

    System.out.println(position + ": " + textMessage);
  }
}
