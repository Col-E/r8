package backport;

public final class StringBackportJava11Main {
  public static void main(String[] args) {
    testRepeat();
    testIsBlank();
    testStrip();
    testStripLeading();
    testStripTrailing();
  }

  private static void testRepeat() {
    try {
      throw new AssertionError("hey".repeat(-1));
    } catch (IllegalArgumentException e) {
      assertEquals("count is negative: -1", e.getMessage());
    }

    assertEquals("", "".repeat(0));
    assertEquals("", "".repeat(1));
    assertEquals("", "".repeat(2));

    assertEquals("", "hey".repeat(0));
    assertEquals("hey", "hey".repeat(1));
    assertEquals("heyhey", "hey".repeat(2));
    assertEquals("heyheyhey", "hey".repeat(3));
    assertEquals("heyheyheyhey", "hey".repeat(4));
  }

  /** Per {@link Character#isWhitespace(int)} */
  private static final String WHITESPACE = ""
      // Unicode "Zs" category:
      + "\u0020"
      + "\u1680"
      //+ "\u00A0" Exception per Javadoc
      + "\u1680"
      + "\u2000"
      + "\u2001"
      + "\u2002"
      + "\u2003"
      + "\u2004"
      + "\u2005"
      + "\u2006"
      //+ "\u2007" Exception per Javadoc
      + "\u2008"
      + "\u2009"
      + "\u200A"
      //+ "\u200F" Exception per Javadoc
      //+ "\u205F" Not honored on Android 4.0.4
      + "\u3000"
      // Unicode "Zl" category:
      + "\u2028"
      // Unicode "Zp" category:
      + "\u2029"
      // Others:
      + "\t"
      + "\n"
      + "\u000B"
      + "\f"
      + "\r"
      + "\u001C"
      + "\u001D"
      + "\u001E"
      + "\u001F"
      ;

  public static void testIsBlank() {
    assertEquals(true, "".isBlank());
    assertEquals(true, WHITESPACE.isBlank());

    // Android <=4.0.4 does not recognize this as whitespace. Just ensure local consistency.
    assertEquals(Character.isWhitespace(0x205F), "\u205F".isBlank());

    assertEquals(false, "a".isBlank());
    assertEquals(false, "å".isBlank());
    assertEquals(false, "a\u030A".isBlank());
    assertEquals(false, "\uD83D\uDE00".isBlank());
    assertEquals(false, (WHITESPACE + "a").isBlank());
    assertEquals(false, ("a" + WHITESPACE).isBlank());
  }

  public static void testStrip() {
    assertEquals("", "".strip());
    assertEquals("", WHITESPACE.strip());
    assertEquals("a", "a".strip());
    assertEquals("a", (WHITESPACE + "a").strip());
    assertEquals("a", ("a" + WHITESPACE).strip());
    assertEquals("a", (WHITESPACE + "a" + WHITESPACE).strip());
    assertEquals("a" + WHITESPACE + "a", ("a" + WHITESPACE + "a").strip());
    assertEquals("a" + WHITESPACE + "a", (WHITESPACE + "a" + WHITESPACE + "a").strip());
    assertEquals("a" + WHITESPACE + "a", ("a" + WHITESPACE + "a" + WHITESPACE).strip());
    assertEquals("a" + WHITESPACE + "a",
        (WHITESPACE + "a" + WHITESPACE + "a" + WHITESPACE).strip());
  }

  public static void testStripLeading() {
    assertEquals("", "".stripLeading());
    assertEquals("", WHITESPACE.stripLeading());
    assertEquals("a", "a".stripLeading());
    assertEquals("a", (WHITESPACE + "a").stripLeading());
    assertEquals("a" + WHITESPACE, ("a" + WHITESPACE).stripLeading());
    assertEquals("a" + WHITESPACE, (WHITESPACE + "a" + WHITESPACE).stripLeading());
    assertEquals("a" + WHITESPACE + "a", ("a" + WHITESPACE + "a").stripLeading());
    assertEquals("a" + WHITESPACE + "a", (WHITESPACE + "a" + WHITESPACE + "a").stripLeading());
    assertEquals("a" + WHITESPACE + "a" + WHITESPACE,
        ("a" + WHITESPACE + "a" + WHITESPACE).stripLeading());
    assertEquals("a" + WHITESPACE + "a" + WHITESPACE,
        (WHITESPACE + "a" + WHITESPACE + "a" + WHITESPACE).stripLeading());
  }

  public static void testStripTrailing() {
    assertEquals("", "".stripTrailing());
    assertEquals("", WHITESPACE.stripTrailing());
    assertEquals("a", "a".stripTrailing());
    assertEquals(WHITESPACE + "a", (WHITESPACE + "a").stripTrailing());
    assertEquals("a", ("a" + WHITESPACE).stripTrailing());
    assertEquals(WHITESPACE + "a", (WHITESPACE + "a" + WHITESPACE).stripTrailing());
    assertEquals("a" + WHITESPACE + "a", ("a" + WHITESPACE + "a").stripTrailing());
    assertEquals(WHITESPACE + "a" + WHITESPACE + "a",
        (WHITESPACE + "a" + WHITESPACE + "a").stripTrailing());
    assertEquals("a" + WHITESPACE + "a", ("a" + WHITESPACE + "a" + WHITESPACE).stripTrailing());
    assertEquals(WHITESPACE + "a" + WHITESPACE + "a",
        (WHITESPACE + "a" + WHITESPACE + "a" + WHITESPACE).stripTrailing());
  }

  private static void assertEquals(Object expected, Object actual) {
    if (expected != actual && (expected == null || !expected.equals(actual))) {
      throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
    }
  }
}
