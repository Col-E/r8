package nestHostExample;

public class NestHostInlining {

  private String field = "inlining";

  public static class InnerWithPrivAccess {
    public String access(NestHostInlining host) {
      return host.field;
    }
  }

  public static class InnerNoPrivAccess {
    public String print() {
      return "InnerNoPrivAccess";
    }
  }

  public abstract static class EmptyNoPrivAccess {}

  public abstract static class EmptyWithPrivAccess {
    public String access(NestHostInlining host) {
      return host.field;
    }
  }

  public static void main(String[] args) {
    System.out.println(new InnerWithPrivAccess().access(new NestHostInlining()));
    System.out.println(new InnerNoPrivAccess().print());
  }
}
