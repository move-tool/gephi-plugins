/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.activeintel.gephi.network.optimization.genetic.ui;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.*;
import java.io.Serializable;
import java.util.Arrays;
import java.util.EventListener;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.activeintel.gephi.jgrapht.JGraphT2GephiConvertingFilter;
import org.activeintel.gephi.jgrapht.JGraphTUtils;
import org.activeintel.gephi.utilities.GephiUtilities;
import org.activeintel.network.optimization.algorithms.GeneticOptimizer;
import org.activeintel.network.optimization.algorithms.SubGraphEvaluator;
import org.activeintel.network.optimization.algorithms.SubGraphFactory;
import org.gephi.appearance.api.AppearanceController;
import org.gephi.appearance.api.AppearanceModel;
import org.gephi.datalab.api.AttributeColumnsController;
import org.gephi.appearance.api.Function;
import org.gephi.appearance.plugin.RankingNodeSizeTransformer;

/*
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeController;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeType;
*/
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.filters.plugin.attribute.AttributeNonNullBuilder;
import org.gephi.filters.plugin.attribute.AttributeNonNullBuilder.AttributeNonNullFilter;
import org.gephi.graph.api.Column;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.layout.plugin.AutoLayout;
import org.gephi.layout.plugin.forceAtlas.ForceAtlasLayout;
import org.gephi.layout.plugin.forceAtlas2.ForceAtlas2;
import org.gephi.layout.spi.LayoutProperty;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperty;
/*
import org.gephi.ranking.api.Ranking;
import org.gephi.ranking.api.RankingController;
import org.gephi.ranking.api.Transformer;
import org.gephi.ranking.plugin.transformer.AbstractSizeTransformer;
*/
import org.gephi.visualization.VizController;
import org.jgrapht.Graph;
import org.jgrapht.WeightedGraph;
import org.jgrapht.graph.DirectedWeightedSubgraph;
import org.jgrapht.graph.Subgraph;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;

/**
 *
 * @author neil
 */
public class GraphView implements Serializable, PropertyChangeListener {
    
    private static final Logger log = Logger.getLogger(GraphView.class.getName());    
    
    private Boolean showOnlySubGraph;
    private Boolean showGraphOptimization;
    private Graph superGraph;
    private Graph subGraph;
    private Boolean autoLayout;
    private Boolean isSize;    
    private SubGraphEvaluator fitnessEvaluator;
    private static final String FLD_SUBGRAPH_FITNESS = "subgraph_fitness";
    private static final String FLD_GRAPH_FITNESS = "graph_fitness";
    private SubGraphFactory subGraphFactory;
    private Color selectedColor;
    private Color notselectedColor;
    
    
    public GraphView() {
    }
    
    
    
    /**
     * Initializes subgraph; by finding it in the current graph
     * (fitness field is non-zero)
     * Extract subGraph by filtering out nodes with fitness <> null
     * 
     * Typically subGraph is assigned by the optimizer;
     * however when the graph is reopened it is not;
     * hence need to manually init it.
     * 
     */
    public void initSubGraph(){
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        // Extract subGraph by filtering out nodes with fitness <> null
                               
        // get column unique to subgraph
        Column subGraphCol = graphModel.getNodeTable().getColumn(FLD_GRAPH_FITNESS);
        
        // return if has not been previously initialized
        if (subGraphCol == null ){
            return;
        }
                
        // use non-null filter on subgraph's col
        FilterController controller = Lookup.getDefault().lookup(FilterController.class);
        //AttributeNonNullFilter subGraphFilter = new AttributeNonNullBuilder.AttributeNonNullFilter(subGraphCol);
        AttributeNonNullBuilder.AttributeNonNullFilter.Node subGraphFilter = new AttributeNonNullBuilder.AttributeNonNullFilter.Node(subGraphCol); 
        Query query = controller.createQuery(subGraphFilter);        
        org.gephi.graph.api.GraphView subGraphView = controller.filter(query);                
        org.gephi.graph.api.Graph gSubGraph = graphModel.getGraph(subGraphView);                  
        log.fine("graph: " + GephiUtilities.toString(graphModel.getGraph()) );        
        log.fine("gsubgraph: " + GephiUtilities.toString(gSubGraph ));        

        
        Graph jSubgraph = JGraphTUtils.wrap(gSubGraph); // is not of the right kind yet more casting is needed        
        log.fine("jSubgraph (before casting): " + jSubgraph);                
        
        jSubgraph = this.subGraphFactory.createSubgraph(jSubgraph.vertexSet());
        log.fine("jSubraph: " + jSubgraph);        
        
        setSubGraph(jSubgraph);
    }
    

    /**
     * @return the showOnlySubGraph
     */
    public Boolean getShowOnlySubGraph() {
        return showOnlySubGraph;
    }

    /**
     * @param showOnlySubGraph the showOnlySubGraph to set
     */
    public void setShowOnlySubGraph(Boolean showOnlySubGraph) {
        this.showOnlySubGraph = showOnlySubGraph;
        updateView(this.subGraph, true);
    }


    void setSuperGraph(Graph graph) {
        this.superGraph = graph;
    }

    

