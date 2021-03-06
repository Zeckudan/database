/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package dbman;
import java.util.Date;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvMapReader;
import org.supercsv.io.CsvMapWriter;
import org.supercsv.io.ICsvMapReader;
import org.supercsv.io.ICsvMapWriter;
import org.supercsv.prefs.CsvPreference;

/**
 * DMLManager
 * @author Jorge Lainfiesta 11142
 * @since Apr 29, 2014
 * @version 1
 */
public class DMLManager {
    private DBObject db;
    private Map<String,MetaTable> currTables = new HashMap<>();
    private LinkedHashMap<String,String> table_aliases = new LinkedHashMap<>();
    private HashMap<String,LinkedList<Integer>> hash_tables;
    /**
     *   
     * @param db 
     */
    public DMLManager(DBObject db){
        hash_tables = new HashMap();
        this.db = db;
        
        //Create empty files with headers if not already there
        for(MetaTable table : db.getTables().values()){
            File f = new File(table.physicalLocation());
            if(!f.exists()) {
                ICsvMapWriter mapWriter = null;
                try {
                        //Prepare for writing
                        mapWriter = new CsvMapWriter(new FileWriter(table.physicalLocation()),
                                CsvPreference.STANDARD_PREFERENCE);
                        String [] header = table.getOrderedColumns();
                    
//                        String [] header = table.getColumns().keySet()..toArray(new String[table.getColumns().keySet().size()]);

                        CellProcessor[] processors = new CellProcessor[header.length];
                        
                        // write the header
                        mapWriter.writeHeader(header);
                        mapWriter.close();

                } catch (IOException ex) {
                    Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
    }
    /**
     * Assumes the tables passed are already validated. If the table
     * does not exists, it will just ignore it without warning.
     * @param tables we'll work with in this transaction
     */
    public void workWithTables(String... tables){
        for(String table : tables){
            if(db.getTables().containsKey(table)){
                currTables.put(table, db.getTables().get(table));
            }
        }
    }
    
    private void doneWithTables(int hashes_affected){
        if(hashes_affected> 0){
            //refreshHashes(this.getCurrentTable().getName());
        }
        doneWithTables();
    }
    
    private void doneWithTables(){
        currTables.clear();
        table_aliases.clear();
    }
    
    /**
     * 
     * @param table
     * @return True if the table exists on the DB, False if not.
     */
    public boolean existsTable(String table){
        return this.db.getTables().containsKey(table);
    }
    /**
     * Checks for a column existence on the current tables. workWithTables must
     * have been previously called. Assumes table is validated.
     * @param table name of the table for the column, or null if check on any current table
     * @param col name of the column
     * @return -1 if ambiguous column, 0 if does not exists, 1 if it does.
     */
    public int existsColumn(String table, String col){
        int result = 0;
        
        if(table != null){
            if(table_aliases.containsKey(table)){
                table = table_aliases.get(table);
            }
            MetaTable sel_table = this.currTables.get(table);
            if(sel_table != null && sel_table.getColumns().containsKey(col)){
                result = 1;
            }
        }else {
            
            for(Map.Entry<String, MetaTable> currTs : currTables.entrySet()){
                if(currTs.getValue().getColumns().containsKey(col)){
                    result += 1;
                }
            }
            result = (result > 1) ? -1 : result;
        }
        return result;
    }
    /**
     * Returns column type on the current tables. workWithTables must
     * have been previously called. Assumes table is validated.
     * @param table name of the table for the column, or null if check on any current table
     * @param col name of the column
     * @return the type as string, null if does not exists, and if col was ambiguous the type of the last found
     */
    public String getColumnType(String table, String col){
        JSONObject result = this.getColumn(table, col);
        return (result == null) ? null : (String) result.get("type");
    }
    
    public JSONObject getColumn(String table, String col){
        JSONObject result = null;
        if(table != null){
            if(table_aliases.containsKey(table)){
                table = table_aliases.get(table);
            }
            result = this.currTables.get(table).getColumns().get(col);
        }else {
            for(Map.Entry<String, MetaTable> currTs : currTables.entrySet()){
                if(currTs.getValue().getColumns().containsKey(col)){
                    result = currTs.getValue().getColumns().get(col);
                }
            }
        }
        return result;
    }
    /**
     * Assumes table is validated.
     * @param talias
     * @param table 
     * @return True if valid, false if talias already existed as an alias or table name in the DB
     */
    public boolean registerTableAlias(String talias, String table){
        if(table_aliases.containsKey(talias) || db.getTables().containsKey(talias)){
            return false;
        }else {
            table_aliases.put(talias, table);
            return true;
        }
    }
    
    private MetaTable getCurrentTable(){
        //Get current table
        MetaTable currTable = null;
        for(MetaTable table : this.currTables.values()){
            currTable = table;
        }
        return currTable;
    }
    
    public String getValType(String val){
//        System.out.println("getValType: "+val);
        if(val == null || val.equals("NULL") || val.equals("'NULL'") || val.equals("")){
            return "NULL";
        }else if(val.matches("[0-9]+")){
            return "INT";
        } else if(val.matches("[0-9]*\\.[0-9]*") && val.length()>1){
            return "FLOAT";
        } else if(val.matches("^'(19|20)\\d\\d[\\-\\/.](0[1-9]|1[012])[\\-\\/.](0[1-9]|[12][0-9]|3[01])'$")) {
            return "DATE";
        } else if(val.startsWith("'") && val.endsWith("'") || val.startsWith("\"") && val.endsWith("\"")) {
            return "CHAR";
        } else {
            return "UNDEFINED";
        }
    }
    
    /**
     * Casts a value
     * @param coltype type to which cast value to
     * @param value value to cast
     * @return 
     */
    public String castVal(String coltype, String value){
        String valtype = this.getValType(value);
        if(coltype.equals("CHAR"))
            if(valtype.equals("INT")||valtype.equals("FLOAT"))
                return "'"+value+"'";
            else
                return value;
        if(valtype.equals("NULL"))
            return "";
        if(coltype.equals(valtype)){
            return value;
        }else if(coltype.equals("DATE") && valtype.equals("CHAR") && value.matches("^'(19|20)\\d\\d[\\-\\/.](0[1-9]|1[012])[\\-\\/.](0[1-9]|[12][0-9]|3[01])'$")){
            return value;
        }else if(coltype.equals("INT") && valtype.equals("FLOAT")){
            return value.split("\\.")[0];
        } else if(coltype.equals("FLOAT") && valtype.equals("INT")){
            return value+".00";
        }else if(valtype.equals("CHAR")) {
            value = value.replace("'", "");
            valtype = getValType(value);
//           System.out.println("Value type "+valtype+" and "+coltype);
            if(coltype.equals(valtype))
                return value;
            if(coltype.equals("INT") && valtype.equals("FLOAT")){
                return value.split("\\.")[0];
            } else if(coltype.equals("FLOAT") && valtype.equals("INT")){
                return value+".00";
        }
            
        }
            return null;
    }
    
    private void refreshPKHashes(){
        refreshPKHashes(getCurrentTable());
    }
    private void refreshPKHashes(MetaTable table){
           LinkedList<Integer> hashes = new LinkedList<>();

           if(!hash_tables.containsKey(table.getName())){
               hash_tables.put(table.getName(), hashes);
           }
           try {
               ICsvMapReader mapReader;
               mapReader = new CsvMapReader(new FileReader(table.physicalLocation()), CsvPreference.STANDARD_PREFERENCE);

               // the header columns are used as the keys to the Map
               final String[] header = mapReader.getHeader(true);
               //columnCheck.toArray(new String[columnCheck.size()]);
               final CellProcessor[] processors = new CellProcessor[header.length];

   // System.out.println(String.format("Header size: %s Processor size: %s", header.length, processors.length));

               Map<String, Object> rowMap;
               //Mientras haya que leer
               while( (rowMap = mapReader.read(header, processors)) != null ) {

                       String pk = "";
                       for(String pk_col : table.getPK()){
   // System.out.println(String.format("pk_col: %s rop: %s", pk_col, rowMap.get(pk_col)));
                           pk += rowMap.get(pk_col);
                       }
   // System.out.println(String.format("lineNo=%s, rowNo=%s, customerMap=%s, pk=%s, pkhash=%s", mapReader.getLineNumber(),
   // mapReader.getRowNumber(), rowMap, pk, pk.hashCode()));
                       hashes.add(pk.hashCode());
               }
               mapReader.close();
           }catch (IOException ex) {
               Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
           }
    }
    
    private boolean isUniquePK(List<String> valcheck){
        LinkedList<Integer> hashes;
        MetaTable currTable = getCurrentTable();
        /*
        Aqui miramos si ya habiamos leido el archivo anteriormente. 
        La idea es reducir las veces en que tenemos que leer el archivo solo para encontrar llaves
        Tenerlas guardadas y actualizarla cuando se de un cambio es mas eficiente en cuestiones de tiempo
        de hasta 50~60 segundos.
        */
        if(!hash_tables.containsKey(currTable.getName())){
            //Si no existe que cree su lista
            refreshPKHashes();
        }
        
        hashes = hash_tables.get(currTable.getName());
        
        String newkey = "";
        for(String val : valcheck){
            newkey += val;
        }
        
        return !hashes.contains(newkey.hashCode());
    }
    
    private boolean existsInForeginPK(String table, List<String> valcheck){
        LinkedList<Integer> hashes;
        MetaTable ref_table = db.getTables().get(table);
        if(!hash_tables.containsKey(table)){
            refreshPKHashes(ref_table);
        }
        hashes = hash_tables.get(ref_table.getName());
        
        String newkey = "";
        for(String val : valcheck){
            newkey += val;
        }
        
        return hashes.contains(newkey.hashCode());
    }
    
    /**
     * Assumes columns are validated and values are type validated.
     * @param columns
     * @param values
     * @return 
     * @throws ConstrainException 
     */
    public int insert(List<String> values, List<String> columns) throws ConstrainException{
        int insertedRows = 0;
        if(this.currTables.size() != 1){
            throw new ConstrainException("Invalid working table: "+this.currTables.keySet());
        }else {
            if(columns !=null && columns.size() != values.size()){
                throw new ConstrainException("Mismatch in number of columns and values: ("+columns+")  "+values);
            }else {
                MetaTable currTable = this.getCurrentTable();
                //Locate physical file
                String fileURL = currTable.physicalLocation();
//                System.out.println(String.format("Tname: %s, cols: %s", currTable.getName(), currTable.getColumns()));
                String [] header = currTable.getOrderedColumns();
                
                //If it's an implicit column list, we assign them from the originals
                if(columns == null){
                    columns = new LinkedList<>();
                    for(int i = 0; i<values.size(); i++){
                        columns.add(header[i]);
                    }
                }
                
                //Prepare to store in CSV
                Map<String, Object> newRow = new LinkedHashMap<>();
                
                for(int i = 0; i<columns.size(); i++){
                    JSONObject column = this.getColumn(currTable.getName(), columns.get(i));
                    String coltype = column.get("type").toString();
                    String valinsert = this.castVal(coltype, values.get(i));
                    if(valinsert == null){
                        throw new ConstrainException(String.format("Incompatible types: column '%s' is type %s and '%s' is %s", columns.get(i), 
                                this.getColumnType(currTable.getName(), columns.get(i)), values.get(i), this.getValType(values.get(i))));
                    }else {
                        if(coltype.equals("CHAR")){
                            if(this.getValType(valinsert).equals("CHAR") && Integer.parseInt(column.get("size").toString()) < valinsert.length()-2){
                                throw new ConstrainException(String.format("Invalid CHAR size %s for columns '%s'", valinsert.length()-2, column.get("name").toString()));
                            }
                        }else
                            newRow.put(columns.get(i),valinsert);
                        
                    }
                }
                Map<String, JSONObject> cols = currTable.getColumns();
                for(String col : cols.keySet()){
                    //Check not null
                    if(newRow.get(col) == null && cols.get(col).get("notNull").equals("true")){
                        throw new ConstrainException(String.format("Insert violates NOT NULL constraint on column '%s' of table '%s'.", 
                                col,  currTable.getName()));
                    }
                    //Check NOT NULL
                    if(newRow.get(col) == null && currTable.getPK().contains(col)){
                        throw new ConstrainException(String.format("Insert violates PRIMARY KEY constraint on column '%s', cannot be NULL", col));
                    }
                    //Check CHECK
                    List<JSONObject> chks = db.getCH(currTable.getName(), col);
                    if(chks.size() > 0){
                        //Ponemos la data para el evalwhere
                        Map<String, Map<String, Object>> data = new HashMap<>();
                        data.put(currTable.getName(), newRow);
                        //Si no cumple con el while
                        for(JSONObject chk : chks){
                            System.out.println("chk"+chk.toJSONString()+"  "+chk.get("expression"));
                            if(!this.evalWhere((String) chk.get("expression"), data)){
                                throw new  ConstrainException(String.format("Insert violates '%s' CHECK", chk.get("name")));
                            }
                        }
                    }
                }
                
                //Check PK
                List<String> pk_vals = new LinkedList<>();
                for(String pkey : currTable.getPK()){
                    pk_vals.add((String) newRow.get(pkey));
                }
                if(!isUniquePK(pk_vals)){
                    throw new ConstrainException(String.format("Invalid values '%s' for PRIMARY KEY %s on INSERT. (PK must be unique)", pk_vals, currTable.getPK()));
                }
                //Check FK
                for(JSONObject fk : db.getFKs(currTable.getName())){
                    //Revisar si la columna de FK se usa en este insert
                    int ins_fk = 0;
                    JSONArray l_cols = (JSONArray) fk.get("columns");
                    for(Object lcol : l_cols){
                        if(columns.contains((String) lcol)){
                            ins_fk++;
                        }
                    }
                    if(ins_fk > 0){
                        JSONArray f_cols = (JSONArray) fk.get("referencedColumns");
                        List<String> fkey = new LinkedList<>();
                        for(Object fcol : l_cols){
                            fkey.add((String) newRow.get((String) fcol));
                        }
                       
                        if(!existsInForeginPK((String) fk.get("referencedTable"), fkey)){
                            throw new ConstrainException(String.format("Insert violates FOREIGN KEY '%s'", fk.get("name")));
                        }
                    }
                    
                }
                
                ICsvMapWriter mapWriter;
                try {   
                    //Prepare for writing
                    mapWriter = new CsvMapWriter(new FileWriter(fileURL, true),
                            CsvPreference.STANDARD_PREFERENCE);

                    // assign a default value for married (if null), and write numberOfKids as an empty column if null
                    CellProcessor[] processors = new CellProcessor[columns.size()];

                    // write the customer Maps
                    mapWriter.write(newRow, header);
                    mapWriter.close();
                    this.hash_tables.get(currTable.getName()).add(pk_vals.hashCode());
                    insertedRows = 1;

                } catch (IOException ex) {
                    Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        doneWithTables();
        return insertedRows;
    }
    /**
     * Deletes the rows that evaluate true for the  validation. If the is no validation it deletes every row.
     * @param validation Must be as described
     * @return in the API
     * @throws ConstrainException 
     */
    public int delete(String validation) throws ConstrainException{
        ICsvMapReader mapReader;
        ICsvMapWriter mapWriter;
        int rowsDeleted = 0;
        try {
            MetaTable currTable = getCurrentTable();
            String tempFile = currTable.physicalLocation()+".aux";
            mapReader = new CsvMapReader(new FileReader(currTable.physicalLocation()), CsvPreference.STANDARD_PREFERENCE);
            mapWriter = new CsvMapWriter(new FileWriter(tempFile), CsvPreference.STANDARD_PREFERENCE);
            
//            System.out.println(currTable.getName() + currTable.physicalLocation());
            
            // the header columns are used as the keys to the Map
            final String[] header = mapReader.getHeader(true);
            final CellProcessor[] processors =  new CellProcessor[currTable.getColumns().size()];
            mapWriter.writeHeader(header);
            //Si 
            if(validation != null){
                Map<String, Object> rowMap;
                //Mientras haya que leer
                while( (rowMap = mapReader.read(header, processors)) != null ) {
//                        System.out.println(String.format("lineNo=%s, rowNo=%s, customerMap=%s", mapReader.getLineNumber(),
//                                mapReader.getRowNumber(), rowMap));
                        //Preparamos objeto como lo espera el evalWhere
                        Map<String, Map<String, Object>> data = new HashMap<>();
                        data.put(currTable.getName(), rowMap);
                        //Si no cumple con el while
                        if(!this.evalWhere(validation, data)){
                        //Check FK
                         List<String>   columns = Arrays.asList(header);
                        for(JSONObject fk : db.getFKs(currTable.getName())){
                            //Revisar si la columna de FK se usa en este insert
                            int ins_fk = 0;
                            JSONArray l_cols = (JSONArray) fk.get("columns");
                            for(Object lcol : l_cols){
                                if(columns.contains((String) lcol)){
                                    ins_fk++;
                                }
                            }
                            if(ins_fk > 0){
                                JSONArray f_cols = (JSONArray) fk.get("referencedColumns");
                                List<String> fkey = new LinkedList<>();
                                for(Object fcol : l_cols){
                                    fkey.add((String) rowMap.get((String) fcol));
                                }

                                if(existsInForeginPK((String) fk.get("referencedTable"), fkey)){
                                    throw new ConstrainException(String.format("Delete violates FOREIGN KEY '%s'", fk.get("name")));
                                }
                            }

                        }
                            mapWriter.write(rowMap, header, processors);
                        }else
                            rowsDeleted++;
                }
            }else{
                Table table = (Table) getCurrentTable();
                rowsDeleted = (int) table.getRecords();
            }
                
            mapWriter.close();
            mapReader.close();
            //Borrar original y Cambiar archivo auxiliar por normal
            File old = new File(currTable.physicalLocation());
            old.delete();
            File newfile = new File(tempFile);
            newfile.renameTo(old);
                
        }
        catch (IOException ex) {
            Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        doneWithTables();
        return rowsDeleted;
    }
    
    public int update(List<String> values, List<String> columns, String validation) throws ConstrainException{
// if(verbose){
// System.out.println(String.format("UPDATE: values %s columns %s validation %s", values, columns, validation));
// }
        if(columns.size() != values.size()){
            throw new ConstrainException(String.format("Values passed (%s) do not corresond to the specified columns (%s).", columns.size(), values.size()));
        }
        int updatedRows = 0;
        ICsvMapReader mapReader;
        ICsvMapWriter mapWriter;
        try {
            MetaTable currTable = getCurrentTable();
            String tempFile = currTable.physicalLocation()+".aux";
            mapReader = new CsvMapReader(new FileReader(currTable.physicalLocation()), CsvPreference.STANDARD_PREFERENCE);
            mapWriter = new CsvMapWriter(new FileWriter(tempFile), CsvPreference.STANDARD_PREFERENCE);
            
// System.out.println(currTable.getName() + currTable.physicalLocation());
            
            // the header columns are used as the keys to the Map
            final String[] header = mapReader.getHeader(true);
            final CellProcessor[] processors = new CellProcessor[currTable.getColumns().size()];
            mapWriter.writeHeader(header);
            
            Map<String, Object> rowMap;
            //Mientras haya que leer
            while( (rowMap = mapReader.read(header, processors)) != null ) {
// System.out.println(String.format("lineNo=%s, rowNo=%s, customerMap=%s", mapReader.getLineNumber(),
// mapReader.getRowNumber(), rowMap));

                    //Preparamos objeto como lo espera el evalWhere
                    Map<String, Map<String, Object>> data = new HashMap<>();
                    data.put(currTable.getName(), rowMap);
                    //Si no cumple con el while
                    if(validation == null || this.evalWhere(validation, data)){
                        //Cambiamos los valores de la file para actualizar
                        for(int i = 0; i<columns.size(); i++){
                            if(this.existsColumn(currTable.getName(), columns.get(i))==1){
                                JSONObject column = this.getColumn(currTable.getName(), columns.get(i));
                                String coltype = column.get("type").toString();
                                String valinsert = this.castVal(coltype, values.get(i));
                                if(valinsert == null){
                                    throw new ConstrainException(String.format("Incompatible types: column '%s' is type %s and '%s' is %s", columns.get(i),
                                            this.getColumnType(currTable.getName(), columns.get(i)), values.get(i), this.getValType(values.get(i))));
                                }else {
                                    //Check CHAR max length
                                    if(this.getValType(valinsert).equals("CHAR") && Integer.parseInt(column.get("size").toString()) < valinsert.length()-2){
                                        throw new ConstrainException(String.format("Invalid CHAR size %s for columns '%s'", valinsert.length()-2, column.get("name").toString()));
                                    }else {
                                        rowMap.put(columns.get(i),valinsert);
                                    }
                                    //Check not null
                                    Map<String, JSONObject> cols = currTable.getColumns();
                                    for(String col : cols.keySet()){
                                        //Check not null
                                        if(rowMap.get(col) == null && cols.get(col).get("notNull").equals("true")){
                                            throw new ConstrainException(String.format("Insert violates NOT NULL constraint on column '%s' of table '%s'.",
                                                    col, currTable.getName()));
                                        }
                                        System.out.println(String.format("newRow: %s", rowMap));
                                        if(rowMap.get(col) == null && currTable.getPK().contains(col)){
                                            throw new ConstrainException(String.format("Insert violates PRIMARY KEY constraint on column '%s', cannot be NULL", col));
                                        }
                                        //Check CHECK
                                        List<JSONObject> chks = db.getCH(currTable.getName(), col);
                                        if(chks.size() > 0){
                                            //Si no cumple con el while
                                            for(JSONObject chk : chks){
                                                if(!this.evalWhere((String) chk.get("expression"), data)){
                                                    throw new ConstrainException(String.format("Update violates '%s' CHECK", chk.get("name")));
                                                }
                                            }
                                        }
                                    }
                                    System.out.println(String.format("COLS: %s PKs: %s Disjoint: %s", columns, currTable.getPK(), Collections.disjoint(columns, currTable.getPK())));
                                    if(!Collections.disjoint(columns, currTable.getPK())){
                                        //Check PK
                                        List<String> pk_vals = new LinkedList<>();
                                        for(String pkey : currTable.getPK()){
                                            pk_vals.add(rowMap.get(pkey).toString());
                                        }
                                        if(!isUniquePK(pk_vals)){
                                            throw new ConstrainException(String.format("Invalid values '%s' for PRIMARY KEY %s on INSERT. (PK must be unique)", pk_vals, currTable.getPK()));
                                        }
                                    }
                                    //Check FK
                                    for(JSONObject fk : db.getFKs(currTable.getName())){
                                        //Revisar si la columna de FK se usa en este insert
                                        int ins_fk = 0;
                                        JSONArray l_cols = (JSONArray) fk.get("columns");
                                        for(Object lcol : l_cols){
                                            if(columns.contains((String) lcol)){
                                                ins_fk++;
                                            }
                                        }
                                        if(ins_fk > 0){
                                            JSONArray f_cols = (JSONArray) fk.get("referencedColumns");
                                            List<String> fkey = new LinkedList<>();
                                            for(Object fcol : l_cols){
                                                fkey.add((String) rowMap.get((String) fcol));
                                            }

                                            if(!existsInForeginPK((String) fk.get("referencedTable"), fkey)){
                                                throw new ConstrainException(String.format("Update violates FOREIGN KEY '%s'", fk.get("name")));
                                            }
                                        }

                                    }
                
                                    
                                }
                            }else {
                                throw new ConstrainException(String.format("Columns '%s' does not exists on table '%s'", columns.get(i), currTable.getName()));
                            }
                        }
                    }
                    mapWriter.write(rowMap, header, processors);
            }
            mapWriter.close();
            mapReader.close();
            //Borrar original y Cambiar archivo auxiliar por normal
            File old = new File(currTable.physicalLocation());
            old.delete();
            File newfile = new File(tempFile);
            newfile.renameTo(old);
            
        }
        catch (IOException ex) {
            Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
        }
        doneWithTables();
        return updatedRows;
    }
    
    /**
     * Returns a list of Map<String, Object>
     * @param columns already validated columns
     * @param validation
     * @param orderBy a column to order by
     * @param orderIn 0 for don't car, 1 for AC, 2 for DESC
     */
    public List<Map<String, Object>> select(List<String> columns, String validation, String orderBy, int orderIn) throws ConstrainException{
        LinkedList<LinkedList<Map<String, Object>>> partial_results = new LinkedList<>();
        for(MetaTable selTable : this.currTables.values()){
            //Juntamos header para lectura parcial
            List<String> partial_header  = new LinkedList<>();
            for(String col : columns){
                if(this.existsColumn(selTable.getName(), col) == 1){
                    partial_header.add(col);
                }else if(this.existsColumn(selTable.getName(), col) == 2){
                    throw new ConstrainException(String.format("Column %s is ambiguous.", col));
                }
            }
            
            LinkedList<Map<String, Object>> partial_result = new LinkedList<>();
            //Leemos del archivo
            ICsvMapReader mapReader;
            try {
                mapReader = new CsvMapReader(new FileReader(selTable.physicalLocation()), CsvPreference.STANDARD_PREFERENCE);
                
                // the header columns are used as the keys to the Map
                final String[] header = mapReader.getHeader(true);
                final CellProcessor[] processors =  new CellProcessor[selTable.getColumns().size()];
                Map<String, Object> rowMap;
                //Mientras haya que leer
                while( (rowMap = mapReader.read(header, processors)) != null ) {
                        //Preparamos objeto como lo espera el evalWhere
                        Map<String, Map<String, Object>> data = new HashMap<>();
                        data.put(selTable.getName(), rowMap);
                        //Si se cumple con el where
                        if(validation == null || this.evalWhere(validation, data)){
                            Map<String, Object> selMap = new HashMap<>();
                            for(String fh : header){
                                if(partial_header.contains(fh)){
                                    selMap.put(fh, rowMap.get(fh));
                                }
                            }
                            partial_result.add(selMap);
                        }
                }
                mapReader.close();
                partial_results.add(partial_result);

            }
            catch (IOException ex) {
                Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
            }
        
        }
        
        LinkedList<Map<String, Object>> result = new LinkedList<>();
        if(partial_results.size() == 1){
            result = partial_results.get(0);
        }else {
            for(int i = 0; i < partial_results.get(0).size(); i++){
                Map<String, Object> crossedRow = new LinkedHashMap<>();
                crossedRow.putAll(partial_results.get(0).get(i));
                
                for(int j = 1; j < partial_results.size(); j++){
                    for(int k = 0; k < partial_results.get(j).size(); k++){
                        crossedRow.putAll(partial_results.get(j).get(k));
                        result.add(crossedRow);
                    }
                }
                
            }
        }
        if(orderBy !=null){
            if(orderIn == 2){
                orderListMaps(result, orderBy, true);
            }
            if(orderIn == 1 || orderIn == 0){
                orderListMaps(result, orderBy, false);
            }
        }
        
        for(Map<String,Object> r : result){
            System.out.println(String.format("%s", r));
        }

        doneWithTables();
        return result;
    }
    
    private List<Map<String,Object>> orderListMaps(List<Map<String,Object>> original, final String col, final boolean asc){
        
        Collections.sort(original, new Comparator() {
                @Override
                public int compare(Object o1, Object o2) {
                        Map<String, Object> o1m = (Map<String, Object>) o1;
                        Map<String, Object> o2m = (Map<String, Object>) o2;
                        int result = 0;
                        if(o1m.get(col).equals("NULL") && !o2m.get(col).equals("NULL")){
                            result =  -1000;
                        }
                        if(o2m.get(col).equals("NULL") && !o1m.get(col).equals("NULL")){
                            result = 1000;
                        }
                        if(o1m.get(col).equals("NULL") && o2m.get(col).equals("NULL")){
                            result = 0;
                        }
                        
                        String val1 = (String) o1m.get(col);
                        String val2 = (String) o2m.get(col);
                        
                        String expr1 = String.format("%s > %s", val1, val2);
                        String expr2 = String.format("%s < %s", val1, val2);
                        
                        try {
                            ScriptEngineManager mgr = new ScriptEngineManager();
                            ScriptEngine engine = mgr.getEngineByName("JavaScript");
                            Object gtr = engine.eval(expr1);
                            Object lq = engine.eval(expr2);
                            
                            if((boolean) gtr){
                                result = 1;
                            }
                            else if((boolean)lq){
                                result = -1;
                            }
                            else {
                                result = 0;
                            }

                        }catch(ScriptException e){
                            System.out.println(e);
                        }
                    if(asc){
                        result = result*(-1);
                    }
                    return result;
                }
        });

        return original;
    }
    
    /**
     * Evaluates a expression according to the API.
     * @param expr according to the API with full columns name in complete
     * @param values Map got from the DB
     * @return 
     */
    private boolean evalWhere(String expr, Map<String, Map<String, Object>> values) throws ConstrainException{
        
        //We check the patterns in where
        Pattern column_pattern = Pattern.compile( "\\{([^\\. ;]+\\.)?[^\\. ;]+\\}"); //Hace match de cosas como {tabla.columna} o {columna}
        Matcher matcher = column_pattern.matcher(expr);
        //Va a iterar cada elemento que cumpla con patrón
        while(matcher.find()){
            String col = matcher.group();
            String[] col_parts = col.substring(1, col.length()-1).split("\\.");
            //Normalizar col para el regex a reemplazar en la expresión luego
            col = col.replace("{", "\\{").replace("}", "\\}");
            if(col_parts.length == 1){
                //Buscamos en qué tabla está                
                if(this.existsColumn(null, col_parts[0]) == 1){
                    String tname = "";
                    for(Map.Entry<String, MetaTable> currTs : currTables.entrySet()){
                        if(currTs.getValue().getColumns().containsKey(col_parts[0])){
                            tname = currTs.getKey();
                        }
                    }
                    Object replacement = new String();
                    replacement = values.get(tname).get(col_parts[0]);
                    System.out.println(String.format("in EvalWhere: %s", replacement));
                    if(replacement==null || replacement.equals(""))
                        replacement = "null";
                    System.out.println(String.format("after EvalWhere: %s", values.get(tname).get(col_parts[0])));
                    expr = expr.replaceAll(col, replacement.toString());
                }else {
                    throw new ConstrainException(String.format("Column '%s' does not exists.", col_parts[0]));
                }
            } else {
                //Vemos por tabla específica
                if(this.existsColumn(col_parts[0], col_parts[1]) == 1){
                    Object replacement = values.get(col_parts[0]).get(col_parts[1]);
                    expr = expr.replaceAll(col, replacement.toString());
                }else {
                    throw new ConstrainException(String.format("Column '%s.%s' does not exists.", col_parts[0], col_parts[1]));
                }
                
            }
        }
        try {
            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName("JavaScript");
            Object res = engine.eval(expr);
            return (boolean) res;
        
        }catch(ScriptException e){
            System.out.println(e);
            throw new ConstrainException("WHERE expression is invalid '"+expr+"'");
        }
    }
    
    
    public static void main(String[] args){
        DB db = new DB("proyecto");
        DMLManager dbm = new DMLManager(db);
        dbm.workWithTables("empleado_sucursal", "sucursal");
        
        
        List<String> cols = new LinkedList<>();
        cols.add("empleado_id");
        cols.add("sucursal_id");
        cols.add("id");
        cols.add("nombre");
        cols.add("direccion");
        
        //cols.add("id");
        
        
        List<String> vals = new LinkedList<>();
        vals.add("6");
        vals.add("1");
        //vals.add("11");
        
        
        try {
            //dbm.insert(vals, cols);
          //  vals.set(0, "13");
           // dbm.insert(vals, cols);
            //dbm.update(vals, cols, "{nombre}==\'Pedro\'");
            //dbm.delete("{MMM.t} < 3");
            //dbm.update(cols, vals, "{a} > 2");
            
            dbm.select(cols, null, "id", 1);
        } catch (ConstrainException ex) {
            Logger.getLogger(DMLManager.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
}
                               
                
                                    
