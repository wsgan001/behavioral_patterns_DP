/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package moa.core.PPSDM;

import moa.core.PPSDM.dto.FIWrapper;
import com.yahoo.labs.samoa.instances.Instance;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import moa.core.Example;
import moa.core.FrequentItemset;
import moa.core.PPSDM.clustering.ClusteringComponent;
import moa.core.PPSDM.dto.RecommendationResults;
import moa.core.PPSDM.patternMining.PatternMiningComponent;
import moa.core.PPSDM.utils.MapUtil;
import moa.core.PPSDM.utils.UtilitiesPPSDM;
import moa.core.SemiFCI;

/**
 *
 * @author Tomas
 */
public class RecommendationGenerator {
    
    private PatternMiningComponent patternsMiner;
    private ClusteringComponent clustererPPSDM;
    private List<Integer> recsCombined;
    private List<Integer> recsOnlyFromGlobal;
    private List<Integer> recsOnlyFromGroup;
    private int cntAll;
    private int cntOnlyGlobal;
    private int cntOnlyGroup;
    private RecommendationConfiguration config;

    public RecommendationGenerator(RecommendationConfiguration config, 
            PatternMiningComponent patternsMiner,
            ClusteringComponent clustererPPSDM) {
        this.config = config;
        this.patternsMiner = patternsMiner;
        this.clustererPPSDM = clustererPPSDM;
       
    }
    
    
    
    public RecommendationResults generateRecommendations(Example e, boolean useGrouping) {
        // append group to instance that it belongs to...
        Instance session = (Instance)e.getData();
        // we need to copy instance data to sessionArray where we can modify them 
        // because data in Instance cannot be changed i dont know why...
        ArrayList<Double> sessionArray = new ArrayList<>(); 
        for(int i = 0; i < session.numValues(); i++){
            sessionArray.add(i,session.value(i)); 
        }
        // TESTING Instance - how it performs on recommendation.
        // get window from actual instance
        List<Integer> window = new ArrayList<>(); // items inside window 
        List<Integer> outOfWindow = new ArrayList<>(); // items out of window 

        if((config.getEWS() >= (sessionArray.size()-2))){ 
            return null; // this is when session array is too short - it is ignored.
        }
        
        for(int i = 2; i <= config.getEWS() + 1; i++){ // first item is groupid, 2nd uid
            window.add((int) Math.round(sessionArray.get(i)));
        }
        
        // maximum number of evaluated future items is the same as number of recommended items.
        for(int i = config.getEWS() + 2, j = 0; i < sessionArray.size(); i++){
            outOfWindow.add((int) Math.round(sessionArray.get(i)));
        }
        
        //to get all fcis found 
        List<FIWrapper> mapFciWeight = new LinkedList<>();
        List<FIWrapper> mapFciWeightGroup = new LinkedList<>();
        List<FIWrapper> mapFciWeightGlobal = new LinkedList<>(); 
        Iterator<FrequentItemset> itFis = null;
        itFis = this.patternsMiner.iteratorGlobalPatterns();
        int groupidSet = -1;
        
        while(itFis.hasNext()){
            FrequentItemset fi = itFis.next();
            if(fi.getSize() > 1){
                List<Integer> items = fi.getItems();
                double hitsVal = this.computeSimilarity(items,window);
                if(hitsVal == 0.0){
                    continue;
                }
                FIWrapper fciVal = new FIWrapper();
                fciVal.setItems(fi.getItems());
                fciVal.computeValue(hitsVal, fi.getSupportDouble());
                mapFciWeight.add(fciVal);
                mapFciWeightGlobal.add(fciVal);
            }
        }
        
        if(useGrouping && clustererPPSDM.isClustering()){
            // if already clustering was performed
            Double groupid = -1.0;
            UserModelPPSDM um = clustererPPSDM.getUserModelFromInstance(session);
            double distance = 0;
            if(um != null){
                groupidSet = (int)um.getGroupid();
                distance = um.getDistance();
                groupid = um.getGroupid(); // groupids sorted by preference
            }else{
                sessionArray.set(0,-1.0);
            }            
            //         This next block performs the same with group fcis. 
            if(groupid != -1.0){
                Iterator<FrequentItemset> itFisG = null;
                itFisG = this.patternsMiner.iteratorGroupPatterns((int) Math.round(groupid));
                
                while(itFisG.hasNext()){
                    FrequentItemset fi = itFisG.next();
                    if(fi.getSize() > 1){
                        List<Integer> items = fi.getItems();
                        double hitsVal = this.computeSimilarity(items,window);
                        if(hitsVal == 0.0){
                            continue;
                        }
                        FIWrapper fciVal = new FIWrapper();
                        fciVal.setItems(fi.getItems());
                        fciVal.setDistance(distance);
                        fciVal.computeValue(hitsVal, fi.getSupportDouble());
                        mapFciWeight.add(fciVal);
                        mapFciWeightGroup.add(fciVal);
                   }
                }
            }
        }
        
        // all fcis found have to be sorted descending by its support and similarity.
        Collections.sort(mapFciWeight);
        Collections.sort(mapFciWeightGroup);
        Collections.sort(mapFciWeightGlobal);
        switch (config.getRecommendationStrategy()) {
            case VOTES:
                generateRecsVoteStrategy(mapFciWeightGlobal,
                        mapFciWeightGroup, window);
                break;
            case FIRST_WINS:
                generateRecsFirstWinsStrategy(mapFciWeight, mapFciWeightGlobal,
                        mapFciWeightGroup, window);
                break;
        }        
        RecommendationResults results = new RecommendationResults();
        results.setTestWindow(outOfWindow);
        results.setNumOfRecommendedItems(config.getRC());
        results.setRecommendationsGGC(recsCombined);
        results.setRecommendationsGO(recsOnlyFromGlobal);
        results.setRecommendationsOG(recsOnlyFromGroup);
        results.setGroupid(groupidSet);
        return results;
    }
    
