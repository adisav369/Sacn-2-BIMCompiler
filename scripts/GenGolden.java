// ⚠ DO NOT REMOVE — emits golden conformance vectors from REAL java.math.BigDecimal.
// These pin site/bigdecimal.js to Java semantics. Regenerate if the battery changes:
//   javac -d /tmp scripts/GenGolden.java && java -cp /tmp GenGolden > scripts/golden_bigdecimal.jsonl
import java.math.BigDecimal;
import java.math.RoundingMode;
public class GenGolden {
  static StringBuilder sb = new StringBuilder();
  static void bin(String op,String a,String b,String r){ sb.append("{\"op\":\""+op+"\",\"a\":\""+a+"\",\"b\":\""+b+"\",\"r\":\""+r+"\"}\n"); }
  static void div(String a,String b,int s,String rm,String r){ sb.append("{\"op\":\"divide\",\"a\":\""+a+"\",\"b\":\""+b+"\",\"scale\":"+s+",\"rm\":\""+rm+"\",\"r\":\""+r+"\"}\n"); }
  static void ss(String a,int s,String rm,String r){ sb.append("{\"op\":\"setScale\",\"a\":\""+a+"\",\"scale\":"+s+",\"rm\":\""+rm+"\",\"r\":\""+r+"\"}\n"); }
  static void cmp(String a,String b,int r){ sb.append("{\"op\":\"compareTo\",\"a\":\""+a+"\",\"b\":\""+b+"\",\"r\":"+r+"}\n"); }
  public static void main(String[] x){
    String[][] pairs={{"0.1","0.2"},{"12.34","0.1"},{"1.10","1.10"},{"100000","0"},{"-2.5","2.5"},{"123.456","0.544"},{"999.99","0.01"}};
    for(String[] p:pairs){ BigDecimal a=new BigDecimal(p[0]),b=new BigDecimal(p[1]);
      bin("add",p[0],p[1],a.add(b).toPlainString()); bin("subtract",p[0],p[1],a.subtract(b).toPlainString()); bin("multiply",p[0],p[1],a.multiply(b).toPlainString()); }
    RoundingMode[] m={RoundingMode.HALF_UP,RoundingMode.HALF_DOWN,RoundingMode.HALF_EVEN,RoundingMode.UP,RoundingMode.DOWN,RoundingMode.CEILING,RoundingMode.FLOOR};
    String[] mn={"HALF_UP","HALF_DOWN","HALF_EVEN","UP","DOWN","CEILING","FLOOR"};
    String[][] divs={{"100000","480"},{"1","3"},{"10","3"},{"-10","3"},{"2","7"},{"100","8"},{"1","8"},{"-1","8"}};
    for(String[] d:divs){ BigDecimal a=new BigDecimal(d[0]),b=new BigDecimal(d[1]);
      for(int i=0;i<m.length;i++) for(int sc:new int[]{0,2,4}){ try{ div(d[0],d[1],sc,mn[i],a.divide(b,sc,m[i]).toPlainString()); }catch(Exception e){} } }
    String[] ssv={"2.5","-2.5","2.675","0.125","0.135","999.995","-0.005","123.456","0.5","1.5","2.05","2.15"};
    for(String v:ssv){ BigDecimal a=new BigDecimal(v);
      for(int i=0;i<m.length;i++) for(int sc:new int[]{0,1,2}){ try{ ss(v,sc,mn[i],a.setScale(sc,m[i]).toPlainString()); }catch(Exception e){} } }
    String[][] cmps={{"2.0","2.00"},{"2.0","2.01"},{"-1","-1.0"},{"0.1","0.10"},{"100","99.99"}};
    for(String[] c:cmps) cmp(c[0],c[1],new BigDecimal(c[0]).compareTo(new BigDecimal(c[1])));
    System.out.print(sb);
  }
}
