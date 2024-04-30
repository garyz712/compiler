package crux.ast.types;

/**
 * The variable base is the type of the array element. This could be int or bool. The extent
 * variable is number of elements in the array.
 *
 */
public final class ArrayType extends Type implements java.io.Serializable {
  static final long serialVersionUID = 12022L;
  private final Type base;
  private final long extent;

  public ArrayType(long extent, Type base) {
    this.extent = extent;
    this.base = base;
  }

  public Type getBase() {
    return base;
  }

  public long getExtent() {
    return extent;
  }

  @Override
  public boolean equivalent(Type that){
    if (that.getClass() == ArrayType.class) {
      ArrayType at = (ArrayType) that;
      return this.getBase().equivalent(at.getBase()) && (this.getExtent() == at.getExtent());
    }else{
      throw new AssertionError("That is not an array type, can't compare");
    }
  }

  @Override
  public Type index(Type that) {
    if (that.getClass() == IntType.class){
      return getBase();
    } else {
      return super.index(that);
    }
  }
  @Override
  public String toString() {
    return String.format("array[%d,%s]", extent, base);
  }
}