    private double computeSimilarity(List<Integer> items, List<Integer> window) {
        return ((double)UtilitiesPPSDM.computeLongestCommonSubset(items,window)) / 
                ((double)window.size());
    }
    
    private void generateRecsVoteStrategy(
                                    List<FIWrapper> mapFciWeightGlobal,
                                    List<FIWrapper> mapFciWeightGroup, 
                                    List<Integer> window) {
        Map<Integer, Double> mapItemsVotes = new HashMap<>();
        Map<Integer, Double> mapItemsVotesOnlyGlobal = new HashMap<>();
        Map<Integer, Double> mapItemsVotesOnlyGroup = new HashMap<>();
        Iterator<FIWrapper> itGlobal = mapFciWeightGlobal.iterator();
        Iterator<FIWrapper> itGroup = mapFciWeightGroup.iterator();
        
        while(itGlobal.hasNext() || itGroup.hasNext()){
            if(itGlobal.hasNext()){
               FIWrapper fci = itGlobal.next();
               Iterator<Integer> itFciItems = fci.getItems().iterator();
               while(itFciItems.hasNext()){
                   Integer item = itFciItems.next();         
                   if(mapItemsVotes.containsKey(item)){   
                       Double newVal = mapItemsVotes.get(item) + fci.getLcsVal()*fci.getSupport();
                       mapItemsVotes.put(item, newVal);
                   }else{
                       if(!window.contains(item)){
                           Double newVal =  fci.getLcsVal()*fci.getSupport();
                           mapItemsVotes.put(item, newVal);
                       }
                   }
                   if(mapItemsVotesOnlyGlobal.containsKey(item)){
                        Double newVal = mapItemsVotesOnlyGlobal.get(item) + fci.getLcsVal()*fci.getSupport();
                        mapItemsVotesOnlyGlobal.put(item, newVal);
                   }else{
                        if(!window.contains(item)){
                            Double newVal =  fci.getLcsVal()*fci.getSupport();
                            mapItemsVotesOnlyGlobal.put(item, newVal);
                        }
                   }
               }
            }
            if(itGroup.hasNext()){
               FIWrapper fci = itGroup.next();
               Iterator<Integer> itFciItems = fci.getItems().iterator();
               while(itFciItems.hasNext()){
                   Integer item = itFciItems.next();
                    double dist = fci.getDistance();
                    if(dist == 0.0){
                        dist = 1.0;
                    }else{
                        if(dist < 0){
                            dist = -dist;
                        }
                        dist = 1.0-dist;
                    }
                   if(mapItemsVotes.containsKey(item)){
                       Double newVal = mapItemsVotes.get(item) + fci.getLcsVal()*fci.getSupport()*(dist);
                       mapItemsVotes.put(item, newVal);
                   }else{
                       if(!window.contains(item)){
                           Double newVal =  fci.getLcsVal()*fci.getSupport()*(dist);
                           mapItemsVotes.put(item, newVal);
                       }
                   }  
                   if(mapItemsVotesOnlyGroup.containsKey(item)){
                       Double newVal = mapItemsVotesOnlyGroup.get(item) + fci.getLcsVal()*fci.getSupport()*dist;
                       mapItemsVotesOnlyGroup.put(item, newVal);
                   }else{
                       if(!window.contains(item)){
                           Double newVal =  fci.getLcsVal()*fci.getSupport()*dist;
                           mapItemsVotesOnlyGroup.put(item, newVal);
                       }
                   }   
               }
            }
        }
        mapItemsVotes = MapUtil.sortByValue(mapItemsVotes);
        mapItemsVotesOnlyGlobal = MapUtil.sortByValue(mapItemsVotesOnlyGlobal);
        mapItemsVotesOnlyGroup = MapUtil.sortByValue(mapItemsVotesOnlyGroup);
        recsCombined = new ArrayList<>();
        recsOnlyFromGlobal = new ArrayList<>();
        recsOnlyFromGroup = new ArrayList<>();
        int numRecommendedItems = config.getRC();
        cntAll = 0;
        cntOnlyGlobal = 0;
        cntOnlyGroup = 0;
        
        for(Map.Entry<Integer,Double> e : mapItemsVotes.entrySet()) {
            Integer item = e.getKey();
            recsCombined.add(item);
            cntAll++;
            if(cntAll >= numRecommendedItems){
                break;
            }       
        }
        for(Map.Entry<Integer,Double> e : mapItemsVotesOnlyGlobal.entrySet()) {
            Integer item = e.getKey();
            recsOnlyFromGlobal.add(item);
            cntOnlyGlobal++;
            if(cntOnlyGlobal >= numRecommendedItems){
                break;
            } 
        }
        for(Map.Entry<Integer,Double> e : mapItemsVotesOnlyGroup.entrySet()) {
            Integer item = e.getKey();
            recsOnlyFromGroup.add(item); 
            cntOnlyGroup++;
            if(cntOnlyGroup >= numRecommendedItems){
                break;
            } 
        }
    }

