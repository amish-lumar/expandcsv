package com.expandcsv.expandcsv;

import com.expandcsv.expandcsv.OrderJson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpandCSV {
    private List<Object[]> sheetMatrix = null;
    private List<String> pathList = null;
    private String tmp[] = null;
    private HashSet<String> primitivePath = null;
    private HashSet<String> primitiveUniquePath = null;
    private List<String> unique = null;
    private String regex = "(\\[[0-9]*\\]$)";
    private Pattern pattern = Pattern.compile(regex, Pattern.MULTILINE);
    private JsonElement ele = null;
    private String tmpPath = null;
    private OrderJson makeOrder = new OrderJson();

    /**
     * This method does some pre processing and then calls make2D() to get the
     * 2D representation of Json document.
     *
     * @return returns a JFlat object
     */
    public ExpandCSV json2Sheet(String jsonString) {
        Configuration.setDefaults(new Configuration.Defaults() {
            private final JsonProvider jsonProvider = new JacksonJsonProvider();
            private final MappingProvider mappingProvider = new JacksonMappingProvider();
            // @Override
            public JsonProvider jsonProvider() {
                return jsonProvider;
            }
            // @Override
            public MappingProvider mappingProvider() {
                return mappingProvider;
            }
            // @Override
            public Set options() {
                return EnumSet.noneOf(Option.class);
            }
        });
        Configuration conf = Configuration.defaultConfiguration().addOptions(Option.DEFAULT_PATH_LEAF_TO_NULL)
                .addOptions(Option.SUPPRESS_EXCEPTIONS);
        Configuration pathConf = Configuration.defaultConfiguration().addOptions(Option.AS_PATH_LIST)
                .addOptions(Option.ALWAYS_RETURN_LIST);
        DocumentContext parse = null;
        sheetMatrix = new ArrayList<Object[]>();
        ele = new JsonParser().parse(jsonString);
        pathList = JsonPath.using(pathConf).parse(jsonString).read("$..*");
        parse = JsonPath.using(conf).parse(jsonString);
        primitivePath = new LinkedHashSet<String>();
        primitiveUniquePath = new LinkedHashSet<String>();
        for (String o : pathList) {
            Object tmp = parse.read(o);
            if (tmp == null) {
                primitivePath.add(o);
            } else {
                String dataType = tmp.getClass().getSimpleName();
                if (dataType.equals("Boolean") || dataType.equals("Integer") || dataType.equals("String")
                        || dataType.equals("Double") || dataType.equals("Long")) {
                    primitivePath.add(o);
                } else {
                    // its not a primitive data type
                }
            }
        }
        for (String o : primitivePath) {
            o = o.substring(6,o.length()-2);
            Matcher m = pattern.matcher(o);
            if (m.find()) {
                tmp = o.replace("$", "").split("(\\[[0-9]*\\]$)");
                tmp[0] = tmp[0].replaceAll("(\\[[0-9]*\\])", "");
                primitiveUniquePath.add("/" + (tmp[0] + m.group()).replace("'][", "/").replace("[", "").replace("]", "")
                        .replace("''", "/").replace("'", ""));
            } else {
                primitiveUniquePath.add(o.replace("'][", ".").replace("]['", "."));
//                primitiveUniquePath.add("/" + o.replace("$", "").replaceAll("(\\[[0-9]*\\])", "").replace("[", "")
//                        .replace("]", "").replace("''", "/").replace("'", ""));
            }
        }
        unique = new ArrayList<String>(primitiveUniquePath);
        Object[] header = new Object[unique.size()];
        int i = 0;
        for (String o : unique) {
            header[i] = o;
            i++;
        }
        //header of the csv
        sheetMatrix.add(header);
        //adding all the content of csv
        sheetMatrix.add(matrix(new Object[unique.size()], new Object[unique.size()], ele, "$"));
        Object last[] = sheetMatrix.get(sheetMatrix.size() - 1);
        Object secondLast[] = sheetMatrix.get(sheetMatrix.size() - 2);
        boolean delete = true;
        for (Object o : last) {
            if (o != null) {
                delete = false;
                break;
            }
        }
        if (!delete) {
            delete = true;
            for (int DEL = 0; DEL < last.length; DEL++) {
                if (last[DEL] != null && !last[DEL].equals(secondLast[DEL])) {
                    delete = false;
                    break;
                }
            }
        }
        if (delete)
            sheetMatrix.remove(sheetMatrix.size() - 1);
        return this;
    }
    private Object[] matrix(Object[] cur, Object[] old, JsonElement ele, String path) {
        if (ele.isJsonArray()) {
            int arrIndex = 0;
            for (JsonElement tmp : ele.getAsJsonArray()) {
                cur = new Object[unique.size()];
                row(cur, tmp.getAsJsonObject(), path +"[" + arrIndex + "]");
                sheetMatrix.add(cur);
                arrIndex++;
            }
        }
        return cur;
    }

    private Object[] row(Object[] cur,JsonElement ele, String path) {
        if (ele.isJsonObject()) {
            ele = makeOrder.orderJson(ele);
            for (Map.Entry<String, JsonElement> entry : ele.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    tmpPath = path +"['"+ entry.getKey()+"']";
                    Matcher m = pattern.matcher(tmpPath);
                    if (m.find()) {
                        String[] tmp = tmpPath.replace("$", "").split("(\\[[0-9]*\\]$)");
                        tmp[0] = tmp[0].replaceAll("(\\[[0-9]*\\])", "");
                        tmpPath = ("/" + (tmp[0] + m.group()).replace("'][", "/").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    } else {
                        tmpPath = tmpPath.replace("'][", ".").replace("]['", ".");
                        tmpPath = tmpPath.substring(4,tmpPath.length()-2);
                    }

                    if (unique.contains(tmpPath)) {
                        int index = unique.indexOf(tmpPath);
                        cur[index] = entry.getValue().getAsJsonPrimitive();
                    }
                    tmpPath = null;
                } else if (entry.getValue().isJsonObject()) {
                    cur = row(cur, entry.getValue().getAsJsonObject(),
                            path +"['" + entry.getKey()+"']");
                } else if (entry.getValue().isJsonArray()) {
                    cur = row(cur, entry.getValue().getAsJsonArray(),
                            path +"['" + entry.getKey()+"']");
                }
            }
        } else if (ele.isJsonArray()) {
            int arrIndex = 0;
            for (JsonElement tmp : ele.getAsJsonArray()) {
                if (tmp.isJsonPrimitive()) {
                    tmpPath = path +"['"+ arrIndex +"']";
                    Matcher m = pattern.matcher(tmpPath);
                    if (m.find()) {
                        String tmp1[] = tmpPath.replace("$", "").split("(\\[[0-9]*\\]$)");
                        tmp1[0] = tmp1[0].replaceAll("(\\[[0-9]*\\])", "");
                        tmpPath = ("/" + (tmp1[0] + m.group()).replace("'][", "/").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    } else {
                        tmpPath = tmpPath.replace("'][", ".").replace("]['", ".");
                        tmpPath = tmpPath.substring(4,tmpPath.length()-2);
                    }
                    if (unique.contains(tmpPath)) {
                        int index = unique.indexOf(tmpPath);
                        cur[index] = tmp.getAsJsonPrimitive();
                    }
                    tmpPath = null;
                } else {
                    if (tmp.isJsonObject()) {
                        row(cur, tmp.getAsJsonObject(), path +"[" + arrIndex + "]");
                    } else if (tmp.isJsonArray()) {
                        row(cur, tmp.getAsJsonArray(), path+"["+arrIndex+"]");
                    }
                }
                arrIndex++;
            }
        }
        return cur;
    }

    /**
     * This function transforms the JSON document to its equivalent 2D representation.
     *
     * @param cur
     *            its the logical current row of the Json being processed
     * @param old
     *            it keeps the old row which is always assigned to the current
     *            row.
     * @param ele
     *            this keeps the part of json being parsed to 2D.
     * @param path
     *            this mantains the path of the Json element being processed.
     * @return
     */
    private Object[] make2D(Object[] cur, Object[] old, JsonElement ele, String path) {
        cur = old.clone();
        boolean gotArray = false;
        if (ele.isJsonObject()) {
            ele = makeOrder.orderJson(ele);
            for (Map.Entry<String, JsonElement> entry : ele.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    tmpPath = path +"['"+ entry.getKey()+"']";
                    Matcher m = pattern.matcher(tmpPath);
                    if (m.find()) {
                        String[] tmp = tmpPath.replace("$", "").split("(\\[[0-9]*\\]$)");
                        tmp[0] = tmp[0].replaceAll("(\\[[0-9]*\\])", "");
                        tmpPath = ("/" + (tmp[0] + m.group()).replace("'][", "/").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    } else {
                        tmpPath = ("/" + tmpPath.replace("$", "").replaceAll("(\\[[0-9]*\\])", "").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    }

                    if (unique.contains(tmpPath)) {
                        int index = unique.indexOf(tmpPath);
                        cur[index] = entry.getValue().getAsJsonPrimitive();
                    }
                    tmpPath = null;
                } else if (entry.getValue().isJsonObject()) {
                    cur = make2D(new Object[unique.size()], cur, entry.getValue().getAsJsonObject(),
                            path +"['" + entry.getKey()+"']");
                } else if (entry.getValue().isJsonArray()) {
                    cur = make2D(new Object[unique.size()], cur, entry.getValue().getAsJsonArray(),
                            path +"['" + entry.getKey()+"']");
                }
            }
        } else if (ele.isJsonArray()) {
            int arrIndex = 0;
            for (JsonElement tmp : ele.getAsJsonArray()) {
                if (tmp.isJsonPrimitive()) {
                    tmpPath = path +"['"+ arrIndex +"']";
                    Matcher m = pattern.matcher(tmpPath);
                    if (m.find()) {
                        String tmp1[] = tmpPath.replace("$", "").split("(\\[[0-9]*\\]$)");
                        tmp1[0] = tmp1[0].replaceAll("(\\[[0-9]*\\])", "");
                        tmpPath = ("/" + (tmp1[0] + m.group()).replace("'][", "/").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    } else {
                        tmpPath = ("/" + tmpPath.replace("$", "").replaceAll("(\\[[0-9]*\\])", "").replace("[", "")
                                .replace("]", "").replace("''", "/").replace("'", ""));
                    }

                    if (unique.contains(tmpPath)) {
                        int index = unique.indexOf(tmpPath);
                        cur[index] = tmp.getAsJsonPrimitive();
                    }
                    tmpPath = null;
                } else {
                    if (tmp.isJsonObject()) {
                        gotArray = isInnerArray(tmp);
                        sheetMatrix.add(make2D(new Object[unique.size()], cur, tmp.getAsJsonObject(), path +"[" + arrIndex + "]"));
                        if (gotArray) {
                            sheetMatrix.remove(sheetMatrix.size() - 1);
                        }
                    } else if (tmp.isJsonArray()) {
                        make2D(new Object[unique.size()], cur, tmp.getAsJsonArray(), path+"["+arrIndex+"]");
                    }
                }
                arrIndex++;
            }
        }
        return cur;
    }

    /**
     * This method checks whether object inside an array contains an array or
     * not.
     *
     * @param ele
     *            it a Json object inside an array
     * @return it returns true if Json object inside an array contains an array
     *         or else false
     */
    private boolean isInnerArray(JsonElement ele) {

        for (Map.Entry<String, JsonElement> entry : ele.getAsJsonObject().entrySet()) {
            if (entry.getValue().isJsonArray()) {
                if (entry.getValue().getAsJsonArray().size() > 0)

                    for (JsonElement checkPrimitive : entry.getValue().getAsJsonArray()) {

                        if (checkPrimitive.isJsonObject()) {
                            return true;
                        }
                    }
            }
        }
        return false;
    }

    /**
     * This method replaces the default header separator i.e. "/" with a space.
     *
     * @return JFlat
     * @throws Exception
     */
    public ExpandCSV headerSeparator() throws Exception{
        return headerSeparator(" ");
    }

    /**
     * This method replaces the default header separator i.e. "/" with a custom separator provided by user.
     *
     * @param separator
     * @return JFlat
     * @throws Exception
     */
    public ExpandCSV headerSeparator(String separator) throws Exception{
        try{
            int sheetMatrixLen = this.sheetMatrix.get(0).length;
            for(int I=0; I < sheetMatrixLen; I++){
                this.sheetMatrix.get(0)[I] = this.sheetMatrix.get(0)[I].toString().replaceFirst("^\\/", "").replaceAll("/", separator).trim();
            }
        }catch(NullPointerException nullex){
            throw new Exception("The JSON document hasn't been transformed yet. Try using json2Sheet() before using headerSeparator");
        }
        return this;
    }

    /**
     * This method returns the sheet matrix.
     *
     * @return List<Object>
     */
    public List<Object[]> getJsonAsSheet() {
        return this.sheetMatrix;
    }

    /**
     * This method returns unique fields of the json
     *
     * @return List<String>
     */
    public List<String> getUniqueFields() {
        return this.unique;
    }

    /**
     * This method writes the 2D representation in csv format with ',' as
     * default delimiter.
     *
     * @param writer
     *            it takes the destination path for the csv file.
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    public void write2csv(PrintWriter writer) throws FileNotFoundException, UnsupportedEncodingException {
        this.write2csv(writer, ',');
    }

    /**
     * This method writes the 2D representation in csv format with custom
     * delimiter set by user.
     *
     * @param writer
     *            it takes the destination path for the csv file.
     * @param delimiter
     *            it represents the delimiter set by user.
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     */
    public void write2csv(PrintWriter writer, char delimiter){
        boolean comma = false;
        for (Object[] o : this.sheetMatrix) {
            comma = false;
            for (Object t : o) {
                if (t == null) {
                    writer.print(comma == true ? delimiter : "");
                } else {
                    writer.print(comma == true ? delimiter + t.toString() : t.toString());
                }
                if (comma == false)
                    comma = true;
            }
            writer.println();
        }
    }
}
