/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.activeintel.gephi.network.optimization.genetic.ui;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;
import javax.swing.JComponent;
import javax.swing.SwingWorker;
import org.activeintel.gephi.jgrapht.JGraphT2GephiConvertingFilter;
import org.activeintel.gephi.jgrapht.JGraphTUtils;
import org.activeintel.gephi.utilities.GephiUtilities;
import org.activeintel.network.optimization.algorithms.GeneticOptimizer;
import org.activeintel.network.optimization.algorithms.SubGraphCrossover;
import org.activeintel.network.optimization.algorithms.SubGraphEvaluator;
import org.activeintel.network.optimization.algorithms.SubGraphFactory;
import org.activeintel.network.optimization.algorithms.SubGraphMutation;
import org.gephi.filters.api.FilterController;
import org.gephi.filters.api.Query;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.GraphView;
import org.gephi.graph.api.Node;
//import org.gephi.graph.api.NodeData;
import org.gephi.statistics.spi.Statistics;
import org.gephi.statistics.spi.StatisticsBuilder;
import org.gephi.statistics.spi.StatisticsUI;
import org.jgrapht.Graph;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.openide.windows.TopComponent;
import org.netbeans.api.settings.ConvertAsProperties;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;

import org.openide.util.Lookup;
import org.uncommons.maths.random.Probability;
import org.uncommons.watchmaker.framework.EvolutionEngine;
import org.uncommons.watchmaker.framework.EvolutionObserver;
import org.uncommons.watchmaker.framework.FitnessEvaluator;
import org.uncommons.watchmaker.framework.PopulationData;
import org.uncommons.watchmaker.framework.TerminationCondition;
import org.uncommons.watchmaker.framework.termination.GenerationCount;
import org.uncommons.watchmaker.framework.termination.Stagnation;
import org.uncommons.watchmaker.framework.termination.UserAbort;
import org.uncommons.watchmaker.swing.evolutionmonitor.EvolutionMonitor;

/**
 *
 * @author neil
 */
class NetworkOptimizationTask extends SwingWorker<Graph, Graph> {
    
    private static final Logger log = Logger.getLogger(NetworkOptimizationPanelTopComponent.class.getName());            

    NetworkOptimizationPanelTopComponent parentComponent;
    private UserAbort userAbort;
    private GeneticOptimizer optimizer;
    private org.activeintel.gephi.network.optimization.genetic.ui.GraphView graphViewer;