    void updateView(Graph jSubGraph) {
        updateView(jSubGraph, this.showGraphOptimization);
    }    

    
    void updateView() {
        updateView(this.getSubGraph(), this.showGraphOptimization);
    }    
    

    void updateView(Graph jSubGraph,  Boolean showGraph) {
        this.setSubGraph(jSubGraph);
        
        if (jSubGraph == null){
            return;
        }                                    
        
        if (!showGraph){
            return;
        }
        
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        GephiUtilities.resetNodesColor(graphModel.getGraph(), this.getNotselectedColor());


        // Display resulting subGraph (including conversion of graph from JGraphT to Gephi)
        JGraphT2GephiConvertingFilter filter = new JGraphT2GephiConvertingFilter(jSubGraph);
        FilterController controller = Lookup.getDefault().lookup(FilterController.class);
        Query query = controller.createQuery(filter);
        org.gephi.graph.api.GraphView subGraphView = controller.filter(query);
        org.gephi.graph.api.Graph gSubGraph = graphModel.getGraph(subGraphView);

        GephiUtilities.setNodesColor(gSubGraph, this.getSelectedColor());

        if (this.getShowOnlySubGraph()){
            graphModel.setVisibleView(subGraphView);            
        } else {
            graphModel.setVisibleView(graphModel.getGraph().getView());
        }       
        
    }

    /**
     * @return the subGraph
     */
    public Graph getSubGraph() {
        return subGraph;
    }

    /**
     * @param subGraph the subGraph to set
     */
    public void setSubGraph(Graph subGraph) {
        this.subGraph = subGraph;
    }

    /**
     * @return the showGraphOptimization
     */
    public Boolean getShowGraphOptimization() {
        return showGraphOptimization;
    }

    /**
     * @param showGraphOptimization the showGraphOptimization to set
     */
    public void setShowGraphOptimization(Boolean showGraphOptimization) {
        this.showGraphOptimization = showGraphOptimization;
    }

    @Override
    public void propertyChange(PropertyChangeEvent pce) {        
        //throw new UnsupportedOperationException("Not supported yet.");
        // TODO
        // show full network (if starting optimization)
        // show full network & subgraph (if optimization is finished)        
    }

    void setFitnessEvaluator(SubGraphEvaluator fitnessEvaluator) {
        this.fitnessEvaluator = fitnessEvaluator;
    }

    
    /**
     * Adds fitness data (column) to the nodes of the optimized subgraph
     */
    public void setFitnessData() {
        GraphModel model = Lookup.getDefault().lookup(GraphController.class).getGraphModel();                            
        
        // reset data from previous runs
        resetFitnessData();
        
        
        // write fitness data

        //Add fitness column if does not exist yet
        Column subGraphFitnessCol = model.getNodeTable().getColumn(FLD_SUBGRAPH_FITNESS);
        if (subGraphFitnessCol == null ){
             subGraphFitnessCol = model.getNodeTable().addColumn(FLD_SUBGRAPH_FITNESS, Double.TYPE);            
        }


        //Add fitness column if does not exist yet
        Column graphFitnessCol = model.getNodeTable().getColumn(FLD_GRAPH_FITNESS);
        if (graphFitnessCol == null ){
             graphFitnessCol = model.getNodeTable().addColumn(FLD_GRAPH_FITNESS, Double.TYPE);            
        }

        
        //Write subGraph values to column
        // get fitness data
        Map<Object, Double> subGraphNodesFitness = this.fitnessEvaluator.getFitness(subGraph, subGraph.vertexSet());        
        for (Object jnode : this.getSubGraph().vertexSet() ) {        
            Node gnode = (Node) jnode;
            gnode.setAttribute(subGraphFitnessCol, subGraphNodesFitness.get(jnode));
        }
        
        
        //Write Graph values to column
        // get fitness data
        Map<Object, Double> graphNodesFitness = this.fitnessEvaluator.getFitness( new DirectedWeightedSubgraph((WeightedGraph)superGraph, superGraph.vertexSet(), superGraph.edgeSet()), superGraph.vertexSet());                
        for (Object jnode : this.superGraph.vertexSet() ) {        
            Node gnode = (Node) jnode;
            gnode.setAttribute(graphFitnessCol, graphNodesFitness.get(jnode));
        }


    }

    
    /**
     * deletes fitness column
     */
    public void resetFitnessData() {
        GraphModel model = Lookup.getDefault().lookup(GraphController.class).getGraphModel();                            
        Column fitnessCol = model.getNodeTable().addColumn("utility", Double.TYPE); // hack: add and then removed
        model.getNodeTable().removeColumn(fitnessCol); // remove
    }

    /**
     * @return the autoLayout
     */
    public Boolean getAutoLayout() {
        return autoLayout;
    }

