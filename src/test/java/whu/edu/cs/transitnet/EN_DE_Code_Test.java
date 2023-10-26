package whu.edu.cs.transitnet;
import edu.whu.hyk.encoding.Decoder;
import edu.whu.hyk.encoding.Encoder;
import org.junit.Test;
import java.io.IOException;
import java.text.ParseException;

public class EN_DE_Code_Test {
    @Test
    public void ExpTest() throws InterruptedException, IOException, ParseException {
        int zo=Encode(1,1,0,1);
        Decode(zo,0);
    }
    public int Encode(int i,int j,int k,int l){
        System.out.println("[Encode]=======================================");
        System.out.println("use i j k l: "+i+" "+j+" "+k+" "+l);
        int res=Encoder.combine3(i,j,k,l);
        System.out.println("Encode zorder: "+res);
        return  res;
    }
    public void Decode(int zo, int level){
        System.out.println("[Decode]=======================================");
        System.out.println("use zorder level: "+zo+" "+level);
        int[] res= Decoder.decodeZ3(zo,level);
        System.out.println("Decode res: "+res[0]+" "+res[1]+" "+res[2]+" "+res[3]+" "+res[4]+" "+res[5]);
    }
}
