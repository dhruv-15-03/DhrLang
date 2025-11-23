package dhrlang.ir;

/** Push a try handler that jumps to the given catch label on THROW, with a simple catch type filter. */
public class IrTryPush implements IrInstruction {
    public final String catchLabel;
    public final String catchType; // e.g. "any", "Error", "DhrException", or specific Exception names
    public IrTryPush(String catchLabel, String catchType){ this.catchLabel = catchLabel; this.catchType = catchType; }
    @Override public String toString(){ return "TRY_PUSH("+catchType+") -> "+catchLabel; }
}
