/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moa.tasks;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import moa.core.ObjectRepository;
import com.github.javacliparser.StringOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Runs and evaluates all experiments from  configurations defined in config file
 * @author Tomas Chovanak
 */
public class GridSearchEvaluator extends MainTask {

    private int id = 0;
    public StringOption pathToConfigFile = new StringOption("pathToConfigFile", 'o',
            "Path to file where detail of configuration is stored.", "./");
    private String pathToStream = null;
    private int fromid = 0;
    private boolean grouping = true;
    
    private String pathToSummaryOutputFile = "";
    private String pathToOutputFile = "";
    private String pathToCategoryMappingFile = "";
    private GridSearchLearnEvaluatePPSDMTask gpLearnEvaluateTask = null;
    
    public GridSearchEvaluator(int fromid) {
        this.fromid = fromid;
    }
    
    private void evaluate(List<Parameter> params){
        Map<String, Parameter> paramsMap = new HashMap<>();
        for(Parameter p : params){
            paramsMap.put(p.getName(), p);
        }
        grouping = (paramsMap.get("GROUPING").getValue() == 1.0)?true:false;
        gpLearnEvaluateTask = new GridSearchLearnEvaluatePPSDMTask(id, fromid, paramsMap, pathToStream, 
                pathToSummaryOutputFile, pathToOutputFile, pathToCategoryMappingFile, 
                grouping); 
        gpLearnEvaluateTask.doTask(); 
        this.id = this.gpLearnEvaluateTask.getId();
        gpLearnEvaluateTask = null;
        System.gc();
    }
    