    private void generateRecsFirstWinsStrategy(List<FIWrapper> mapFciWeight,
                                    List<FIWrapper> mapFciWeightGlobal, 
                                    List<FIWrapper> mapFciWeightGroup, 
                                    List<Integer> window) {
        cntAll = 0;
        cntOnlyGroup = 0; 
        cntOnlyGlobal = 0;
        recsCombined = new ArrayList<>();
        recsOnlyFromGroup = new ArrayList<>();
        recsOnlyFromGlobal = new ArrayList<>();
        int numRecommendedItems = config.getRC();
        
        for(FIWrapper fciVal : mapFciWeight) {
            SemiFCI key = fciVal.getFci();
            List<Integer> items = key.getItems();
            Iterator<Integer> itItems = items.iterator();
            while(itItems.hasNext()){
                Integer item =  itItems.next();
                if(!window.contains(item) && !recsCombined.contains(item)){  // create unique recommendations
                    recsCombined.add(item);
                    cntAll++;
                    if(cntAll >= numRecommendedItems){
                        break;
                    }
                }
            }
            if(cntAll >=  numRecommendedItems){
                 break;
            }
        }
        
        
        for(FIWrapper fciVal : mapFciWeightGroup) {
            SemiFCI key = fciVal.getFci();
            List<Integer> items = key.getItems();
            Iterator<Integer> itItems = items.iterator();
            while(itItems.hasNext()){
                Integer item =  itItems.next();
                if(!window.contains(item) && !recsOnlyFromGroup.contains(item)){  // create unique recommendations
                    recsOnlyFromGroup.add(item);
                    cntOnlyGroup++;
                    if(cntOnlyGroup >=  numRecommendedItems){
                        break;
                    }
                }
            }
            if(cntOnlyGroup >=  numRecommendedItems){
                 break;
            }
        }
        
        for(FIWrapper fciVal : mapFciWeightGlobal) {
            SemiFCI key = fciVal.getFci();
            List<Integer> items = key.getItems();
            Iterator<Integer> itItems = items.iterator();
            while(itItems.hasNext()){
                Integer item =  itItems.next();
                if(!window.contains(item) && !recsOnlyFromGlobal.contains(item)){  // create unique recommendations
                    recsOnlyFromGlobal.add(item);
                    cntOnlyGlobal++;
                    if(cntOnlyGlobal >=  numRecommendedItems){
                        break;
                    }
                }
            }
            if(cntOnlyGlobal >=  numRecommendedItems){
                 break;
            }
        }
    }

    public void resetLearning() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
