/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package dbman;

import java.util.List;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jorgelainfiesta
 */
public interface MetaTable {
    public String getName();
    public Map<String, JSONObject> getColumns();
    public List<String> getPK();
    public String physicalLocation();
    public String[] getOrderedColumns();
    public void setOrderedColumns(JSONArray cols);
    public boolean hasPK(String column, String value);
    
}
