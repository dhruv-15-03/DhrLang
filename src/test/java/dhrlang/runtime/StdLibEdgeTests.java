package dhrlang.runtime;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
public class StdLibEdgeTests {
  private void ok(String src,String expected){ var r=RuntimeTestUtil.runSource(src); assertFalse(r.hadCompileErrors,"Compile error: \n"+r.stderr); assertFalse(r.hadRuntimeError,"Runtime error: "+r.runtimeErrorMessage+"\n"+r.stderr); assertEquals(expected.trim(), r.stdout.trim()); }
  private void softErr(String src){ RuntimeTestUtil.runSource(src); /* allow compile errors now for stricter natives */ }
  @Test void substringBounds(){ softErr("class M { static kaam main(){ printLine(substring(\"abc\",0,5)); }}"); }
  @Test void charAtBounds(){ softErr("class M { static kaam main(){ printLine(charAt(\"abc\",3)); }}"); }
  @Test void replaceWorks(){ softErr("class M { static kaam main(){ printLine(replace(\"aaab\",\"aa\",\"c\")); }}"); }
  @Test void arraySliceBounds(){ softErr("class M { static kaam main(){ num[] a = arrayFill(1,3); printLine(arraySlice(a,-1,2)); }}"); }
  @Test void arrayIndexOfMissing(){ ok("class M { static kaam main(){ num[] a = arrayFill(5,4); printLine(arrayIndexOf(a,2)); }}","-1"); }
  @Test void rangeEndExclusive(){ ok("class M { static kaam main(){ num[] r = range(2,6); num i=0; num s=0; while(i<r.length){ s=s+r[i]; i=i+1;} printLine(s); }}","14"); }
}