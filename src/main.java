
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import org.antlr.v4.misc.OrderedHashMap;
import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
 public class main {
 static int contador = -1;

public static String createTree(String word){

    if(word.length()>1 && word.charAt(0)=='('){
        contador++;
        
        System.out.println("===ROOT "+contador+"======");
        createTree(word.substring(1, word.length()));
    }else if(word.length()>1 && word.charAt(word.length()-1)==')'){
        int cont=0;
        while(word.length()>1 && word.charAt(word.length()-1)==')'){
            cont++;
            word = word.substring(0, word.length()-1);
        }
        System.out.println(word);
        while(cont>0){
            System.out.println("===FIN "+contador+"======");
            contador--;
            cont--;
            
        }
    }else
        System.out.println(word);
    return word;
}
     public static void main(String[] args) throws Exception {
  String inputFile = "src/t.expr";
                   ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName("JavaScript");
            System.out.println(engine.eval("'2000-12-12' !=null"));
        InputStream is = System.in;
        is = new FileInputStream(inputFile);

        ANTLRInputStream input = new ANTLRInputStream(is);
        SQLLexer lexer = new SQLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SQLParser parser = new SQLParser(tokens);
        parser.removeErrorListeners(); // remove ConsoleErrorListener
        parser.addErrorListener(new Interfaz.UnderlineListener());
        ParseTree tree = parser.queries(); // parse; start at queries
       System.out.println(tree.toStringTree(parser)); // print tree as text
                String test = tree.toStringTree(parser);
        
        String[] result = test.split("\\ ");
//        System.out.println(tree.toStringTree(parser)); // print tree as text
//          ParseTree tree = parser.program(); // parse; start at prog
        


        Visitante eval = new Visitante();
        eval.visit(tree);
        System.out.println(eval.getAll());
        
 }
 }