    NetworkOptimizationTask(NetworkOptimizationPanelTopComponent parentComponent) {
        this.parentComponent = parentComponent;
    }

    
    /**
     * Used mostly for displaying exceptions
     * 
     */
    @Override
    protected void done(){    
        try {
            get();            
            parentComponent.progressBar.setIndeterminate(false);            
        } catch (InterruptedException ex) {
            Exceptions.printStackTrace(ex);
        } catch (ExecutionException ex) {
            Exceptions.printStackTrace(ex);
        }
    }
    
    
    /**
     * Initializes itself (mostly optimizer); by passing in the data from panels
     * grabbing the current graph/subgraph etc.
     * 
     * NOTE: was added to allow to open a saved graph; and continue working on it
     * also am using it re-calculate fitness
     */
    public void init(){
        optimizer = parentComponent.geneticOptimizerPanel.geneticOptimizer; // need to be initialized further  
        // TODO *: reset optimizer (needed since might be run repeately; and then adding mutations, etc. will be duplicated and cause an illegal state
        // mostly needed for the variables modified by the add* functions
        optimizer.init();
        
        // Initialize and set optimizer's components
        
        // Get main graph (not the visible one; since people might want to run it several times)
        GraphModel graphModel = Lookup.getDefault().lookup(GraphController.class).getGraphModel();
        GephiUtilities.resetNodesColor(graphModel.getGraph());
        // Convert Graph
        Graph graph = JGraphTUtils.wrap(graphModel);
                        
        // SubGraph Factory
        SubGraphFactory subGraphFactory = parentComponent.subGraphFactoryPanel.subGraphFactory;
        subGraphFactory.setSuperGraph(graph);
        optimizer.setCandidateFactory(subGraphFactory);
                
        // Initialize FitnessEvaluator & add criteria ref
        // TODO *: on repeated run isNormalized == false; but should be true
        final SubGraphEvaluator fitnessEvaluator = parentComponent.subGraphEvaluatorPanel.subGraphEvaluator;
        fitnessEvaluator.setSuperGraph(graph);
        log.fine(fitnessEvaluator.toString());
        optimizer.setFitnessEvaluator(fitnessEvaluator);
        parentComponent.subGraphEvaluationCriteriaPanel.setEvaluator(fitnessEvaluator);                
        
        // Init/Add Operators
        // mutation
        SubGraphMutation subGraphMutation = parentComponent.subGraphMutationPanel.subGraphMutation;
        subGraphMutation.setSuperGraph(graph);
        optimizer.addEvolutionaryOperator(subGraphMutation);
        // crossover
        SubGraphCrossover subGraphCrossover = parentComponent.subGraphCrossoverPanel.subGraphCrossover;
        subGraphCrossover.setGraph(graph);
        optimizer.addEvolutionaryOperator(subGraphCrossover);
        
        // Graph Viewer
        //final org.activeintel.gephi.network.optimization.genetic.ui.GraphView graphViewer = parentComponent.graphViewPanel.graphView;
        graphViewer = parentComponent.graphViewPanel.graphView;
        graphViewer.setSuperGraph(graph);
        graphViewer.setFitnessEvaluator(fitnessEvaluator);
        graphViewer.setGraphFactory(subGraphFactory);
        graphViewer.initSubGraph();// if the graph has been processed already
        graphViewer.layout();
        graphViewer.updateView();
        optimizer.addPropertyChangeListener(graphViewer);
        
        // Update subGraph fitness score
        parentComponent.fitnessLbl.setText( String.valueOf(fitnessEvaluator.getFitness(graphViewer.getSubGraph())));
        
        
        //// Obervers
        // Add Obesrver/Updateer (will show updated fitness score)
        optimizer.addEvolutionObserver(new EvolutionObserver<Graph>() {
            public void populationUpdate(PopulationData<? extends Graph> data) {
                parentComponent.fitnessLbl.setText(String.valueOf(fitnessEvaluator.getFitness(data.getBestCandidate(), null)));
            }
        });

        // Add Obesrver/Updateer (show evolving subgraph)
        optimizer.addEvolutionObserver(new EvolutionObserver<Graph>() {
            public void populationUpdate(PopulationData<? extends Graph> data) {
                Graph jSubGraph = data.getBestCandidate();                
                graphViewer.updateView(jSubGraph);
            }
        });


        
        
        // Optimize
        // Termination Conditions
        userAbort = new UserAbort();
        optimizer.addTerminationCondition(userAbort);
        Stagnation stagnation = new Stagnation(parentComponent.geneticOptimizerPanel.geneticOptimizer.getStagnationGenerationLimit(), true, true);        
        optimizer.addTerminationCondition(stagnation);
        //Graph jSubGraph = optimizer.evolve(geneticSettings.getPopulationSize(), geneticSettings.getEliteCount(), userAbort, stagnation );        
    }
    

    @Override
    protected Graph doInBackground() throws Exception {                
        parentComponent.progressBar.setIndeterminate(true);
        init(); // init optimizer and its components
        
        // Graphical Optimization display
        EvolutionMonitor<Graph> evolutionMonitor = new EvolutionMonitor<Graph>();
        JComponent evolutionMonitorComponent = evolutionMonitor.getGUIComponent();
        optimizer.addEvolutionObserver(evolutionMonitor);
        evolutionMonitor.showInFrame("monitor", false);
        parentComponent.add(evolutionMonitor.getGUIComponent());
        
        Graph jSubGraph = optimizer.evolve();        
                
        //// Display Results
        // Display optimized subGraph
        // fitnessEvaluator used for updating node's fitness for displaying
        SubGraphEvaluator fitnessEvaluator = (SubGraphEvaluator) optimizer.getFitnessEvaluator();
        fitnessEvaluator.setIsNormalized(false); // un-normalized
        graphViewer.setFitnessEvaluator(fitnessEvaluator);
        graphViewer.setFitnessData();
        graphViewer.layout();
        graphViewer.size();        
        graphViewer.updateView(jSubGraph, true);  
        
        return jSubGraph;  // see <done> method for how things are wrapped up        
    }

    public void abort() {
        userAbort.abort();
    }
            

}