    /**
     * @param autoLayout the autoLayout to set
     */
    public void setAutoLayout(Boolean isAutoLayout) {
        this.autoLayout = isAutoLayout;
    }
    
    
    public void layout(){

        final GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();                    
                        
        // if true -> lay it out        
        if (this.autoLayout && graphModel != null){
            SwingWorker worker = new SwingWorker<Void, Void>() {

                @Override
                protected Void doInBackground() throws Exception {
                    org.gephi.graph.api.GraphView view = graphModel.getVisibleView();

                    AutoLayout autoLayouter = new AutoLayout(3, TimeUnit.SECONDS); // make it dynamic later: num of nodes divided by some factor
                    autoLayouter.setGraphModel(graphModel);
                    
                    ForceAtlas2 fa2Layout = new ForceAtlas2(null);
                    fa2Layout.setGraphModel(graphModel);
                    fa2Layout.resetPropertiesValues();
                    fa2Layout.setThreadsCount(8);
                    //fa2Layout.setLinLogMode(true);
                    fa2Layout.setAdjustSizes(true); 
                    //fa2Layout.setBarnesHutOptimize(true); // setAdjustSizes doesn't seem to work; am using setBarnesHutOptimize instead
                    fa2Layout.setEdgeWeightInfluence(1.0);
                    fa2Layout.setScalingRatio(10.0);
                    fa2Layout.setGravity(1.0);
                    fa2Layout.setStrongGravityMode(false);
                    
                    fa2Layout.initAlgo();                     
///*                    
                    for (int i = 0; i < 30000; i++){
                        fa2Layout.goAlgo();
                    }
//*/
                    
                    /*
                    ForceAtlasLayout faLayout = new ForceAtlasLayout(null);
                    faLayout.resetPropertiesValues();
                    faLayout.setAdjustSizes(true);
                    */
                    //AutoLayout.DynamicProperty adjustBySizeProperty = AutoLayout.createDynamicProperty("forceAtlas.adjustSizes.name", Boolean.TRUE, 1f);//True after 10% of layout time
                    //AutoLayout.DynamicProperty repulsionProperty = AutoLayout.createDynamicProperty("forceAtlas.repulsionStrength.name", new Double(500.), 0f);//500 for the complete period
                                       
                    autoLayouter.addLayout(fa2Layout, 1f);                    
                                        
                    //autoLayouter.addLayout(faLayout, 0.5f, new AutoLayout.DynamicProperty[]{adjustBySizeProperty, repulsionProperty});                                        
                    //autoLayouter.addLayout(faLayout, 0.5f);                                        
                    
                    //autoLayouter.execute();   
                    
                    VizController.getInstance().getGraphIO().centerOnGraph();            
                    
                    return null;
                }

                @Override
                public void done() {
                    try {
                        get();
                    } catch (Exception ex) {
                        Exceptions.printStackTrace(ex);
                        throw new RuntimeException(ex);
                    } 
                }


            };     
            worker.execute();
        }        
    }
    
    void size() {
        size(false);
    }    

    void size(boolean forceExecution) {
        if (this.getIsSize() || forceExecution){
            AppearanceController appearanceController = Lookup.getDefault().lookup(AppearanceController.class);
            AppearanceModel appearanceModel = appearanceController.getModel();
            
            // Get visible graph
            GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
            org.gephi.graph.api.Graph gGraph = graphModel.getGraphVisible();
            // convert to jgrapht
            Graph jGraph = JGraphTUtils.wrap(gGraph);
            // update sizes

            // use sub/graph utility (depending on which graph is displayed)
            Column col;
            //AttributeModel attributeModel = Lookup.getDefault().lookup(AttributeController.class).getModel();
            GraphModel attributeModel = graphModel;
                    
            if (jGraph.vertexSet().size() == this.superGraph.vertexSet().size()){
                col = attributeModel.getNodeTable().getColumn(FLD_GRAPH_FITNESS);                        
            } else {
                col = attributeModel.getNodeTable().getColumn(FLD_SUBGRAPH_FITNESS);                        
            }
                                    
            Function sizeRanking = appearanceModel.getNodeFunction(gGraph, col, RankingNodeSizeTransformer.class);
            RankingNodeSizeTransformer sizeTransformer = (RankingNodeSizeTransformer) sizeRanking.getTransformer();            
            sizeTransformer.setMinSize(10);
            sizeTransformer.setMaxSize(40);
            appearanceController.transform(sizeRanking);                                                
            
            VizController.getInstance().getGraphIO().centerOnGraph();            
        }
    }

    /**
     * @return the isSize
     */
    public Boolean getIsSize() {
        return isSize;
    }

    /**
     * @param isSize the isSize to set
     */
    public void setIsSize(Boolean isSize) {
        this.isSize = isSize;
    }

    void setGraphFactory(SubGraphFactory subGraphFactory) {
        this.subGraphFactory = subGraphFactory;
    }

    /**
     * @return the selectedColor
     */
    public Color getSelectedColor() {
        return selectedColor;
    }

    
    /**
     * @param selectedColor the selectedColor to set
     */
    public void setSelectedColor(Color selectedColor) {
        this.selectedColor = selectedColor;
    }
    
    
    /**
     * @return the notselectedColor
     */
    public Color getNotselectedColor() {
        return notselectedColor;
    }

    /**
     * @param notselectedColor the notselectedColor to set
     */
    public void setNotselectedColor(Color notselectedColor) {
        this.notselectedColor = notselectedColor;
    }
    

}