    private void startGridEvaluation(List<Parameter> params, List<Parameter> preparedParameters){
        if(params.isEmpty()){
            this.evaluate(preparedParameters);
        }else{
            List<Parameter> origParamsCopy = deepCopy(params);
            Parameter p = origParamsCopy.remove(0);
            if(p.getValue() > 0){
                origParamsCopy = deepCopy(params);
                Parameter p2 = origParamsCopy.remove(0);
                List<Parameter> copyParams = deepCopy(preparedParameters);
                copyParams.add(p2);
                this.startGridEvaluation(origParamsCopy, copyParams);
            }else{
                //double[] b = p.getBoundaries();
                Iterator<Double> it = p.getPossibleValues().iterator();
                //for(double i = b[0]; i <= b[1]; i+= b[2]){  
                while(it.hasNext()){  
                    origParamsCopy = deepCopy(params);
                    Parameter p2 = origParamsCopy.remove(0);
                    p2.setValue(it.next());
                    List<Parameter> copyParams = deepCopy(preparedParameters);
                    copyParams.add(p2);
                    this.startGridEvaluation(origParamsCopy, copyParams);
                }
            }
        }
    }
    
    
    @Override
    protected Object doMainTask(TaskMonitor tm, ObjectRepository or) {
        InputStream fileStream;
        BufferedReader fileReader = null;
        try {
            fileStream = new FileInputStream(this.pathToConfigFile.getValue());
            fileReader = new BufferedReader(new InputStreamReader(fileStream));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(GridSearchEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(fileReader == null){
            return null;
        }
        startEvaluation(fileReader);
        return null;
    }
    
    
    private List<Parameter> deepCopy(List<Parameter> orig){
        List<Parameter> copy = new ArrayList<>(); 
        Iterator<Parameter> iterator = orig.iterator(); 
        while(iterator.hasNext()){ 
            try { 
                copy.add(iterator.next().clone());
            } catch (CloneNotSupportedException ex) {
                Logger.getLogger(GridSearchEvaluator.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return copy;
    }
    
    private static void writeHeader(String path) {
        try {
            try (FileWriter writer = new FileWriter(path, true)) {
                writer.append("FILE ID");writer.append(',');
                writer.append("USE GROUPING");writer.append(',');
                // RECOMMEND PARAMETERS
                writer.append("REC:RECOMMEND STRATEGY");writer.append(',');
                writer.append("GEN:MAXIMAL DIFFERENCE OF CLUSTERING IDs");writer.append(',');
                writer.append("REC:EVALUATION WINDOW SIZE");writer.append(',');
                writer.append("REC:NUM OF RECOMMENDED ITEMS");writer.append(',');
                // INCMINE PARAMETERS
                writer.append("FPM:MIN SUPPORT");writer.append(',');
                writer.append("FPM:RELAXATION RATE");writer.append(','); 
                writer.append("FPM:FIXED SEGMENT LENGTH");writer.append(',');
                writer.append("FPM:GROUP FIXED SEGMENT LENGTH");writer.append(',');
                writer.append("FPM:MAX ITEMSET LENGTH");writer.append(',');
                writer.append("FPM:WINDOW SIZE");writer.append(',');
                // UNIVERSAL PARAMETERS - RESTRICTIONS
                writer.append("RES:NUM OF DIMENSTIONS IN USER MODEL");writer.append(',');
                writer.append("RES:MAX FCI SET COUNT");writer.append(',');
                writer.append("RES:MIN TRANSACTIONS PER SECOND");writer.append(',');
                writer.append("RES:MAX UPDATE TIME");writer.append(',');
                writer.append("RES:START EVALUATING FROM TID");writer.append(',');
                // CLUSTERING PARAMETERS
                writer.append("CLU:MIN NUM OF CHANGES IN USER MODEL");writer.append(',');
                writer.append("CLU:MIN NUM OF CHANGES IN MICROCLUSTERS");writer.append(',');
                writer.append("CLU:NUM OF GROUPS");writer.append(',');
                writer.append("CLU:NUM OF MICROKERNELS");writer.append(',');
                writer.append("CLU:KERNEL RADI FACTOR");writer.append(',');
                // RESULTS 
                writer.append("GGC:ALL HITS");writer.append(',');
                writer.append("GGC:REAL RECOMMENDED");writer.append(',');
                writer.append("GGC:PRECISION");writer.append(',');
                writer.append("GGC:RECALL");writer.append(',');
                writer.append("GGC:F1");writer.append(',');
                writer.append("GGC:NDCG");writer.append(',');
                
                writer.append("GO:ALL HITS");writer.append(',');
                writer.append("GO:REAL RECOMMENDED ITEMS");writer.append(',');
                writer.append("GO:PRECISION");writer.append(',');
                writer.append("GO:RECALL");writer.append(',');
                writer.append("GO:F1");writer.append(',');
                writer.append("GO:NDCG");writer.append(',');
                
                writer.append("OG: ALL HITS");writer.append(',');
                writer.append("OG: REAL RECOMMENDED ITEMS");writer.append(',');
                writer.append("OG:PRECISION");writer.append(',');
                writer.append("OG:RECALL");writer.append(',');
                writer.append("OG:F1");writer.append(',');
                writer.append("OG:NDCG");writer.append(',');
                
                writer.append("ALL TESTED ITEMS");writer.append(',');
                writer.append("ALL TESTED TRANSACTIONS");writer.append(',');
                writer.append("MAX RECOMMENDED ITEMS");writer.append(',');
                writer.append("DURATION IN SECONDS");writer.append(',');
                writer.append("TRANSACTIONS PER SECOND");writer.append(',');
                writer.append("NUM ANALYZED TRANSACTIONS");writer.append(',');
                writer.append('\n');
                writer.close();
                
            }
        } catch (IOException ex) {
            Logger.getLogger(GridSearchEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private static void startEvaluation(BufferedReader fileReader) {
        List<Parameter> params = new ArrayList<>();
        try {
            String inputSessionFile = fileReader.readLine().split(",")[1].trim();
            String outputToDirectory = fileReader.readLine().split(",")[1].trim();
            String pathToCategoryMappingFile = fileReader.readLine().split(",")[1].trim();
            int fromid = Integer.parseInt(fileReader.readLine().split(",")[1].trim());
            // READ IN PARAMETERS 
            for(String line = fileReader.readLine(); line != null; line = fileReader.readLine()) {
                String[] row = line.split(",");
                double value = Double.parseDouble(row[1].trim());
                // LIST OF POSSIBLE VALUES
                Parameter p;
                if(value == -1){
                    p = new Parameter(value);
                    for(int i = 2; i < row.length; i++){
                        p.addPossibleValue(Double.parseDouble(row[i].trim()));
                    }
                }else {
                    //boundaries
                    p = new Parameter(0.0, value,Double.parseDouble(row[2].trim()), 
                            Double.parseDouble(row[3].trim()));   
                }
                p.setName(row[0].trim());
                params.add(p);
            }
            writeHeader(outputToDirectory + "summary_results.csv");
            GridSearchEvaluator evaluator = new GridSearchEvaluator(fromid);
            evaluator.setPathToOutputFile(outputToDirectory);
            evaluator.setPathToInputFile(inputSessionFile);
            evaluator.setPathToCategoryMappingFile(pathToCategoryMappingFile);
            evaluator.setPathToSummaryOutputFile(outputToDirectory + "summary_results.csv");
            List<Parameter> preparedParams = new ArrayList<>();
            evaluator.startGridEvaluation(params, preparedParams);  
        } catch (IOException ex) {
            Logger.getLogger(GridSearchEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        } 
    }
    
    /**
     * Arguments:
     *  1. config file: path to config file where parameters for grid search are declared
    * @param args 
     */
    public static void main(String args[]){
        InputStream fileStream;
        BufferedReader fileReader = null;
        try {
            if(args.length > 0){
                fileStream = new FileInputStream(args[0]);
            }else{
                fileStream = new FileInputStream("g:\\workspace_DP2\\results_grid\\config\\config_sacbee.csv");
            }
            
            fileReader = new BufferedReader(new InputStreamReader(fileStream));
            
        } catch (FileNotFoundException ex) {
            Logger.getLogger(GridSearchEvaluator.class.getName()).log(Level.SEVERE, null, ex);
        }
        if(fileReader == null){
            return;
        }
        
        startEvaluation(fileReader);
        
    }
    
    public void setPathToInputFile(String path) {
        this.pathToStream = path;
    }
    
    public void setPathToStream(String pathToStream) {
        this.pathToStream = pathToStream;
    }

    public void setPathToSummaryOutputFile(String pathToSummaryOutputFile) {
        this.pathToSummaryOutputFile = pathToSummaryOutputFile;
    }

    public void setPathToOutputFile(String pathToOutputFile) {
        this.pathToOutputFile = pathToOutputFile;
    }

    public String getPathToCategoryMappingFile() {
        return pathToCategoryMappingFile;
    }

    public void setPathToCategoryMappingFile(String pathToCategoryMappingFile) {
        this.pathToCategoryMappingFile = pathToCategoryMappingFile;
    }

    @Override
    public Class<?> getTaskResultType() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
