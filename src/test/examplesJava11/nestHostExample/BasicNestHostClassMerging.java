package nestHostExample;

public class BasicNestHostClassMerging {

  private String field = "Outer";

  public static class MiddleOuter extends BasicNestHostClassMerging {

    private String field = "Middle";

    public static void main(String[] args) {
      System.out.println(new InnerMost().getFields());
    }
  }

  public static class MiddleInner extends MiddleOuter {
    private String field = "Inner";
  }

  public static class InnerMost extends MiddleInner {

    public String getFields() {
      return ((BasicNestHostClassMerging) this).field
          + ((MiddleOuter) this).field
          + ((MiddleInner) this).field;
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerMost().getFields());
  }
}
