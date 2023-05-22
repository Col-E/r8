// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public abstract class MarkerMatcher extends TypeSafeMatcher<Marker> {

  public static void assertMarkersMatch(Iterable<Marker> markers, Matcher<Marker> matcher) {
    assertMarkersMatch(markers, ImmutableList.of(matcher));
  }

  public static void assertMarkersMatch(
      Iterable<Marker> markers, Collection<Matcher<Marker>> matchers) {
    // Match is unordered, but we make no attempts to find the maximum match.
    int markerCount = 0;
    Set<Marker> matchedMarkers = new HashSet<>();
    Set<Matcher<Marker>> matchedMatchers = new HashSet<>();
    for (Marker marker : markers) {
      markerCount++;
      for (Matcher<Marker> matcher : matchers) {
        if (matchedMatchers.contains(matcher)) {
          continue;
        }
        if (matcher.matches(marker)) {
          matchedMarkers.add(marker);
          matchedMatchers.add(matcher);
          break;
        }
      }
    }
    StringBuilder builder = new StringBuilder();
    boolean failedMatching = false;
    if (matchedMarkers.size() < markerCount) {
      failedMatching = true;
      builder.append("\nUnmatched markers:");
      for (Marker marker : markers) {
        if (!matchedMarkers.contains(marker)) {
          builder.append("\n  - ").append(marker);
        }
      }
    }
    if (matchedMatchers.size() < matchers.size()) {
      failedMatching = true;
      builder.append("\nUnmatched matchers:");
      for (Matcher<Marker> matcher : matchers) {
        if (!matchedMatchers.contains(matcher)) {
          builder.append("\n  - ").append(matcher);
        }
      }
    }
    if (failedMatching) {
      builder.append("\nAll markers:");
      for (Marker marker : markers) {
        builder.append("\n  - ").append(marker);
      }
      builder.append("\nAll matchers:");
      for (Matcher<Marker> matcher : matchers) {
        builder.append("\n  - ").append(matcher);
      }
      fail(builder.toString());
    }
    // Double check consistency.
    assertEquals(matchers.size(), markerCount);
    assertEquals(markerCount, matchedMarkers.size());
    assertEquals(markerCount, matchedMatchers.size());
  }

  public static Matcher<Marker> markerTool(Tool tool) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getTool() == tool;
      }

      @Override
      protected void explain(Description description) {
        description.appendText("tool ").appendText(tool.name());
      }
    };
  }

  public static Matcher<Marker> markerAndroidPlatformBuild() {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.isAndroidPlatformBuild();
      }

      @Override
      protected void explain(Description description) {
        description.appendText("platform");
      }
    };
  }

  public static Matcher<Marker> markerCompilationMode(CompilationMode compilationMode) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getCompilationMode().equals(StringUtils.toLowerCase(compilationMode.name()));
      }

      @Override
      protected void explain(Description description) {
        description.appendText(Marker.COMPILATION_MODE + " ").appendText(compilationMode.name());
      }
    };
  }

  public static Matcher<Marker> markerBackend(Backend backend) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getBackend().equals(StringUtils.toLowerCase(backend.name()));
      }

      @Override
      protected void explain(Description description) {
        description
            .appendText(Marker.BACKEND + " ")
            .appendText(StringUtils.toLowerCase(backend.name()));
      }
    };
  }

  public static Matcher<Marker> markerIsDesugared() {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.isDesugared();
      }

      @Override
      protected void explain(Description description) {
        description.appendText("desugared ");
      }
    };
  }

  public static Matcher<Marker> markerMinApi(AndroidApiLevel level) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getMinApi() == level.getLevel();
      }

      @Override
      protected void explain(Description description) {
        description.appendText(Marker.MIN_API + " ").appendText(level.toString());
      }
    };
  }

  public static Matcher<Marker> markerHasMinApi() {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.hasMinApi();
      }

      @Override
      protected void explain(Description description) {
        description.appendText(Marker.MIN_API + " found");
      }
    };
  }

  public static Matcher<Marker> markerHasChecksums(boolean value) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getHasChecksums() == value;
      }

      @Override
      protected void explain(Description description) {
        description.appendText(Marker.HAS_CHECKSUMS + " ").appendText(Boolean.toString(value));
      }
    };
  }

  public static Matcher<Marker> markerPgMapId(Matcher<String> predicate) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return predicate.matches(marker.getPgMapId());
      }

      @Override
      protected void explain(Description description) {
        description.appendText("with pg_map_id matching ");
        predicate.describeTo(description);
      }
    };
  }

  public static Matcher<Marker> markerR8Mode(String r8Mode) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.getR8Mode().equals(r8Mode);
      }

      @Override
      protected void explain(Description description) {
        description.appendText(Marker.R8_MODE + " ").appendText(r8Mode);
      }
    };
  }

  public static Matcher<Marker> markerDesugaredLibraryIdentifier(
      String desugaredLibraryIdentifier) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        if (marker.getDesugaredLibraryIdentifiers().length != 1) {
          return false;
        }
        return marker.getDesugaredLibraryIdentifiers()[0].equals(desugaredLibraryIdentifier);
      }

      @Override
      protected void explain(Description description) {
        description
            .appendText(Marker.DESUGARED_LIBRARY_IDENTIFIERS + " ")
            .appendText(desugaredLibraryIdentifier);
      }
    };
  }

  public static Matcher<Marker> markerHasDesugaredLibraryIdentifier() {
    return markerHasDesugaredLibraryIdentifier(true);
  }

  public static Matcher<Marker> markerHasDesugaredLibraryIdentifier(boolean value) {
    return new MarkerMatcher() {
      @Override
      protected boolean eval(Marker marker) {
        return marker.hasDesugaredLibraryIdentifiers() == value;
      }

      @Override
      protected void explain(Description description) {
        description.appendText(
            Marker.DESUGARED_LIBRARY_IDENTIFIERS + (value ? " found" : " not found"));
      }
    };
  }

  @Override
  protected boolean matchesSafely(Marker marker) {
    return eval(marker);
  }

  @Override
  public void describeTo(Description description) {
    explain(description.appendText("a marker "));
  }

  protected abstract boolean eval(Marker diagnostic);

  protected abstract void explain(Description description);
}
